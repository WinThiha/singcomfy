package com.singcomfy.app.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.singcomfy.app.MainActivity
import com.singcomfy.app.R
import com.singcomfy.app.dsp.DspChain
import com.singcomfy.app.dsp.FxState
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

/** How the processed audio is played so that the original (muted) media stream stays silent. */
enum class OutputRoute(val label: String) {
    ACCESSIBILITY("Accessibility stream"),
    ALARM("Alarm stream"),
    MEDIA("Media stream (original not muted)"),
}

object LiveFx {
    val status = MutableStateFlow("Stopped")
    val route = MutableStateFlow<OutputRoute?>(null)
    /** Output level 0..1 controlled by the volume keys / overlay while Live FX runs. */
    val outputLevel = MutableStateFlow(0.7f)
    val overlayVisible = MutableStateFlow(false)
    /** True briefly after a volume-button press, so the bubble can show the level. */
    val volumeHud = MutableStateFlow(false)
    private val hudHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideHud = Runnable { volumeHud.value = false }
    const val VOLUME_STEPS = 15

    /** Volume buttons while Live FX runs: change the karaoke output level in 1/15 steps. */
    fun nudgeVolume(direction: Int) {
        val step = (outputLevel.value * VOLUME_STEPS).let { kotlin.math.round(it) } + direction
        outputLevel.value = (step / VOLUME_STEPS).coerceIn(0f, 1f)
        volumeHud.value = true
        hudHandler.removeCallbacks(hideHud)
        hudHandler.postDelayed(hideHud, 1500)
    }

    /** Whether volume buttons should be redirected to the karaoke output right now. */
    fun interceptsVolumeKeys(): Boolean =
        com.singcomfy.app.dsp.FxState.liveRunning.value && route.value != null && route.value != OutputRoute.MEDIA

    fun stop(context: Context) {
        context.startService(Intent(context, LiveFxService::class.java).setAction(LiveFxService.ACTION_STOP))
    }
}

