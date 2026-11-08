package com.singfx.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singfx.app.dsp.EQ_LABELS
import com.singfx.app.dsp.EQ_PRESETS
import com.singfx.app.dsp.FxParams
import com.singfx.app.dsp.FxState
import kotlin.math.abs
import kotlin.math.roundToInt

val Pink = Color(0xFFFF3D8B)
val Violet = Color(0xFF8B5CF6)
val Gold = Color(0xFFFFD23F)
val Bg = Color(0xFF0E0B16)
val Surface1 = Color(0xFF1A1526)
val Surface2 = Color(0xFF241D35)

val AppColors: ColorScheme = darkColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    secondary = Violet,
    tertiary = Gold,
    background = Bg,
    surface = Surface1,
    surfaceVariant = Surface2,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Color(0xFF2E2642),
    surfaceContainerLow = Color(0xFF15111F),
    onSurface = Color(0xFFF2EEF8),
    onSurfaceVariant = Color(0xFFB7AECB),
    outline = Color(0xFF4A4060),
)

@Composable
fun SingTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AppColors) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides AppColors.onSurface, content = content
    )
}

fun formatSemis(s: Float): String {
    val v = if (abs(s - s.roundToInt()) < 0.01f) s.roundToInt().toString() else String.format("%.1f", s)
    return if (s > 0.001f) "+$v" else v
}

@Composable
private fun SectionTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Big key/pitch control. */
@Composable
fun PitchControl(p: FxParams, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = { FxState.update { it.copy(pitchSemitones = (it.pitchSemitones - 1f).coerceAtLeast(-12f)) } }, modifier = Modifier.size(if (compact) 44.dp else 52.dp)) {
            Icon(Icons.Default.Remove, "Key down")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatSemis(p.pitchSemitones), fontSize = if (compact) 30.sp else 40.sp, fontWeight = FontWeight.Black,
                color = if (abs(p.pitchSemitones) < 0.01f) MaterialTheme.colorScheme.onSurface else Pink)
            Text("key (semitones)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { FxState.update { it.copy(pitchSemitones = (it.pitchSemitones + 1f).coerceAtMost(12f)) } }, modifier = Modifier.size(if (compact) 44.dp else 52.dp)) {
            Icon(Icons.Default.Add, "Key up")
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { FxState.update { it.copy(pitchSemitones = (it.pitchSemitones - 0.5f).coerceAtLeast(-12f)) } }) { Text("−½") }
        TextButton(onClick = { FxState.update { it.copy(pitchSemitones = 0f) } }) { Text("Original key") }
        TextButton(onClick = { FxState.update { it.copy(pitchSemitones = (it.pitchSemitones + 0.5f).coerceAtMost(12f)) } }) { Text("+½") }
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String,
    steps: Int = 0, onReset: (() -> Unit)? = null, onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.bodyMedium, color = Pink, fontWeight = FontWeight.Bold)
            if (onReset != null) TextButton(onClick = onReset, modifier = Modifier.height(32.dp)) { Text("Reset", fontSize = 12.sp) }
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Pink, inactiveTrackColor = Surface2),
            modifier = Modifier.height(30.dp))
    }
}

