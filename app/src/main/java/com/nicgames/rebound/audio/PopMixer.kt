package com.nicgames.rebound.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlin.math.tanh

/** Overlapping pops finish instead of stealing voices. Same-frame hits are spaced
 * 2ms apart; large groups share weighted onsets, without a long audio backlog. */
class PopMixer(private val sample: ShortArray, private val sampleRate: Int = 44100) {
    private class Voice(var position: Int, var gain: Double)
    private val voices = ArrayList<Voice>(96)
    var acceptedHits = 0L
        private set
    val active: Boolean get() = voices.isNotEmpty()
    init { require(sample.isNotEmpty() && sampleRate > 0) }

    fun add(hits: Int = 1, preview: Boolean = false) {
        if (hits <= 0) return
        acceptedHits += hits
        val groups = minOf(hits, 16)
        for (i in 0 until groups) {
            val count = hits / groups + if (i < hits % groups) 1 else 0
            val position = -(i * sampleRate / 500)
            val gain = (if (preview) .8 else .65) * sqrt(count.toDouble())
            if (voices.size < 96) voices.add(Voice(position, gain))
            else {
                val pending = voices.firstOrNull { it.position <= 0 } ?: voices.last()
                pending.gain = sqrt(pending.gain * pending.gain + gain * gain)
            }
        }
    }
    fun clear() { voices.clear() }

    fun render(output: ShortArray): Int {
        output.fill(0)
        if (!active) return 0
        var frames = 0
        for (index in output.indices) {
            var value = 0.0
            var voiceIndex = 0
            while (voiceIndex < voices.size) {
                val voice = voices[voiceIndex]
                if (voice.position >= 0) value += sample[voice.position] / 32768.0 * voice.gain
                voice.position++
                if (voice.position >= sample.size) voices.removeAt(voiceIndex) else voiceIndex++
            }
            output[index] = (tanh(value) * 0.82 * 32767).toInt().toShort()
            frames++
            if (!active) break
        }
        return frames
    }
}

object PopSample {
    fun decode(bytes: ByteArray): ShortArray {
        require(bytes.size >= 44 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF")
        require(bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE")
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(data.getShort(20).toInt() == 1 && data.getShort(22).toInt() == 1)
        require(data.getInt(24) == 44100 && data.getShort(34).toInt() == 16)
        require(bytes.copyOfRange(36, 40).toString(Charsets.US_ASCII) == "data")
        val size = data.getInt(40)
        require(size > 0 && size % 2 == 0 && size <= bytes.size - 44)
        return ShortArray(size / 2) { data.getShort(44 + it * 2) }
    }
}