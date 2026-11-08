package com.singcomfy.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.singcomfy.app.dsp.FxState
import com.singcomfy.app.player.PlayerHolder

class PlayerUiState(player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
    var meta by mutableStateOf(player.mediaMetadata)
    var hasMedia by mutableStateOf(player.mediaItemCount > 0)
    var hasVideo by mutableStateOf(player.videoSize != VideoSize.UNKNOWN && player.videoSize.width > 0)
    var buffering by mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
    var error by mutableStateOf<String?>(null)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberPlayerUiState(): PlayerUiState {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.get(ctx) }
    val state = remember { PlayerUiState(player) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.isPlaying = isPlaying }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { state.meta = mediaMetadata }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { state.hasMedia = player.mediaItemCount > 0 }
            override fun onVideoSizeChanged(videoSize: VideoSize) { state.hasVideo = videoSize.width > 0 }
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) state.error = null
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                state.error = error.message ?: error.errorCodeName
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                state.hasVideo = false
                state.meta = player.mediaMetadata
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    return state
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val s = rememberPlayerUiState()
    val fx by FxState.params.collectAsState()
    if (!s.hasMedia) return
    Row(
        Modifier.fillMaxWidth().background(Surface2).clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Pink, Violet))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(s.meta.title?.toString() ?: "Playing", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("Key ${formatSemis(fx.pitchSemitones)}" + (s.meta.artist?.let { " · $it" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { val p = PlayerHolder.get(ctx); if (p.isPlaying) p.pause() else p.play() }) {
            Icon(if (s.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pause")
        }
    }
}

@Composable
fun BusyOverlay(text: String) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = Surface1) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Pink, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Text(text)
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val player = remember { PlayerHolder.get(ctx) }
    val s = rememberPlayerUiState()
    val fx by FxState.params.collectAsState()
    val liveOn by FxState.liveRunning.collectAsState()
    val loopA by PlayerHolder.loopA.collectAsState()
    val loopB by PlayerHolder.loopB.collectAsState()
    var fullscreen by remember { mutableStateOf(false) }

    DisposableEffect(fullscreen) {
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ctl.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ctl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = fullscreen) { fullscreen = false }

    val playerView = @Composable { mod: Modifier ->
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3500
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setFullscreenButtonClickListener { fullscreen = it }
                    defaultArtwork = null
                }
            },
            update = { it.setFullscreenButtonState(fullscreen) },
            onRelease = { it.player = null },
            modifier = mod
        )
    }

    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { playerView(Modifier.fillMaxSize()) }
        return
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close player") }
            Column(Modifier.weight(1f)) {
                Text(s.meta.title?.toString() ?: "Nothing playing", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                s.meta.artist?.let { Text(it.toString(), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Pill("KEY ${formatSemis(fx.pitchSemitones)}")
            Spacer(Modifier.width(10.dp))
        }
        val videoMod = if (s.hasVideo) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.fillMaxWidth().height(200.dp)
        Box(videoMod) {
            playerView(Modifier.fillMaxSize())
            if (!s.hasVideo) {
                Column(Modifier.align(Alignment.TopCenter).padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, null, tint = Pink.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
                }
            }
        }
        s.error?.let {
            Text("Playback error: $it", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp, 6.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (liveOn) {
                Text("Live FX is on, so effects are applied by Live FX for this player too.",
                    style = MaterialTheme.typography.bodySmall, color = Gold, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Repeat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                AssistChip(onClick = { PlayerHolder.loopA.value = player.currentPosition; PlayerHolder.loopB.value = null },
                    label = { Text(loopA?.let { "A ${fmtTime(it)}" } ?: "Set A") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = if (loopA != null) Pink else MaterialTheme.colorScheme.onSurface))
                AssistChip(onClick = {
                    val a = loopA
                    if (a != null && player.currentPosition > a) { PlayerHolder.loopB.value = player.currentPosition; player.seekTo(a) }
                }, enabled = loopA != null,
                    label = { Text(loopB?.let { "B ${fmtTime(it)}" } ?: "Set B") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = if (loopB != null) Pink else MaterialTheme.colorScheme.onSurface))
                if (loopA != null) AssistChip(onClick = { PlayerHolder.loopA.value = null; PlayerHolder.loopB.value = null }, label = { Text("Clear") })
            }
            FxPanel(fx, compact = false, showTempo = true)
            Spacer(Modifier.height(24.dp))
        }
    }
}
