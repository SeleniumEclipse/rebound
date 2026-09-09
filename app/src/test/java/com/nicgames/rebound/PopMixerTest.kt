package com.nicgames.rebound

import com.nicgames.rebound.audio.PopMixer
import com.nicgames.rebound.audio.PopSample
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh
import org.junit.Assert.*
import org.junit.Test

/** Pure JVM PCM checks; these do not test device playback or perceived loudness. */
class PopMixerTest {
    @Test fun bundledWavIsCanonicalWithQuickOnsetAndControlledPeak() {
        val bytes = File("src/main/res/raw/ui_pop.wav").readBytes()
        val sample = PopSample.decode(bytes)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(bytes.size - 8, header.getInt(4))
        assertEquals("WAVEfmt ", String(bytes, 8, 8, Charsets.US_ASCII))
        assertEquals(16, header.getInt(16))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(SAMPLE_RATE, header.getInt(24))
        assertEquals(SAMPLE_RATE * 2, header.getInt(28))
        assertEquals(2, header.getShort(32).toInt())
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        assertEquals(bytes.size - 44, header.getInt(40))
        assertEquals(bytes.size - 44, sample.size * 2)

        val durationMs = sample.size * 1000.0 / SAMPLE_RATE
        val onset = sample.indexOfFirst { abs(it.toInt()) / PCM_SCALE >= 0.02 }
        val peak = sample.maxOf { abs(it.toInt()) } / PCM_SCALE
        assertTrue("Pop duration was $durationMs ms", durationMs in 40.0..100.0)
        assertTrue("Pop never reaches 0.02 amplitude", onset >= 0)
        assertTrue("Audible PCM onset must precede 12 ms", onset * 1000.0 / SAMPLE_RATE < 12.0)
        assertTrue("Peak $peak exceeds 0.65 plus one PCM step", peak <= 0.65 + 1.0 / PCM_SCALE)
    }

    @Test fun lastSampleIsNotCutAndReusedOutputHasNoStaleFrames() {
        // The opposite-sign last frame makes a truncated ending observable.
        val sample = ShortArray(70) { 4096 }.also { it[it.lastIndex] = -8192 }
        val mixer = PopMixer(sample)
        val output = ShortArray(64) { 12345 }
        mixer.add()

        assertEquals(64, mixer.render(output))
        assertTrue(output.all { it > 0 })
        assertTrue(mixer.active)

        assertEquals(6, mixer.render(output))
        assertTrue(output.take(5).all { it > 0 })
        assertEquals(limitedPcm(-8192 / PCM_SCALE * 0.65), output[5])
        assertTrue("Unused part of reused buffer must be zero", output.drop(6).all { it == 0.toShort() })
        assertFalse(mixer.active)

        output.fill(12345)
        assertEquals(0, mixer.render(output))
        assertTrue(output.all { it == 0.toShort() })
        assertEquals(1L, mixer.acceptedHits)
    }

    @Test fun secondHitOverlapsWithoutRestartingOrStealingTheFirstTail() {
        val sample = ShortArray(256) { 4096 }
        val gap = 64
        val solo = ShortArray(sample.size)
        val reference = PopMixer(sample).apply { add() }
        assertEquals(solo.size, reference.render(solo))

        val mixer = PopMixer(sample).apply { add() }
        val lead = ShortArray(gap)
        assertEquals(gap, mixer.render(lead))
        assertArrayEquals(solo.copyOfRange(0, gap), lead)
        mixer.add()

        val overlap = ShortArray(sample.size - gap)
        assertEquals(overlap.size, mixer.render(overlap))
        assertTrue("Both voices must contribute during overlap",
            energy(overlap) > 3.5 * energy(solo.copyOfRange(gap, sample.size)))
        assertTrue(mixer.active)

        val tail = ShortArray(gap)
        assertEquals(gap, mixer.render(tail))
        val soloTail = solo.copyOfRange(sample.size - gap, sample.size)
        assertTrue("Second hit must retain its tail", energy(tail) > 0.0)
        assertEquals("First hit must have ended, not restarted", energy(soloTail), energy(tail), 1e-12)
        assertArrayEquals(soloTail, tail)
        assertFalse(mixer.active)
        assertEquals(2L, mixer.acceptedHits)
    }

