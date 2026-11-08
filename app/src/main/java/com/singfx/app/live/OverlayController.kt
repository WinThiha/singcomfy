package com.singfx.app.live

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.singfx.app.MainActivity
import com.singfx.app.dsp.FxState
import com.singfx.app.ui.FxPanel
import com.singfx.app.ui.Pink
import com.singfx.app.ui.SingTheme
import com.singfx.app.ui.Surface1
import com.singfx.app.ui.Violet
import com.singfx.app.ui.formatSemis
import kotlin.math.abs
import kotlin.math.roundToInt

private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val ssr = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ssr.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    fun create() { ssr.performAttach(); ssr.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
}

/** Floating bubble that expands into a pitch / EQ control panel on top of any app. */
class OverlayController(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner().also { it.create() }
    private val expanded = mutableStateOf(false)
    private var root: DragFrame? = null
    var isShowing = false
        private set

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Use absolute screen coordinates (no automatic offset below the status bar), so the
        // on-screen clamping below matches what the user sees.
        if (android.os.Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
        if (android.os.Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        x = 24
        y = (context.resources.displayMetrics.heightPixels * 0.3f).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return
        val frame = root ?: DragFrame(context).also { f ->
            val cv = ComposeView(context).apply {
                setContent { SingTheme { OverlayContent() } }
            }
            f.addView(cv)
            // Whenever the content changes size (bubble <-> panel), keep it fully on screen.
            f.addOnLayoutChangeListener { v, l, t, r, b, ol, ot, oRight, ob ->
                if (r - l != oRight - ol || b - t != ob - ot) keepOnScreen(v.width, v.height)
            }
            f.setViewTreeLifecycleOwner(owner)
            f.setViewTreeSavedStateRegistryOwner(owner)
            f.setViewTreeViewModelStoreOwner(owner)
            root = f
        }
        wm.addView(frame, params)
        isShowing = true
        if (!callbacksRegistered) { context.registerComponentCallbacks(configCallbacks); callbacksRegistered = true }
    }

    private var callbacksRegistered = false
    private val configCallbacks = object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
            root?.post { root?.let { keepOnScreen(it.width, it.height) } }
        }
        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {}
    }

    /** Remembered bubble position, so collapsing the panel puts the bubble back where it was. */
    private var bubbleX = params.x
    private var bubbleY = params.y

    /** Screen area not covered by the status bar, navigation bar or camera cutout. */
    private fun usableArea(): android.graphics.Rect {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val m = wm.currentWindowMetrics
            val ins = m.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
            )
            val b = m.bounds
            return android.graphics.Rect(ins.left, ins.top, b.width() - ins.right, b.height() - ins.bottom)
        }
        val dm = context.resources.displayMetrics
        return android.graphics.Rect(0, (24 * dm.density).toInt(), dm.widthPixels, dm.heightPixels - (48 * dm.density).toInt())
    }

    private fun clampedX(x: Int, w: Int): Int { val a = usableArea(); return x.coerceIn(a.left, maxOf(a.left, a.right - w)) }
    private fun clampedY(y: Int, h: Int): Int { val a = usableArea(); return y.coerceIn(a.top, maxOf(a.top, a.bottom - h)) }

    private fun keepOnScreen(w: Int, h: Int) {
        if (!isShowing || w == 0 || h == 0) return
        val nx = clampedX(params.x, w)
        val ny = clampedY(params.y, h)
        if (nx != params.x || ny != params.y) {
            params.x = nx; params.y = ny
            root?.let { runCatching { wm.updateViewLayout(it, params) } }
        }
    }

    /** After a drag, slide the bubble to the nearest side edge. */
    private fun snapBubbleToEdge(view: android.view.View) {
        val a = usableArea()
        val w = view.width
        val targetX = if (params.x + w / 2 < (a.left + a.right) / 2) a.left else a.right - w
        val targetY = clampedY(params.y, view.height)
        val startX = params.x
        val startY = params.y
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            addUpdateListener { an ->
                val f = an.animatedValue as Float
                params.x = (startX + (targetX - startX) * f).roundToInt()
                params.y = (startY + (targetY - startY) * f).roundToInt()
                if (isShowing) runCatching { wm.updateViewLayout(view, params) }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { bubbleX = params.x; bubbleY = params.y }
            })
            start()
        }
    }

    fun hide() {
        if (!isShowing) return
        root?.let { runCatching { wm.removeView(it) } }
        isShowing = false
    }

    fun destroy() {
        hide()
        if (callbacksRegistered) { runCatching { context.unregisterComponentCallbacks(configCallbacks) }; callbacksRegistered = false }
        owner.destroy()
        root = null
    }

    private fun setExpanded(v: Boolean) {
        if (v == expanded.value) return
        if (v) {
            bubbleX = params.x; bubbleY = params.y
            expanded.value = true // the layout listener then pulls the bigger panel back on screen
        } else {
            expanded.value = false
            params.x = bubbleX; params.y = bubbleY
            root?.let { if (isShowing) runCatching { wm.updateViewLayout(it, params) } }
        }
    }

    /** Container that turns drags anywhere on the bubble (or the panel header) into window moves. */
    private inner class DragFrame(ctx: Context) : FrameLayout(ctx) {
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private var downX = 0f; private var downY = 0f
        private var startX = 0; private var startY = 0
        private var dragging = false
        private val headerPx = (56 * ctx.resources.displayMetrics.density)

        private fun canDragFrom(ev: MotionEvent) = !expanded.value || ev.y < headerPx

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = params.x; startY = params.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> if (canDragFrom(ev) && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) {
                    dragging = true
                    return true
                }
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    params.x = clampedX(startX + (ev.rawX - downX).roundToInt(), width)
                    params.y = clampedY(startY + (ev.rawY - downY).roundToInt(), height)
                    runCatching { wm.updateViewLayout(this, params) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging && !expanded.value) snapBubbleToEdge(this)
                    dragging = false
                }
            }
            return true
        }
    }

    @Composable
    private fun OverlayContent() {
        val p by FxState.params.collectAsState()
        val level by LiveFx.outputLevel.collectAsState()
        val hud by LiveFx.volumeHud.collectAsState()
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val panelWidth = minOf(330, cfg.screenWidthDp - 24).dp
        val bodyMax = (cfg.screenHeightDp - 170).coerceIn(140, 470).dp
        if (!expanded.value) {
            Box(
                Modifier
                    .padding(6.dp)
                    .shadow(10.dp, CircleShape)
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Pink, Violet)))
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickableNoRipple { setExpanded(true) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hud) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("${(level * 100).roundToInt()}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    } else {
                        Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(formatSemis(p.pitchSemitones), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .padding(6.dp)
                    .shadow(16.dp, RoundedCornerShape(22.dp))
                    .width(panelWidth)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Surface1.copy(alpha = 0.97f))
                    .border(1.dp, Pink.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            ) {
                Row(
                    Modifier.background(Brush.horizontalGradient(listOf(Pink.copy(alpha = 0.35f), Violet.copy(alpha = 0.35f)))).padding(start = 14.dp, end = 4.dp).heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(" SingFX Live", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("tab", "live"))
                        setExpanded(false)
                    }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open app") }
                    IconButton(onClick = { LiveFx.stop(context) }) { Icon(Icons.Default.PowerSettingsNew, "Stop Live FX", tint = Pink) }
                    IconButton(onClick = { setExpanded(false) }) { Icon(Icons.Default.Close, "Minimize") }
                }
                Column(Modifier.heightIn(max = bodyMax).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    if (LiveFx.route.value != OutputRoute.MEDIA) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", modifier = Modifier.size(20.dp))
                            Slider(level, { LiveFx.outputLevel.value = it }, modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Pink))
                            Text("${(level * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
                        }
                    }
                    FxPanel(p, compact = true, showTempo = false)
                }
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
