package com.singcomfy.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.singcomfy.app.dsp.FxState
import com.singcomfy.app.player.LocalItem
import com.singcomfy.app.player.PlaybackService
import com.singcomfy.app.player.PlayerHolder
import com.singcomfy.app.ui.Bg
import com.singcomfy.app.ui.LibraryScreen
import com.singcomfy.app.ui.LiveScreen
import com.singcomfy.app.ui.MiniPlayer
import com.singcomfy.app.ui.Pink
import com.singcomfy.app.ui.PlayerScreen
import com.singcomfy.app.ui.SingTheme
import com.singcomfy.app.ui.Surface1
import com.singcomfy.app.ui.WebHolder
import com.singcomfy.app.ui.WebScreen
import com.singcomfy.app.ui.YouTubeScreen
import com.singcomfy.app.yt.YouTubeRepo
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    LIVE("Live FX", Icons.Default.GraphicEq),
    YOUTUBE("YouTube", Icons.Default.SmartDisplay),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    WEB("Web", Icons.Default.Public),
}

@UnstableApi
class MainActivity : ComponentActivity() {
    private val tab = mutableStateOf(Tab.LIVE)
    private val playerOpen = mutableStateOf(false)
    private val busy = mutableStateOf<String?>(null)
    private val update = mutableStateOf<AppUpdate?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so always use light status/navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        FxState.init(this)
        PlayerHolder.get(this)
        setContent { SingTheme { Root() } }
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch { update.value = UpdateChecker.check(this@MainActivity) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Binding a controller keeps the background-playback service (and its notification) alive.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    fun openPlayer() { playerOpen.value = true }

    fun playYouTube(url: String, audioOnly: Boolean = false) {
        busy.value = "Loading video…"
        lifecycleScope.launch {
            try {
                val s = YouTubeRepo.resolve(url, audioOnly)
                PlayerHolder.playStream(this@MainActivity, s)
                playerOpen.value = true
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Couldn't load: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            } finally {
                busy.value = null
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("tab")?.let { if (it == "live") tab.value = Tab.LIVE }
        if (intent.getBooleanExtra("openPlayer", false)) playerOpen.value = true
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Media"
                PlayerHolder.playLocal(this, listOf(LocalItem(uri, name, isVideo = intent.type?.startsWith("video") == true)), 0)
                playerOpen.value = true
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val url = Regex("https?://\\S+").find(text)?.value ?: return
                openLink(url)
            }
        }
    }

    /** Route any link: YouTube -> built-in player, direct media -> player, anything else -> web tab. */
    fun openLink(url: String) {
        when {
            YouTubeRepo.isYouTubeUrl(url) -> playYouTube(url)
            Regex("\\.(mp3|m4a|aac|wav|flac|ogg|opus|mp4|mkv|webm|m3u8|mpd)(\\?.*)?$", RegexOption.IGNORE_CASE).containsMatchIn(Uri.parse(url).path ?: url) -> {
                PlayerHolder.playUrl(this, url); playerOpen.value = true
            }
            else -> { WebHolder.load(this, url); tab.value = Tab.WEB; playerOpen.value = false }
        }
    }

    @Composable
    private fun Root() {
        val current = tab.value
        val showPlayer = playerOpen.value
        val liveOn by FxState.liveRunning.collectAsState()
        BackHandler(enabled = showPlayer) { playerOpen.value = false }
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    Column {
                        MiniPlayer(onOpen = { playerOpen.value = true })
                        NavigationBar(containerColor = Surface1) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = current == t,
                                    onClick = { tab.value = t },
                                    icon = {
                                        Icon(t.icon, t.label, tint = if (t == Tab.LIVE && liveOn) Pink else androidx.compose.material3.LocalContentColor.current)
                                    },
                                    label = { Text(if (t == Tab.LIVE && liveOn) "Live ON" else t.label) },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = Pink.copy(alpha = 0.22f))
                                )
                            }
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (current) {
                        Tab.LIBRARY -> LibraryScreen(onPlay = { playerOpen.value = true }, onOpenLink = ::openLink)
                        Tab.YOUTUBE -> YouTubeScreen(onPlay = { url, audioOnly -> playYouTube(url, audioOnly) }, onOpenLink = ::openLink)
                        Tab.WEB -> WebScreen(onGoLive = { tab.value = Tab.LIVE })
                        Tab.LIVE -> LiveScreen()
                    }
                }
            }
            if (showPlayer) PlayerScreen(onClose = { playerOpen.value = false })
            busy.value?.let { com.singcomfy.app.ui.BusyOverlay(it) }
            update.value?.let { u ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { update.value = null },
                    title = { Text("Update available: ${u.version}") },
                    text = { Text(("A new version of SingComfy is on GitHub (you have ${BuildConfig.VERSION_NAME}).\n\n" + u.notes).trim()) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            update.value = null
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl ?: u.pageUrl)))
                        }) { Text("Download") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { UpdateChecker.skip(this@MainActivity, u.version); update.value = null }) { Text("Skip this version") }
                    }
                )
            }
        }
    }
}
