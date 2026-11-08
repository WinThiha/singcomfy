package com.singcomfy.app.dsp

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

val EQ_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

data class FxParams(
    /** Pitch shift in semitones (-12..+12, 0.5 steps). */
    val pitchSemitones: Float = 0f,
    /** Playback speed / tempo for in-app player (0.5..1.5). */
    val tempo: Float = 1f,
    /** 0 = off, 1 = maximum center-channel (vocal) removal. */
    val vocalRemoval: Float = 0f,
    val eqEnabled: Boolean = true,
    val eqGains: List<Float> = List(10) { 0f },
    /** Output gain in dB (-6..+12). A limiter prevents clipping. */
    val gainDb: Float = 0f,
    /** -1 = left only, +1 = right only. */
    val balance: Float = 0f,
) {
    val pitchRatio: Float get() = 2f.pow(pitchSemitones / 12f)
}

data class EqPreset(val name: String, val gains: List<Float>)

val EQ_PRESETS = listOf(
    EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Bass boost", listOf(7f, 6f, 4.5f, 2.5f, 0.5f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Karaoke", listOf(3f, 2.5f, 1f, 0f, -1.5f, -4f, -5f, -3f, 0f, 1f)),
    EqPreset("Vocal boost", listOf(-2f, -1.5f, -1f, 0.5f, 2f, 3.5f, 3.5f, 2.5f, 1f, 0f)),
    EqPreset("Treble boost", listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2f, 4f, 5.5f, 6.5f)),
    EqPreset("Loudness", listOf(6f, 4.5f, 2f, 0f, -1f, 0f, 0f, 1.5f, 4f, 5f)),
    EqPreset("Pop", listOf(-1f, 0.5f, 2.5f, 3.5f, 3f, 1f, -0.5f, -1f, -1f, -1f)),
    EqPreset("Rock", listOf(4.5f, 3.5f, 2f, 0.5f, -1f, -1f, 0.5f, 2.5f, 3.5f, 4f)),
    EqPreset("Small speaker", listOf(-6f, -3f, 2f, 3f, 1.5f, 0f, 1f, 2f, 2f, 1f)),
)

/** Global, persisted effect settings shared by the in-app player and Live FX. */
object FxState {
    private val _params = MutableStateFlow(FxParams())
    val params: StateFlow<FxParams> = _params.asStateFlow()

    /** True while system-wide Live FX capture is running. */
    val liveRunning = MutableStateFlow(false)

    @Volatile
    var current: FxParams = FxParams()
        private set

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("fx", Context.MODE_PRIVATE)
        prefs = p
        val gains = p.getString("eq", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 10 } ?: List(10) { 0f }
        set(
            FxParams(
                pitchSemitones = p.getFloat("pitch", 0f),
                tempo = p.getFloat("tempo", 1f),
                vocalRemoval = p.getFloat("vocal", 0f),
                eqEnabled = p.getBoolean("eqOn", true),
                eqGains = gains,
                gainDb = p.getFloat("gain", 0f),
                balance = p.getFloat("balance", 0f),
            ), persist = false
        )
    }

    fun update(block: (FxParams) -> FxParams) = set(block(_params.value))

    fun set(value: FxParams, persist: Boolean = true) {
        current = value
        _params.value = value
        if (persist) prefs?.edit()?.apply {
            putFloat("pitch", value.pitchSemitones)
            putFloat("tempo", value.tempo)
            putFloat("vocal", value.vocalRemoval)
            putBoolean("eqOn", value.eqEnabled)
            putString("eq", value.eqGains.joinToString(","))
            putFloat("gain", value.gainDb)
            putFloat("balance", value.balance)
            apply()
        }
    }

    fun resetAll() = set(FxParams())
}
