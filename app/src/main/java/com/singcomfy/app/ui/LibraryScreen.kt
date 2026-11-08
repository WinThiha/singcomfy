package com.singcomfy.app.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.singcomfy.app.player.LocalItem
import com.singcomfy.app.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaFile(val uri: Uri, val title: String, val subtitle: String, val durationMs: Long, val isVideo: Boolean)

private fun query(context: Context, video: Boolean): List<MediaFile> {
    val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val proj = if (video) arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
    else arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ARTIST)
    val out = ArrayList<MediaFile>()
    val sel = if (video) null else "${MediaStore.Audio.Media.DURATION} >= 15000"
    context.contentResolver.query(collection, proj, sel, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            out += MediaFile(
                ContentUris.withAppendedId(collection, id),
                c.getString(1) ?: "Unknown",
                c.getString(3)?.takeIf { it != "<unknown>" } ?: "",
                c.getLong(2),
                video
            )
        }
    }
    return out
}

private fun fmt(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryScreen(onPlay: () -> Unit, onOpenLink: (String) -> Unit) {
    val ctx = LocalContext.current
    var video by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }
    var showLink by remember { mutableStateOf(false) }

    val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun granted() = perms.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    var hasPerm by remember { mutableStateOf(granted()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = granted(); refresh++
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            PlayerHolder.playLocal(ctx, listOf(LocalItem(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "File")), 0)
            onPlay()
        }
    }

    LaunchedEffect(Unit) { if (!hasPerm) permLauncher.launch(perms) }
    LaunchedEffect(video, hasPerm, refresh) {
        items = if (hasPerm) withContext(Dispatchers.IO) { runCatching { query(ctx, video) }.getOrDefault(emptyList()) } else emptyList()
    }
    val shown = remember(items, filter) {
        if (filter.isBlank()) items else items.filter { it.title.contains(filter, true) || it.subtitle.contains(filter, true) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Pink, modifier = Modifier.padding(top = 14.dp))
        Text("Play songs and videos on your phone with key change, vocal remover & EQ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openDoc.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Open file")
            }
            OutlinedButton(onClick = { showLink = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Open link")
            }
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !video, onClick = { video = false }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = Pink.copy(alpha = 0.25f))) { Text("Music") }
            SegmentedButton(selected = video, onClick = { video = true }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = Pink.copy(alpha = 0.25f))) { Text("Videos") }
        }
        OutlinedTextField(
            value = filter, onValueChange = { filter = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Filter") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        if (!hasPerm) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow access to your music and videos to see them here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permLauncher.launch(perms) }) { Text("Allow access") }
            }
        } else if (shown.isEmpty()) {
            Text(if (items.isEmpty()) "No ${if (video) "videos" else "music"} found on this device." else "No matches.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(shown, key = { _, f -> f.uri.toString() }) { index, f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        PlayerHolder.playLocal(ctx, shown.map { LocalItem(it.uri, it.title, it.subtitle.ifBlank { null }, it.isVideo) }, index)
                        onPlay()
                    }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                        Icon(if (f.isVideo) Icons.Default.Movie else Icons.Default.MusicNote, null, tint = if (f.isVideo) Violet else Pink)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(f.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (f.subtitle.isNotBlank()) Text(f.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(fmt(f.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showLink) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLink = false },
            title = { Text("Open a link") },
            text = {
                Column {
                    Text("YouTube links open in the player. Direct audio/video links (mp3, mp4, m3u8…) play directly. Other pages open in the Web tab.",
                        style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(url, { url = it }, singleLine = true, placeholder = { Text("https://…") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    showLink = false
                    if (url.isNotBlank()) onOpenLink(u)
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { showLink = false }) { Text("Cancel") } }
        )
    }
}