    @Test fun sameFrameHitsKeepTheirCountAndHaveBoundedWeightedOnsets() {
        // One-frame impulses expose each onset directly in the rendered PCM.
        for (hits in intArrayOf(1, 3, 16, 37)) {
            val sample = shortArrayOf(8192)
            val mixer = PopMixer(sample)
            mixer.add(hits)
            assertEquals(hits.toLong(), mixer.acceptedHits)
            val output = ShortArray(sample.size + MAX_STAGGER_FRAMES + 16)
            val rendered = mixer.render(output)
            val onsets = output.indices.filter { output[it] != 0.toShort() }
            val groups = minOf(hits, 16)

            assertEquals("Wrong number of onsets for $hits hits", groups, onsets.size)
            for (group in 0 until groups) {
                val onset = group * SAMPLE_RATE / 500
                val representedHits = hits / groups + if (group < hits % groups) 1 else 0
                assertEquals("Onset $group for $hits hits", onset, onsets[group])
                assertEquals("Grouped hits must affect PCM, not just the counter",
                    limitedPcm(8192 / PCM_SCALE * 0.65 * sqrt(representedHits.toDouble())), output[onset])
            }
            assertTrue("No onset may create a long backlog", onsets.last() < MAX_STAGGER_FRAMES + sample.size)
            assertEquals(onsets.last() + sample.size, rendered)
            assertTrue(output.drop(rendered).all { it == 0.toShort() })
            assertFalse(mixer.active)
        }

        // Only 32 onset groups are queued: verify Long counting without a huge render loop.
        val counter = PopMixer(shortArrayOf(8192))
        counter.add(Int.MAX_VALUE)
        counter.add(Int.MAX_VALUE)
        assertEquals(2L * Int.MAX_VALUE, counter.acceptedHits)
        counter.clear()
    }

    @Test fun zeroAndNegativeHitCountsProduceNoSoundOrStateChanges() {
        val sample = shortArrayOf(8192, -4096, 2048)
        val mixer = PopMixer(sample)
        for (preview in listOf(false, true)) {
            for (hits in intArrayOf(0, -1, Int.MIN_VALUE)) mixer.add(hits, preview)
        }
        assertEquals(0L, mixer.acceptedHits)
        assertFalse(mixer.active)
        val output = ShortArray(32) { 12345 }
        assertEquals(0, mixer.render(output))
        assertTrue(output.all { it == 0.toShort() })

        mixer.add()
        for (hits in intArrayOf(0, -1, Int.MIN_VALUE)) mixer.add(hits)
        val expected = ShortArray(output.size)
        val reference = PopMixer(sample).apply { add() }
        assertEquals(sample.size, reference.render(expected))
        assertEquals(sample.size, mixer.render(output))
        assertArrayEquals(expected, output)
        assertEquals(1L, mixer.acceptedHits)
        assertFalse(mixer.active)
    }

    @Test fun tenThousandHitsInOneBatchKeepHeadroomWithoutHardClipping() {
        val sample = ShortArray(256) { if (it < 128) Short.MAX_VALUE else Short.MIN_VALUE }
        val mixer = PopMixer(sample)
        mixer.add(10_000) // One batch, at most 16 onset groups, not 10,000 individual voices.
        val output = ShortArray(sample.size + MAX_STAGGER_FRAMES + 32)
        val rendered = mixer.render(output)

        assertEquals(10_000L, mixer.acceptedHits)
        assertEquals(sample.size + MAX_STAGGER_FRAMES, rendered)
        assertTrue(output.any { it > 0 })
        assertTrue(output.any { it < 0 })
        assertTrue("Stress signal must actually exercise the limiter",
            output.maxOf { abs(it.toInt()) } / PCM_SCALE > 0.75)
        assertTrue("Every PCM frame must retain headroom",
            output.all { abs(it.toInt()) / PCM_SCALE < 0.83 })
        assertTrue("No frame may hit either integer clipping rail",
            output.none { it == Short.MAX_VALUE || it == Short.MIN_VALUE })
        assertTrue(output.drop(rendered).all { it == 0.toShort() })
        assertFalse(mixer.active)
    }

    @Test fun actualPopMixIsIdenticalAcrossRenderChunkSizes() {
        val sample = bundledPop()
        fun scheduledMix(chunks: IntArray): ShortArray {
            val mixer = PopMixer(sample)
            mixer.add(4)
            val first = renderChunks(mixer, 137, chunks)
            mixer.add(3, preview = true)
            val middle = renderChunks(mixer, 509, chunks)
            mixer.add(9)
            val tail = renderChunks(mixer, sample.size + MAX_STAGGER_FRAMES + 47, chunks)
            assertEquals(16L, mixer.acceptedHits)
            assertFalse("All scheduled voices must finish", mixer.active)
            return first + middle + tail
        }

        val expected = scheduledMix(intArrayOf(Int.MAX_VALUE))
        assertTrue(energy(expected) > 0.0)
        for (chunks in arrayOf(intArrayOf(1), intArrayOf(64), intArrayOf(7, 511, 2, 128), intArrayOf(1024))) {
            assertArrayEquals("Chunk pattern ${chunks.contentToString()} changed PCM", expected, scheduledMix(chunks))
        }
    }

