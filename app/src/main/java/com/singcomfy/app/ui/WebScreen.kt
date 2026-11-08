package com.singcomfy.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.singcomfy.app.dsp.FxState

/** Keeps one WebView alive across tab switches. */
object WebHolder {
    private var web: WebView? = null
    private var wrapper: MutableContextWrapper? = null
    val url = mutableStateOf("https://m.youtube.com")
    val progress = mutableStateOf(100)
    val customView = mutableStateOf<View?>(null)
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var pending: String? = null
    const val HOME = "https://m.youtube.com"

    @SuppressLint("SetJavaScriptEnabled")
    fun get(context: Context): WebView {
        web?.let { w -> wrapper?.baseContext = context; return w }
        val wr = MutableContextWrapper(context)
        wrapper = wr
        val w = WebView(wr).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val s = request.url.scheme
                    return s != "http" && s != "https" // block intent:// etc. that would leave the app
                }
                override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) { WebHolder.url.value = u }
                override fun doUpdateVisitedHistory(view: WebView, u: String, isReload: Boolean) { WebHolder.url.value = u }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { WebHolder.progress.value = newProgress }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    customCallback = callback; customView.value = view
                }
                override fun onHideCustomView() { customView.value = null; customCallback = null }
            }
            loadUrl(pending ?: HOME)
            pending = null
        }
        web = w
        return w
    }

    fun load(context: Context, u: String) {
        val w = web
        if (w == null) pending = u else w.loadUrl(u)
        url.value = u
    }

    fun exitFullscreen() { customCallback?.onCustomViewHidden(); customView.value = null }
}

@Composable
fun WebScreen(onGoLive: () -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val web = WebHolder.get(ctx)
    val currentUrl by WebHolder.url
    val progress by WebHolder.progress
    val custom by WebHolder.customView
    val liveOn by FxState.liveRunning.collectAsState()
    var editing by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = custom != null || web.canGoBack()) {
        if (custom != null) WebHolder.exitFullscreen() else web.goBack()
    }

    if (custom != null) {
        AndroidView(factory = { (custom!!.parent as? ViewGroup)?.removeView(custom); custom!! }, modifier = Modifier.fillMaxSize().background(Color.Black))
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (web.canGoBack()) web.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            OutlinedTextField(
                value = editing ?: currentUrl,
                onValueChange = { editing = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val t = (editing ?: "").trim()
                    editing = null
                    focus.clearFocus()
                    if (t.isNotEmpty()) {
                        val u = when {
                            t.startsWith("http://") || t.startsWith("https://") -> t
                            t.contains('.') && !t.contains(' ') -> "https://$t"
                            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, "UTF-8")
                        }
                        WebHolder.load(ctx, u)
                    }
                }),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { web.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
            IconButton(onClick = { WebHolder.load(ctx, WebHolder.HOME) }) { Icon(Icons.Default.Home, "Home") }
        }
        if (progress < 100) LinearProgressIndicator(progress = { progress / 100f }, color = Pink, modifier = Modifier.fillMaxWidth())
        if (!liveOn) {
            Row(
                Modifier.fillMaxWidth().background(Pink.copy(alpha = 0.15f)).clickable(onClick = onGoLive).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GraphicEq, null, tint = Pink, modifier = Modifier.size(18.dp))
                Text("  To change key / EQ of website audio, turn on Live FX →", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { (web.parent as? ViewGroup)?.removeView(web); web }, modifier = Modifier.fillMaxSize())
        }
    }
}
