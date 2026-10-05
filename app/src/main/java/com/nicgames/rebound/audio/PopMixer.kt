package com.nicgames.rebound.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Fixed-level feedback paced by output samples, independently of simulation speed. */
class PopMixer(private val sample: ShortArray, private val sampleRate: Int = 44100) {
    private class Voice(var position: Int, val gain: Double)
    private val voices = ArrayList<Voice>(2)
    private val intervalFrames = maxOf((sample.size + 1) / 2, sampleRate * 60 / 1000).coerceAtMost(sample.size)
    private val sampleGain = minOf(1.0, .65 / (sample.maxOfOrNull { abs(it.toInt()) } ?: 0).coerceAtLeast(1) * 32768.0)
    private var nextOnsetFrames = 0
    private var pending = false
    private var pendingPreview = false
    var acceptedHits = 0L
        private set
    var emittedPops = 0L
        private set
    val active: Boolean get() = voices.isNotEmpty() || pending
    init { require(sample.isNotEmpty() && sampleRate > 0) }

    fun add(hits: Int = 1, preview: Boolean = false) {
        if (hits <= 0) return
        acceptedHits += hits
        if (nextOnsetFrames == 0) startPop(preview)
        else {
            pending = true
            pendingPreview = pendingPreview || preview
        }
    }

    private fun startPop(preview: Boolean) {
        voices.add(Voice(0, (if (preview) .6 else .5) * sampleGain))
        emittedPops++
        nextOnsetFrames = intervalFrames
    }

    fun clear() {
        voices.clear()
        pending = false
        pendingPreview = false
        nextOnsetFrames = 0
    }

    fun render(output: ShortArray): Int {
        output.fill(0)
        if (!active) return 0
        var frames = 0
        for (index in output.indices) {
            if (pending && nextOnsetFrames == 0) {
                startPop(pendingPreview)
                pending = false
                pendingPreview = false
            }
            var value = 0.0
            var voiceIndex = 0
            while (voiceIndex < voices.size) {
                val voice = voices[voiceIndex]
                value += sample[voice.position] / 32768.0 * voice.gain
                voice.position++
                if (voice.position >= sample.size) voices.removeAt(voiceIndex) else voiceIndex++
            }
            output[index] = (value * 32767).toInt().toShort()
            nextOnsetFrames = (nextOnsetFrames - 1).coerceAtLeast(0)
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