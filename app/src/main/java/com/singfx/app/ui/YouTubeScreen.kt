package com.singfx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.singfx.app.yt.SearchPage
import com.singfx.app.yt.VideoResult
import com.singfx.app.yt.YouTubeRepo
import kotlinx.coroutines.launch

/** Results survive tab switches. */
private object YtCache {
    var query = ""
    var results: List<VideoResult> = emptyList()
    var page: SearchPage? = null
}

private fun dur(sec: Long): String = if (sec <= 0) "LIVE" else if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

@Composable
fun YouTubeScreen(onPlay: (url: String, audioOnly: Boolean) -> Unit, onOpenLink: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var q by rememberSaveable { mutableStateOf(YtCache.query) }
    var results by remember { mutableStateOf(YtCache.results) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var audioOnly by rememberSaveable { mutableStateOf(false) }

    fun search() {
        val text = q.trim()
        if (text.isEmpty()) return
        focus.clearFocus()
        keyboard?.hide()
        if (text.startsWith("http")) { onOpenLink(text); return }
        loading = true; error = null
        scope.launch {
            try {
                val page = YouTubeRepo.search(text)
                YtCache.query = text; YtCache.page = page; YtCache.results = page.items
                results = page.items
            } catch (e: Exception) {
                error = "Search failed: ${e.message ?: e.javaClass.simpleName}"
            } finally { loading = false }
        }
    }

    fun more() {
        val next = YtCache.page?.next ?: return
        if (loading) return
        loading = true
        scope.launch {
            try {
                val page = YouTubeRepo.more(YtCache.query, next)
                YtCache.page = page; YtCache.results = YtCache.results + page.items
                results = YtCache.results
            } catch (e: Exception) { error = e.message } finally { loading = false }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Search songs, e.g. \"Adele Hello karaoke\" or paste a link") },
            trailingIcon = { IconButton(onClick = ::search) { Icon(Icons.Default.Search, "Search") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { audioOnly = !audioOnly }) {
            Checkbox(audioOnly, { audioOnly = it })
            Icon(Icons.Default.Headphones, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" Audio only (saves data)", style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { Text(it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall) }
        if (results.isEmpty() && !loading && error == null) {
            Column(Modifier.padding(top = 30.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Search YouTube and sing along", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Tip: add \"karaoke\" or \"instrumental\" to your search. You can also share a video from the YouTube app to SingFX, or use Live FX to control the YouTube app directly.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.url }) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlay(r.url, audioOnly) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        AsyncImage(
                            model = r.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(140.dp).height(79.dp).clip(RoundedCornerShape(10.dp)).background(Surface2)
                        )
                        Text(dur(r.durationSec), fontSize = 11.sp, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        r.channel?.let { Text(it, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(color = Pink)
                    else if (results.isNotEmpty() && YtCache.page?.next != null) OutlinedButton(onClick = ::more) { Text("Load more") }
                }
            }
        }
    }
}
