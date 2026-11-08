package com.singfx.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import com.singfx.app.dsp.FxState
import com.singfx.app.live.LiveFx
import com.singfx.app.live.LiveFxPrefs
import com.singfx.app.live.LiveFxService
import com.singfx.app.live.LiveStartActivity
import com.singfx.app.live.OutputRoute
import com.singfx.app.live.VolumeA11yService
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class SingApp(val pkg: String, val label: String, val icon: ImageBitmap?)

private val SING_APPS = listOf(
    "com.google.android.youtube" to "YouTube",
    "com.google.android.apps.youtube.music" to "YT Music",
    "com.spotify.music" to "Spotify",
    "com.android.chrome" to "Chrome",
    "com.smule.singandroid" to "Smule",
    "com.starmakerinteractive.starmaker" to "StarMaker",
    "com.zhiliaoapp.musically" to "TikTok",
)

private fun installedSingApps(ctx: Context): List<SingApp> = SING_APPS.mapNotNull { (pkg, label) ->
    val pm = ctx.packageManager
    if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
    val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    SingApp(pkg, label, icon)
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun startLive(ctx: Context, launchPkg: String? = null) {
    ctx.startActivity(Intent(ctx, LiveStartActivity::class.java).apply {
        if (launchPkg != null) putExtra(LiveStartActivity.EXTRA_LAUNCH, launchPkg)
    })
}

private fun openApp(ctx: Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun PowerButton(running: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "ring")
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        if (running) {
            Box(Modifier.size(150.dp).scale(ring).clip(CircleShape).background(Pink.copy(alpha = 0.18f)))
            Box(Modifier.size(150.dp).scale((ring - 1f) * 0.5f + 1f).clip(CircleShape).background(Pink.copy(alpha = 0.22f)))
        }
        Box(
            Modifier.size(140.dp).clip(CircleShape)
                .background(if (running) Brush.linearGradient(listOf(Pink, Violet)) else Brush.linearGradient(listOf(Surface2, Color(0xFF2E2642))))
                .border(2.dp, if (running) Color.White.copy(alpha = 0.6f) else Pink.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PowerSettingsNew, null, tint = if (running) Color.White else Pink, modifier = Modifier.size(46.dp))
                Text(if (running) "LIVE" else "START", fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    color = if (running) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(16.dp)) { content() }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LiveScreen() {
    val ctx = LocalContext.current
    val running by FxState.liveRunning.collectAsState()
    val status by LiveFx.status.collectAsState()
    val route by LiveFx.route.collectAsState()
    val level by LiveFx.outputLevel.collectAsState()
    val overlayShown by LiveFx.overlayVisible.collectAsState()
    val fx by FxState.params.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val a11yOk = remember(tick) { VolumeA11yService.isEnabled(ctx) }
    val apps = remember(tick) { installedSingApps(ctx) }
    var forced by remember { mutableStateOf(LiveFxPrefs.forcedRoute(ctx)) }
    var advanced by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // ---- Header
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SingFX", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Pink)
                Text("Change the key of any app and sing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (running) Pill("KEY ${formatSemis(fx.pitchSemitones)}")
        }

        // ---- Hero
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(if (running) listOf(Pink.copy(alpha = 0.28f), Surface1) else listOf(Surface2, Surface1)))
                .border(1.dp, Pink.copy(alpha = if (running) 0.6f else 0.25f), RoundedCornerShape(26.dp))
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PowerButton(running) { if (running) LiveFx.stop(ctx) else startLive(ctx) }
            Text(
                when {
                    running -> "Live FX is on"
                    status.startsWith("Error") -> status
                    status.startsWith("Starting") -> "Starting…"
                    else -> "Tap to start, then play music in any app"
                },
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = if (status.startsWith("Error") && !running) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurface
            )
            if (running) {
                Text(
                    "Your key, vocal and EQ settings are applied to every app. " +
                        (route?.let { "Output: ${it.label}." } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
                if (route != OutputRoute.MEDIA) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", modifier = Modifier.size(20.dp))
                        Slider(level, { LiveFx.outputLevel.value = it }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pink))
                        Text("${(level * 100).roundToInt()}", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
                    }
                    Text("Phone volume buttons also control this while Live FX is on.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { ctx.startService(Intent(ctx, LiveFxService::class.java).setAction(LiveFxService.ACTION_TOGGLE_OVERLAY)) }) {
                    Icon(Icons.Default.PictureInPicture, null, Modifier.size(18.dp))
                    Text(if (overlayShown) "  Hide floating bubble" else "  Show floating bubble")
                }
            }
        }

        // ---- Sing with…
        if (apps.isNotEmpty()) {
            Text("SING WITH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.forEach { app ->
                    Column(
                        Modifier.width(78.dp).clip(RoundedCornerShape(16.dp)).background(Surface1)
                            .clickable { if (running) openApp(ctx, app.pkg) else startLive(ctx, app.pkg) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (app.icon != null) Image(app.icon, app.label, Modifier.size(40.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(app.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
            if (!running) Text("Tapping an app starts Live FX and opens it.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }

        // ---- Setup (only when something is missing)
        if (!overlayOk || !a11yOk) {
            Spacer(Modifier.height(14.dp))
            Card(Modifier.border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text("  Finish setup", fontWeight = FontWeight.Bold)
                }
                if (!overlayOk) {
                    Text("Floating bubble: lets the controls float over YouTube and other apps.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) {
                        Text("Allow display over other apps")
                    }
                }
                if (!a11yOk) {
                    Text("Clean sound + volume buttons: turn on \"SingFX audio output\" in Accessibility. Sound then goes to your speaker, headphones or Bluetooth like normal, and the volume buttons control the karaoke volume.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility settings") }
                }
            }
        }

        // ---- Effects
        Spacer(Modifier.height(14.dp))
        Card {
            Text("YOUR SOUND", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
            FxPanel(fx, compact = false, showTempo = false)
        }

        // ---- Advanced
        Row(Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Advanced", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        AnimatedVisibility(advanced) {
            Column {
                Text("Output channel", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Pair<String, OutputRoute?>>("Auto" to null, "Accessibility" to OutputRoute.ACCESSIBILITY, "Alarm" to OutputRoute.ALARM, "Media" to OutputRoute.MEDIA).forEach { (label, r) ->
                        FilterChip(selected = forced == r, onClick = { forced = r; LiveFxPrefs.setForcedRoute(ctx, r) }, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pink.copy(alpha = 0.25f)))
                    }
                }
                Text(
                    "Auto uses the Accessibility channel when \"SingFX audio output\" is on, otherwise Alarm (which also plays on the phone speaker when headphones are connected). " +
                        "Media doesn't mute the original. Changes apply the next time Live FX starts.\n\n" +
                        "Apps that block recording (Netflix, some music apps) can't be processed. Live mode adds about 0.1 to 0.2 s of delay; the built-in player (YouTube and Library tabs) has none.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                AboutRow()
                Spacer(Modifier.height(24.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AboutRow() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<com.singfx.app.AppUpdate?>(null) }
    val repoUrl = "https://github.com/${com.singfx.app.BuildConfig.GITHUB_REPO}"
    Text("SingFX ${com.singfx.app.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
    Text("Free and open source (GPL-3.0).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row {
        TextButton(enabled = !checking, onClick = {
            checking = true
            scope.launch {
                val u = com.singfx.app.UpdateChecker.check(ctx, force = true)
                checking = false
                if (u == null) android.widget.Toast.makeText(ctx, "You have the latest version", android.widget.Toast.LENGTH_SHORT).show()
                found = u
            }
        }) { Text(if (checking) "Checking…" else "Check for updates") }
        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))) }) { Text("GitHub page") }
    }
    found?.let { u ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { found = null },
            title = { Text("Update available: ${u.version}") },
            text = { Text(u.notes.ifBlank { "A new version is available on GitHub." }) },
            confirmButton = {
                TextButton(onClick = { found = null; ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl ?: u.pageUrl))) }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { found = null }) { Text("Later") } }
        )
    }
}