    @Test fun clearSilencesBothPlayingAndPendingVoicesAndAllowsReuse() {
        val sample = bundledPop()
        val mixer = PopMixer(sample).apply { add(4) }
        // The fourth voice is still pending at frame 257; the others have started.
        assertEquals(257, mixer.render(ShortArray(257)))
        assertTrue(mixer.active)
        mixer.clear()
        val output = ShortArray(sample.size + MAX_STAGGER_FRAMES + 16) { 12345 }
        assertFalse(mixer.active)
        assertEquals(0, mixer.render(output))
        assertTrue(output.all { it == 0.toShort() })
        assertEquals(4L, mixer.acceptedHits)

        mixer.add()
        val expected = ShortArray(output.size)
        val reference = PopMixer(sample).apply { add() }
        assertEquals(sample.size, reference.render(expected))
        assertEquals(sample.size, mixer.render(output))
        assertArrayEquals(expected, output)
        assertEquals(5L, mixer.acceptedHits)
        assertFalse(mixer.active)
    }

    @Test fun repeatedBurstsTerminateWithinOneSamplePlusTheStaggerWindow() {
        // A short 1 kHz fixture exercises the 96-voice capacity without costly long samples.
        val sample = ShortArray(64) { 4096 }
        val mixer = PopMixer(sample, sampleRate = 1000)
        val step = ShortArray(1)
        repeat(12) { burst ->
            mixer.add(23, preview = burst % 2 == 0)
            assertEquals((burst + 1) * 23L, mixer.acceptedHits)
            assertEquals(1, mixer.render(step))
            assertTrue(step[0] > 0)
            assertTrue(mixer.active)
        }
        val bound = sample.size + 30 // At 1 kHz the final stagger is 30 frames.
        val tail = ShortArray(bound + 16) { 12345 }
        val rendered = mixer.render(tail)
        assertTrue("Burst drain took $rendered frames, bound is $bound", rendered in 1..bound)
        assertTrue(energy(tail) > 0.0)
        assertTrue(tail.drop(rendered).all { it == 0.toShort() })
        assertFalse("Bursts must not leave a queued backlog", mixer.active)
        assertEquals(276L, mixer.acceptedHits)
        assertEquals(0, mixer.render(tail))
        assertTrue(tail.all { it == 0.toShort() })
    }

    @Test fun repeatedActualAssetHitsProduceExpectedPcmWithReasonableRms() {
        val sample = bundledPop()
        val hits = 8
        val gap = SAMPLE_RATE * 8 / 1000
        val output = ShortArray((hits - 1) * gap + sample.size)
        val expectedSum = DoubleArray(output.size)
        val mixer = PopMixer(sample)

        repeat(hits) { hit ->
            mixer.add()
            val chunk = ShortArray(if (hit == hits - 1) sample.size else gap)
            assertEquals(chunk.size, mixer.render(chunk))
            chunk.copyInto(output, hit * gap)
            // Independent offline sum of the decoded asset at the exact hit times.
            for (index in sample.indices) expectedSum[hit * gap + index] += sample[index] / PCM_SCALE * 0.65
        }
        val expected = ShortArray(output.size) { limitedPcm(expectedSum[it]) }
        assertArrayEquals("Repeated hits must mix the actual asset without dropping tails", expected, output)
        val rms = sqrt(energy(output) / output.size)
        assertTrue("Normalized PCM RMS $rms is too quiet or too dense", rms in 0.02..0.5)
        assertTrue(output.any { it > 0 })
        assertTrue(output.any { it < 0 })
        assertEquals(hits.toLong(), mixer.acceptedHits)
        assertFalse(mixer.active)
    }

    private fun bundledPop(): ShortArray = PopSample.decode(File("src/main/res/raw/ui_pop.wav").readBytes())

    private fun energy(pcm: ShortArray): Double = pcm.sumOf {
        val normalized = it / PCM_SCALE
        normalized * normalized
    }

    private fun limitedPcm(value: Double): Short = (tanh(value) * 0.82 * 32767).toInt().toShort()

    private fun renderChunks(mixer: PopMixer, frames: Int, chunks: IntArray): ShortArray {
        val result = ShortArray(frames)
        var offset = 0
        var chunkIndex = 0
        while (offset < frames) {
            val size = minOf(chunks[chunkIndex++ % chunks.size], frames - offset)
            val output = ShortArray(size) { 12345 }
            val rendered = mixer.render(output)
            assertTrue("Invalid rendered frame count: $rendered", rendered in 0..size)
            assertTrue("Unwritten chunk frames must be zero", output.drop(rendered).all { it == 0.toShort() })
            output.copyInto(result, offset)
            offset += size
        }
        return result
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val PCM_SCALE = 32768.0
        const val MAX_STAGGER_FRAMES = SAMPLE_RATE * 30 / 1000
    }
}