@UnstableApi
class LiveFxService : Service() {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_TOGGLE_OVERLAY = "overlay"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "live_fx"
        private const val NOTIF_ID = 42
        private const val SR = 48000
        private const val TAG = "LiveFx"
    }

    private var projection: MediaProjection? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private lateinit var am: AudioManager
    private var overlay: OverlayController? = null
    private val main = Handler(Looper.getMainLooper())

    private var savedMusicMuted = false
    private var savedOutStreamVol = -1
    private var outStream = -1
    private var volumeReceiver: BroadcastReceiver? = null
    private var track: AudioTrack? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(AUDIO_SERVICE) as AudioManager
        FxState.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { if (running) shutdown() else stopSelf(); return START_NOT_STICKY }
            ACTION_TOGGLE_OVERLAY -> { if (running) toggleOverlay() else stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                if (running) return START_NOT_STICKY
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                if (data == null) { stopSelf(); return START_NOT_STICKY }
                startAsForeground()
                try {
                    start(code, data)
                } catch (e: Exception) {
                    Log.e(TAG, "start failed", e)
                    LiveFx.status.value = "Error: ${e.message}"
                    shutdown()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live FX", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("tab", "live"), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, LiveFxService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val ov = PendingIntent.getService(this, 2, Intent(this, LiveFxService::class.java).setAction(ACTION_TOGGLE_OVERLAY), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("SingComfy Live is on")
            .setContentText("Pitch & EQ applied to audio from other apps")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Controls", ov)
            .addAction(0, "Stop", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIF_ID, n)
    }

    private fun chooseRoute(): OutputRoute = when {
        VolumeA11yService.isEnabled(this) -> OutputRoute.ACCESSIBILITY
        else -> OutputRoute.ALARM
    }

    @SuppressLint("MissingPermission")
    private fun start(code: Int, data: Intent) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("Microphone/recording permission missing")
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(code, data) ?: throw IllegalStateException("Screen-capture permission denied")
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { shutdown() } }
        }, main)

        val route = LiveFxPrefs.forcedRoute(this) ?: chooseRoute()
        LiveFx.route.value = route

        val captureCfg = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val inFormat = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val minIn = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord.Builder()
            .setAudioFormat(inFormat)
            .setBufferSizeInBytes(maxOf(minIn * 2, SR / 10 * 4))
            .setAudioPlaybackCaptureConfig(captureCfg)
            .build()

        val usage = when (route) {
            OutputRoute.ACCESSIBILITY -> AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
            OutputRoute.ALARM -> AudioAttributes.USAGE_ALARM
            OutputRoute.MEDIA -> AudioAttributes.USAGE_MEDIA
        }
        outStream = when (route) {
            OutputRoute.ACCESSIBILITY -> AudioManager.STREAM_ACCESSIBILITY
            OutputRoute.ALARM -> AudioManager.STREAM_ALARM
            OutputRoute.MEDIA -> AudioManager.STREAM_MUSIC
        }
        val outFormat = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
        val minOut = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(outFormat)
            .setBufferSizeInBytes(maxOf(minOut * 2, SR / 20 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track = t

        setupVolumes(route)

        record.startRecording()
        t.play()
        running = true
        FxState.liveRunning.value = true
        LiveFx.status.value = "Running"
        worker = Thread({
            try {
                loop(record, t)
            } catch (e: Throwable) {
                Log.e(TAG, "audio loop crashed", e)
                LiveFx.status.value = "Error: ${e.message ?: e.javaClass.simpleName}"
                runCatching { record.release() }
                runCatching { t.release() }
                main.post { shutdown() }
            }
        }, "LiveFx").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    private fun levelFromMusicIndex(idx: Int): Float {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (idx.toFloat() / max).coerceIn(0f, 1f)
    }

    /** Perceptual curve: 0..1 slider -> linear gain (about 45 dB range). */
    private fun levelToGain(level: Float): Float = if (level <= 0f) 0f else 10f.pow((level - 1f) * 45f / 20f)

    private fun setupVolumes(route: OutputRoute) {
        if (route == OutputRoute.MEDIA) {
            LiveFx.outputLevel.value = 1f
            return
        }
        val musicIdx = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        LiveFx.outputLevel.value = levelFromMusicIndex(if (musicIdx == 0) am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2 else musicIdx)
        savedMusicMuted = am.isStreamMute(AudioManager.STREAM_MUSIC)
        // The accessibility stream's volume can only be changed by the system, so only touch alarm.
        if (outStream != AudioManager.STREAM_ACCESSIBILITY) try {
            savedOutStreamVol = am.getStreamVolume(outStream)
            am.setStreamVolume(outStream, am.getStreamMaxVolume(outStream), 0)
        } catch (e: Exception) { Log.w(TAG, "set out vol", e) }
        LiveFxPrefs.markMuted(this, outStream, savedOutStreamVol, savedMusicMuted)
        muteMusic()

        // Volume keys normally change (and un-mute) the media stream. Turn that into our output level.
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (!running) return
                val stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (stream != AudioManager.STREAM_MUSIC && stream != -1) return
                if (i.action == "android.media.VOLUME_CHANGED_ACTION" && stream == AudioManager.STREAM_MUSIC) {
                    val idx = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                    val prev = i.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
                    Log.d(TAG, "media volume $prev -> $idx")
                    if (idx > 0 && idx != prev) LiveFx.outputLevel.value = levelFromMusicIndex(idx)
                }
                if (!am.isStreamMute(AudioManager.STREAM_MUSIC)) muteMusic()
            }
        }
        volumeReceiver = r
        val f = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
        }
        ContextCompat.registerReceiver(this, r, f, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun muteMusic() {
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        } catch (e: Exception) { Log.w(TAG, "mute", e) }
    }

    private fun restoreVolumes() {
        volumeReceiver?.let { runCatching { unregisterReceiver(it) } }
        volumeReceiver = null
        val route = LiveFx.route.value ?: return
        if (route == OutputRoute.MEDIA) return
        try {
            if (!savedMusicMuted) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            if (savedOutStreamVol >= 0 && outStream >= 0) am.setStreamVolume(outStream, savedOutStreamVol, 0)
        } catch (e: Exception) { Log.w(TAG, "restore", e) }
        savedOutStreamVol = -1
        LiveFxPrefs.clearMuted(this)
    }

    private fun loop(record: AudioRecord, t: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val frames = SR / 100 // 10 ms
        val inBytes = ByteArray(frames * 4)
        val inBuf = ByteBuffer.allocateDirect(frames * 4 * 2).order(ByteOrder.LITTLE_ENDIAN)
        var floats = FloatArray(frames * 2 * 4)
        var shorts = ShortArray(frames * 2 * 4)
        val chain = DspChain().apply { configure(SR, 2) }
        val sonic = SonicAudioProcessor()
        val fmt = AudioProcessor.AudioFormat(SR, 2, C.ENCODING_PCM_16BIT)
        var sonicPitch = 1f
        var sonicOn = false
        var lastGain = -1f
        var peak = 0f
        var blocks = 0

        // Small pre-roll of silence to absorb capture jitter.
        t.write(ShortArray(SR / 50 * 2), 0, SR / 50 * 2, AudioTrack.WRITE_BLOCKING)

        while (running) {
            val n = record.read(inBytes, 0, inBytes.size, AudioRecord.READ_BLOCKING)
            if (n <= 0) { if (n < 0) Thread.sleep(5); continue }
            val p = FxState.current

            val ratio = p.pitchRatio
            if (abs(ratio - sonicPitch) > 1e-4f) {
                sonicPitch = ratio
                sonic.setPitch(ratio)
                sonic.configure(fmt)
                sonic.flush(AudioProcessor.StreamMetadata.DEFAULT)
                sonicOn = sonic.isActive
            }

            val gain = levelToGain(LiveFx.outputLevel.value)
            if (gain != lastGain) { t.setVolume(gain); lastGain = gain }

            var outCount: Int // in samples (shorts)
            if (sonicOn) {
                inBuf.clear(); inBuf.put(inBytes, 0, n); inBuf.flip()
                sonic.queueInput(inBuf)
                val out = sonic.output.order(ByteOrder.LITTLE_ENDIAN)
                outCount = out.remaining() / 2
                if (outCount == 0) continue
                if (floats.size < outCount) { floats = FloatArray(outCount * 2); shorts = ShortArray(outCount * 2) }
                val sb = out.asShortBuffer()
                for (i in 0 until outCount) floats[i] = sb.get(i) / 32768f
            } else {
                outCount = n / 2
                if (floats.size < outCount) { floats = FloatArray(outCount * 2); shorts = ShortArray(outCount * 2) }
                for (i in 0 until outCount) {
                    val lo = inBytes[i * 2].toInt() and 0xff
                    val hi = inBytes[i * 2 + 1].toInt()
                    floats[i] = ((hi shl 8) or lo).toShort() / 32768f
                }
            }

            chain.process(floats, outCount / 2, p)
            var pk = 0f
            for (i in 0 until outCount) { val a = abs(floats[i]); if (a > pk) pk = a }
            if (pk > peak) peak = pk
            if (++blocks % 300 == 0) { Log.d(TAG, "level peak=%.3f pitch=%.2f underruns=%d".format(peak, sonicPitch, t.underrunCount)); peak = 0f }
            for (i in 0 until outCount) shorts[i] = (floats[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            t.write(shorts, 0, outCount, AudioTrack.WRITE_BLOCKING)
        }
        runCatching { record.stop() }
        record.release()
        runCatching { t.stop() }
        t.release()
        sonic.reset()
    }

    private fun showOverlay() {
        if (overlay == null) overlay = OverlayController(this)
        overlay?.show()
        LiveFx.overlayVisible.value = true
    }

    private fun toggleOverlay() {
        if (overlay?.isShowing == true) {
            overlay?.hide(); LiveFx.overlayVisible.value = false
        } else if (Settings.canDrawOverlays(this)) showOverlay()
    }

    private fun shutdown() {
        val wasRunning = running
        running = false
        worker?.join(500)
        worker = null
        track = null
        if (wasRunning) restoreVolumes()
        overlay?.destroy(); overlay = null
        LiveFx.overlayVisible.value = false
        projection?.stop(); projection = null
        FxState.liveRunning.value = false
        if (!LiveFx.status.value.startsWith("Error")) LiveFx.status.value = "Stopped"
        LiveFx.route.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) shutdown()
        super.onDestroy()
    }
}

