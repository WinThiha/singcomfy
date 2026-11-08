package com.singcomfy.app.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** RBJ biquad, transposed direct form II, one instance per channel. */
class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var z1 = 0.0; private var z2 = 0.0
    var bypass = true
        private set

    fun setPeaking(fs: Double, f0: Double, q: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05 || f0 >= fs / 2) { bypass = true; return }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val cw = cos(w0)
        norm(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
    }

    fun setLowShelf(fs: Double, f0: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05) { bypass = true; return }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0) // S = 1
        val sa = 2 * sqrt(a) * alpha
        norm(
            a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
            (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa
        )
    }

    fun setHighShelf(fs: Double, f0: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05 || f0 >= fs / 2) { bypass = true; return }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        norm(
            a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
            (a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa
        )
    }

    fun setLowPass(fs: Double, f0: Double, q: Double = 0.7071) {
        val w0 = 2 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        norm((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
    }

    private fun norm(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
        bypass = false
    }

    fun reset() { z1 = 0.0; z2 = 0.0 }

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}

/**
 * Effect chain (everything except pitch): vocal remover -> 10-band EQ -> balance -> gain -> limiter.
 * Operates in place on interleaved float samples in [-1, 1].
 */
class DspChain {
    private var sampleRate = 0
    private var channels = 0
    private var applied: FxParams? = null

    private val eq = Array(2) { Array(10) { Biquad() } }
    private val midLow = Biquad()
    private var gainLin = 1.0
    private var vocal = 0.0
    private var balL = 1.0
    private var balR = 1.0
    private var limEnv = 0.0
    private var limRelease = 0.9995

    fun configure(sampleRate: Int, channels: Int) {
        if (sampleRate == this.sampleRate && channels == this.channels) return
        this.sampleRate = sampleRate
        this.channels = channels
        applied = null
        reset()
    }

    fun reset() {
        eq.forEach { ch -> ch.forEach { it.reset() } }
        midLow.reset()
        limEnv = 0.0
    }

    private fun applyParams(p: FxParams) {
        if (p === applied) return
        val fs = sampleRate.toDouble()
        for (ch in 0 until 2) for (i in 0 until 10) {
            val g = if (p.eqEnabled) p.eqGains[i].toDouble() else 0.0
            val bq = eq[ch][i]
            when (i) {
                0 -> bq.setLowShelf(fs, 45.0, g)
                9 -> bq.setHighShelf(fs, 12000.0, g)
                else -> bq.setPeaking(fs, EQ_FREQS[i].toDouble(), 1.2, g)
            }
        }
        midLow.setLowPass(fs, 160.0)
        gainLin = 10.0.pow(p.gainDb / 20.0)
        vocal = p.vocalRemoval.toDouble().coerceIn(0.0, 1.0)
        balL = if (p.balance > 0) 1.0 - p.balance else 1.0
        balR = if (p.balance < 0) 1.0 + p.balance else 1.0
        limRelease = 1.0 - 1.0 / (0.15 * fs) // ~150 ms release
        applied = p
    }

    fun process(buf: FloatArray, frames: Int, params: FxParams) {
        if (sampleRate == 0 || channels == 0) return
        applyParams(params)
        val stereo = channels >= 2
        val eqL = eq[0]
        val eqR = eq[1]
        var idx = 0
        for (f in 0 until frames) {
            var l = buf[idx].toDouble()
            var r = if (stereo) buf[idx + 1].toDouble() else l

            if (stereo && vocal > 0.001) {
                val mid = (l + r) * 0.5
                val side = (l - r) * 0.5
                val low = midLow.process(mid)
                val newMid = low + (mid - low) * (1.0 - vocal)
                // Compensate a little for the energy removed with the vocals.
                val sideGain = 1.0 + 0.6 * vocal
                l = newMid + side * sideGain
                r = newMid - side * sideGain
            }

            for (b in 0 until 10) {
                val bl = eqL[b]
                if (!bl.bypass) l = bl.process(l)
                if (stereo) { val br = eqR[b]; if (!br.bypass) r = br.process(r) }
            }

            l *= gainLin * balL
            r *= gainLin * balR

            // Linked peak limiter (instant attack, smooth release).
            val peak = max(abs(l), abs(r))
            limEnv = if (peak > limEnv) peak else limEnv * limRelease + peak * (1 - limRelease)
            if (limEnv > 0.97) {
                val g = 0.97 / limEnv
                l *= g; r *= g
            }

            buf[idx] = clamp(l)
            if (stereo) {
                buf[idx + 1] = clamp(r)
                // Any extra channels are left untouched.
            }
            idx += channels
        }
    }

    private fun clamp(v: Double): Float = min(1.0, max(-1.0, v)).toFloat()
}