/** Compact vertical slider for EQ bands. Double-tap resets to 0 dB. */
@Composable
fun VerticalSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cur by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val active = if (enabled) Pink else Color.Gray
    Canvas(
        modifier
            .pointerInput(min, max) {
                fun v(y: Float) = (max - (y / size.height) * (max - min)).coerceIn(min, max)
                detectVerticalDragGestures { change2, _ ->
                    change2.consume()
                    change((v(change2.position.y) * 2).roundToInt() / 2f)
                }
            }
            .pointerInput(min, max) {
                detectTapGestures(onDoubleTap = { change(0f) }, onTap = { off ->
                    val v = (max - (off.y / size.height) * (max - min)).coerceIn(min, max)
                    change((v * 2).roundToInt() / 2f)
                })
            }
    ) {
        val w = size.width
        val h = size.height
        val trackW = 6.dp.toPx()
        val cx = w / 2
        drawRoundRect(Surface2, Offset(cx - trackW / 2, 0f), Size(trackW, h), CornerRadius(trackW))
        val zeroY = h * (max / (max - min))
        val y = h * ((max - cur) / (max - min))
        val top = minOf(zeroY, y)
        drawRoundRect(active, Offset(cx - trackW / 2, top), Size(trackW, abs(zeroY - y)), CornerRadius(trackW))
        drawLine(Color(0x55FFFFFF), Offset(cx - 9.dp.toPx(), zeroY), Offset(cx + 9.dp.toPx(), zeroY), 1.dp.toPx())
        drawCircle(Color.White, 8.dp.toPx(), Offset(cx, y))
        drawCircle(active, 5.dp.toPx(), Offset(cx, y))
    }
}

@Composable
fun EqSection(p: FxParams, compact: Boolean) {
    SectionTitle("Equalizer") {
        Switch(checked = p.eqEnabled, onCheckedChange = { on -> FxState.update { it.copy(eqEnabled = on) } })
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EQ_PRESETS.forEach { preset ->
            val selected = p.eqGains.zip(preset.gains).all { (a, b) -> abs(a - b) < 0.01f }
            FilterChip(
                selected = selected,
                onClick = { FxState.update { it.copy(eqGains = preset.gains, eqEnabled = true) } },
                label = { Text(preset.name, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pink.copy(alpha = 0.25f), selectedLabelColor = Color.White),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 10) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                val g = p.eqGains[i]
                Text(if (g > 0) "+${g.roundToInt()}" else "${g.roundToInt()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VerticalSlider(
                    value = g, min = -12f, max = 12f, enabled = p.eqEnabled,
                    onChange = { v -> FxState.update { s -> s.copy(eqGains = s.eqGains.toMutableList().also { it[i] = v }, eqEnabled = true) } },
                    modifier = Modifier.width(30.dp).height(if (compact) 110.dp else 150.dp)
                )
                Text(EQ_LABELS[i], fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The full effects panel. [compact] is used by the floating overlay. */
@Composable
fun FxPanel(p: FxParams, compact: Boolean = false, showTempo: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Key / Pitch")
        PitchControl(p, compact)

        if (showTempo) {
            LabeledSlider("Speed", p.tempo, 0.5f..1.5f, "${(p.tempo * 100).roundToInt()}%",
                onReset = { FxState.update { it.copy(tempo = 1f) } }) { v ->
                FxState.update { it.copy(tempo = (v * 20).roundToInt() / 20f) }
            }
        }

        SectionTitle("Vocal remover")
        LabeledSlider("Reduce vocals", p.vocalRemoval, 0f..1f, if (p.vocalRemoval < 0.01f) "Off" else "${(p.vocalRemoval * 100).roundToInt()}%") { v ->
            FxState.update { it.copy(vocalRemoval = v) }
        }
        if (!compact) Text(
            "Cancels sound that is centered in the stereo mix (usually the lead singer). Works best on studio songs.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        EqSection(p, compact)

        SectionTitle("Output")
        LabeledSlider("Boost", p.gainDb, -6f..12f, "${if (p.gainDb > 0) "+" else ""}${p.gainDb.roundToInt()} dB",
            onReset = { FxState.update { it.copy(gainDb = 0f) } }) { v -> FxState.update { it.copy(gainDb = v.roundToInt().toFloat()) } }
        if (!compact) {
            LabeledSlider("Balance", p.balance, -1f..1f,
                when { p.balance < -0.02f -> "L ${(-p.balance * 100).roundToInt()}"; p.balance > 0.02f -> "R ${(p.balance * 100).roundToInt()}"; else -> "Center" },
                onReset = { FxState.update { it.copy(balance = 0f) } }) { v -> FxState.update { it.copy(balance = v) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { FxState.resetAll() }) { Text("Reset all effects") }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = Pink) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))