object LiveFxPrefs {
    private fun p(c: Context) = c.getSharedPreferences("live", Context.MODE_PRIVATE)

    fun markMuted(c: Context, outStream: Int, outVol: Int, musicWasMuted: Boolean) = p(c).edit()
        .putBoolean("mutedByUs", true).putInt("outStream", outStream).putInt("outVol", outVol)
        .putBoolean("musicWasMuted", musicWasMuted).commit()

    fun clearMuted(c: Context) = p(c).edit().putBoolean("mutedByUs", false).commit()

    /** If the app died while Live FX had the media stream muted, put the volumes back. */
    fun recoverAudio(c: Context) {
        val prefs = p(c)
        if (!prefs.getBoolean("mutedByUs", false)) return
        val am = c.getSystemService(AudioManager::class.java)
        runCatching {
            if (!prefs.getBoolean("musicWasMuted", false)) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            val os = prefs.getInt("outStream", -1); val ov = prefs.getInt("outVol", -1)
            if (os >= 0 && ov >= 0) am.setStreamVolume(os, ov, 0)
        }
        clearMuted(c)
    }
    fun forcedRoute(c: Context): OutputRoute? = p(c).getString("route", null)?.let { runCatching { OutputRoute.valueOf(it) }.getOrNull() }
    fun setForcedRoute(c: Context, r: OutputRoute?) = p(c).edit().putString("route", r?.name).apply()
}
