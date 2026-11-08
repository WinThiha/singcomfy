package com.singfx.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.singfx.app.dsp.DspChain
import com.singfx.app.dsp.FxParams
import com.singfx.app.dsp.FxState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Applies vocal remover / EQ / gain to ExoPlayer's 16-bit PCM output. Pitch is handled by Sonic. */
@UnstableApi
class FxAudioProcessor : BaseAudioProcessor() {
    private val chain = DspChain()
    private var floats = FloatArray(0)
    private val bypassParams = FxParams()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        chain.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val channels = inputAudioFormat.channelCount
        val samples = remaining / 2
        val frames = samples / channels
        if (floats.size < samples) floats = FloatArray(samples)
        val shorts = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until samples) floats[i] = shorts.get(i) / 32768f
        inputBuffer.position(inputBuffer.position() + samples * 2)

        // While Live FX is running it captures our own output too, so don't double-process.
        val params = if (FxState.liveRunning.value) bypassParams else FxState.current
        chain.process(floats, frames, params)

        val out = replaceOutputBuffer(samples * 2)
        for (i in 0 until samples) {
            out.putShort((floats[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }

    override fun onFlush() { chain.reset() }
    override fun onReset() { chain.reset() }
}
