package com.singfx.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.singfx.app.dsp.FxState
import com.singfx.app.yt.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LocalItem(val uri: Uri, val title: String, val artist: String? = null, val isVideo: Boolean = false)

/** A resolved online stream (e.g. from YouTube). */
data class StreamItem(
    val title: String,
    val artist: String?,
    val thumbnail: String?,
    val videoUrl: String?,
    val audioUrl: String?,
    val muxedUrl: String?,
    val hlsUrl: String?,
    val dashUrl: String? = null,
    val pageUrl: String? = null,
)

@UnstableApi
object PlayerHolder {
    private var player: ExoPlayer? = null
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** A-B loop points in ms (null = not set). */
    val loopA = MutableStateFlow<Long?>(null)
    val loopB = MutableStateFlow<Long?>(null)

    fun get(context: Context): ExoPlayer {
        player?.let { return it }
        appContext = context.applicationContext
        FxState.init(appContext)
        val renderers = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(FxAudioProcessor()))
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(false)
                .build()
        }.setEnableDecoderFallback(true)

        val p = ExoPlayer.Builder(appContext, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = p

        scope.launch {
            combine(FxState.params, FxState.liveRunning) { fx, live -> fx to live }.collect { (fx, live) ->
                p.playbackParameters = PlaybackParameters(fx.tempo, if (live) 1f else fx.pitchRatio)
            }
        }
        // A-B loop watcher
        scope.launch {
            while (true) {
                val a = loopA.value
                val b = loopB.value
                if (a != null && b != null && b > a && p.isPlaying && p.currentPosition >= b) p.seekTo(a)
                delay(50)
            }
        }
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loopA.value = null; loopB.value = null
            }
        })
        return p
    }

    private fun httpFactory() = OkHttpDataSource.Factory(Http.client).setUserAgent(Http.USER_AGENT)

    private fun dataSourceFactory() = DefaultDataSource.Factory(appContext, com.singfx.app.yt.YtDataSource.Factory(httpFactory()))

    private fun meta(title: String, artist: String?, art: String? = null) = MediaMetadata.Builder()
        .setTitle(title).setArtist(artist).apply { art?.let { setArtworkUri(Uri.parse(it)) } }.build()

    fun playLocal(context: Context, items: List<LocalItem>, start: Int) {
        val p = get(context)
        val mediaItems = items.map {
            MediaItem.Builder().setUri(it.uri).setMediaId(it.uri.toString()).setMediaMetadata(meta(it.title, it.artist)).build()
        }
        p.setMediaItems(mediaItems, start, 0)
        p.prepare()
        p.play()
    }

    fun playUrl(context: Context, url: String, title: String? = null) {
        val p = get(context)
        val item = MediaItem.Builder().setUri(url).setMediaId(url)
            .setMediaMetadata(meta(title ?: Uri.parse(url).lastPathSegment ?: url, null)).build()
        p.setMediaItem(item)
        p.prepare()
        p.play()
    }

    fun playStream(context: Context, s: StreamItem) {
        val p = get(context)
        val id = s.pageUrl ?: s.title
        val m = meta(s.title, s.artist, s.thumbnail)
        val ds = dataSourceFactory()
        fun item(url: String) = MediaItem.Builder().setUri(url).setMediaId(id).setMediaMetadata(m).build()
        val source: MediaSource = when {
            s.videoUrl != null && s.audioUrl != null -> MergingMediaSource(
                true,
                ProgressiveMediaSource.Factory(ds).createMediaSource(item(s.videoUrl)),
                ProgressiveMediaSource.Factory(ds).createMediaSource(item(s.audioUrl)),
            )
            s.muxedUrl != null -> ProgressiveMediaSource.Factory(ds).createMediaSource(item(s.muxedUrl))
            s.audioUrl != null -> ProgressiveMediaSource.Factory(ds).createMediaSource(item(s.audioUrl))
            s.hlsUrl != null -> HlsMediaSource.Factory(ds).createMediaSource(item(s.hlsUrl))
            s.dashUrl != null -> DashMediaSource.Factory(ds).createMediaSource(item(s.dashUrl))
            else -> throw IllegalStateException("No playable stream found")
        }
        p.setMediaSource(source)
        p.prepare()
        p.play()
    }

    fun release() {
        player?.release()
        player = null
    }
}
