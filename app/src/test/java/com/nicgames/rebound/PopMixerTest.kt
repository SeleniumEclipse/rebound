package com.nicgames.rebound

import com.nicgames.rebound.audio.PopMixer
import com.nicgames.rebound.audio.PopSample
import com.nicgames.rebound.game.Ball
import com.nicgames.rebound.game.Block
import com.nicgames.rebound.game.Board
import com.nicgames.rebound.game.GameEngine
import com.nicgames.rebound.game.Phase
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

/** Pure JVM PCM checks; these do not test device playback or perceived loudness. */
class PopMixerTest {
    @Test fun collisionBatchSizeDoesNotAmplifyOrDistortThePop() {
        val sample = bundledPop()
        val single = PopMixer(sample).apply { add() }
        val crowded = PopMixer(sample).apply { add(10_000) }
        val expected = ShortArray(sample.size + SAMPLE_RATE / 10)
        val actual = ShortArray(expected.size)
        single.render(expected)
        crowded.render(actual)
        assertEquals(10_000L, crowded.acceptedHits)
        assertArrayEquals("Collision density must not multiply sample gain or flatten its waveform", expected, actual)
    }

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
        assertEquals(linearPcm(-8192 / PCM_SCALE * 0.5), output[5])
        assertTrue("Unused part of reused buffer must be zero", output.drop(6).all { it == 0.toShort() })
        assertFalse(mixer.active)

        output.fill(12345)
        assertEquals(0, mixer.render(output))
        assertTrue(output.all { it == 0.toShort() })
        assertEquals(1L, mixer.acceptedHits)
    }

    @Test fun secondHitOverlapsWithoutRestartingOrStealingTheFirstTail() {
        val sample = ShortArray(100) { 4096 }
        val gap = 64
        val solo = ShortArray(sample.size)
        val reference = PopMixer(sample, sampleRate = 1000).apply { add() }
        assertEquals(solo.size, reference.render(solo))

        val mixer = PopMixer(sample, sampleRate = 1000).apply { add() }
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

    @Test fun sameFrameHitsKeepTheirCountAndShareOneFixedLevelOnset() {
        for (hits in intArrayOf(1, 3, 16, 37)) {
            val sample = shortArrayOf(8192)
            val mixer = PopMixer(sample)
            mixer.add(hits)
            assertEquals(hits.toLong(), mixer.acceptedHits)
            val output = ShortArray(sample.size + ONSET_INTERVAL_FRAMES + 16)
            val rendered = mixer.render(output)
            val onsets = output.indices.filter { output[it] != 0.toShort() }
            assertEquals(listOf(0), onsets)
            assertEquals(linearPcm(8192 / PCM_SCALE * 0.5), output[0])
            assertEquals(1L, mixer.emittedPops)
            assertEquals(sample.size, rendered)
            assertTrue(output.drop(rendered).all { it == 0.toShort() })
            assertFalse(mixer.active)
        }

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

    @Test fun fullScaleAssetAndTenThousandHitsRetainLinearHeadroom() {
        val sample = ShortArray(256) { if (it < 128) Short.MAX_VALUE else Short.MIN_VALUE }
        val mixer = PopMixer(sample)
        mixer.add(10_000)
        val output = ShortArray(sample.size + ONSET_INTERVAL_FRAMES + 32)
        val rendered = mixer.render(output)

        assertEquals(10_000L, mixer.acceptedHits)
        assertEquals(sample.size, rendered)
        assertTrue(output.any { it > 0 })
        assertTrue(output.any { it < 0 })
        assertEquals(0.325, output.maxOf { abs(it.toInt()) } / PCM_SCALE, 0.0001)
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
            val tail = renderChunks(mixer, sample.size + ONSET_INTERVAL_FRAMES + 47, chunks)
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
        assertEquals(257, mixer.render(ShortArray(257)))
        mixer.add(3)
        assertTrue(mixer.active)
        mixer.clear()
        val output = ShortArray(sample.size + ONSET_INTERVAL_FRAMES + 16) { 12345 }
        assertFalse(mixer.active)
        assertEquals(0, mixer.render(output))
        assertTrue(output.all { it == 0.toShort() })
        assertEquals(7L, mixer.acceptedHits)

        mixer.add()
        val expected = ShortArray(output.size)
        val reference = PopMixer(sample).apply { add() }
        assertEquals(sample.size, reference.render(expected))
        assertEquals(sample.size, mixer.render(output))
        assertArrayEquals(expected, output)
        assertEquals(8L, mixer.acceptedHits)
        assertFalse(mixer.active)
    }

    @Test fun repeatedBurstsTerminateWithinOneSamplePlusOneOnsetInterval() {
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
        val bound = sample.size + 60
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
        val output = ShortArray((hits - 1) * gap + sample.size + ONSET_INTERVAL_FRAMES)
        val expectedSum = DoubleArray(output.size)
        val mixer = PopMixer(sample)

        repeat(hits) { hit ->
            mixer.add()
            val chunk = ShortArray(if (hit == hits - 1) sample.size + ONSET_INTERVAL_FRAMES else gap)
            mixer.render(chunk)
            chunk.copyInto(output, hit * gap)
        }
        for (onset in intArrayOf(0, ONSET_INTERVAL_FRAMES)) {
            for (index in sample.indices) expectedSum[onset + index] += sample[index] / PCM_SCALE * 0.5
        }
        val expected = ShortArray(output.size) { linearPcm(expectedSum[it]) }
        assertArrayEquals("Dense feedback must be a linear sum of complete, unaltered pops", expected, output)
        val rms = sqrt(energy(output) / output.size)
        assertTrue("Normalized PCM RMS $rms is too quiet or too dense", rms in 0.02..0.5)
        assertTrue(output.any { it > 0 })
        assertTrue(output.any { it < 0 })
        assertEquals(hits.toLong(), mixer.acceptedHits)
        assertEquals(2L, mixer.emittedPops)
        assertFalse(mixer.active)
    }

    @Test fun allSelectableSpeedsHaveBoundedFeedbackAndNoAudioBacklog() {
        val sample = bundledPop()
        val framesPerDisplayFrame = SAMPLE_RATE / 60
        for (speedTenths in 10..60) {
            val mixer = PopMixer(sample)
            var hits = 0
            var peak = 0
            repeat(120) { displayFrame ->
                val totalHits = (displayFrame + 1) * 2 * speedTenths / 10
                mixer.add(totalHits - hits)
                hits = totalHits
                val output = ShortArray(framesPerDisplayFrame)
                mixer.render(output)
                peak = maxOf(peak, output.maxOf { abs(it.toInt()) })
            }
            val tail = ShortArray(sample.size + ONSET_INTERVAL_FRAMES)
            mixer.render(tail)
            assertFalse("Audio backlog at ${speedTenths / 10.0}x", mixer.active)
            assertEquals(hits.toLong(), mixer.acceptedHits)
            assertTrue("Feedback must remain audible but bounded", mixer.emittedPops in 25L..35L)
            assertTrue("Density must not drive saturation", peak / PCM_SCALE in 0.1..0.65)
            assertEquals(0, mixer.render(tail))
            assertTrue(tail.all { it == 0.toShort() })
        }
    }

    @Test fun densePhysicsVolleysKeepLinearAudioAtNormalFractionalAndMaximumSpeed() {
        val sample = bundledPop()
        val initial = GameEngine(6401).snapshot().copy(
            phase = Phase.FIRING, round = 300, ballCount = 250, nextId = 1000,
            pickups = emptyList(),
            blocks = listOf(0, 7).flatMap { row ->
                (0 until Board.COLUMNS).filter { it != 3 }.map { column ->
                    Block(1L + row * Board.COLUMNS + column, column, row, 600)
                }
            },
            balls = List(250) { index ->
                val angle = -Math.PI + .15 + (index % 31) * (Math.PI - .3) / 30
                Ball(24.0 + (index % 25) * 310.0 / 24, 105.0 + (index / 25) * 12, 430 * cos(angle), 430 * sin(angle))
            },
        )
        var referenceHits: Long? = null
        for (speed in listOf(1.0, 3.5, 6.0)) {
            val engine = requireNotNull(GameEngine.restore(initial))
            val mixer = PopMixer(sample)
            val chunks = ArrayList<ShortArray>()
            val hitSchedule = ArrayList<Pair<Int, Int>>()
            var displayFrame = 0
            while (engine.phase == Phase.FIRING || engine.phase == Phase.ADVANCING) {
                assertTrue("Volley did not finish at $speed", displayFrame < 60 * 40)
                val before = engine.totalHits
                engine.tick(speed / 60)
                val hits = (engine.totalHits - before).toInt()
                if (hits > 0) {
                    mixer.add(hits)
                    hitSchedule.add(displayFrame * (SAMPLE_RATE / 60) to hits)
                }
                chunks.add(ShortArray(SAMPLE_RATE / 60).also { mixer.render(it) })
                displayFrame++
            }
            chunks.add(ShortArray(sample.size + ONSET_INTERVAL_FRAMES).also { mixer.render(it) })
            assertFalse("No sound backlog after $speed volley", mixer.active)
            assertTrue("Must exercise dense physics, not an empty board", engine.totalHits > 1000)
            assertEquals(engine.totalHits, mixer.acceptedHits)
            if (referenceHits == null) referenceHits = engine.totalHits
            assertEquals(referenceHits, engine.totalHits)
            assertTrue(mixer.emittedPops > 30)
            assertTrue(mixer.emittedPops <= displayFrame * (SAMPLE_RATE / 60) / ONSET_INTERVAL_FRAMES + 2L)
            val pcm = ShortArray(chunks.sumOf { it.size })
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(pcm, offset)
                offset += chunk.size
            }
            val peak = pcm.maxOf { abs(it.toInt()) } / PCM_SCALE
            assertTrue("Unclipped linear output at $speed", peak in .1.. .65)
            val audit = File("../build/audio-audit")
            assertTrue(audit.isDirectory || audit.mkdirs())
            writeWave(File(audit, "volley-${speed}x.wav"), pcm)
            val trace = buildJsonObject {
                put("speed", speed)
                put("sampleRate", SAMPLE_RATE)
                put("frames", pcm.size)
                put("hits", engine.totalHits)
                put("emittedPops", mixer.emittedPops)
                put("peak", peak)
                put("rms", sqrt(energy(pcm) / pcm.size))
                put("events", buildJsonArray {
                    for ((frame, hits) in hitSchedule) add(buildJsonObject {
                        put("frame", frame)
                        put("hits", hits)
                    })
                })
            }
            File(audit, "volley-${speed}x.json").writeText(trace.toString())
            println("Dense audio: speed=$speed hits=${engine.totalHits} pops=${mixer.emittedPops} peak=$peak")
        }
    }

    private fun writeWave(file: File, pcm: ShortArray) {
        val bytes = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(bytes.capacity() - 8)
        bytes.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        bytes.putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2)
        bytes.putShort(2).putShort(16).put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size * 2)
        for (value in pcm) bytes.putShort(value)
        file.writeBytes(bytes.array())
    }

    private fun bundledPop(): ShortArray = PopSample.decode(File("src/main/res/raw/ui_pop.wav").readBytes())

    private fun energy(pcm: ShortArray): Double = pcm.sumOf {
        val normalized = it / PCM_SCALE
        normalized * normalized
    }

    private fun linearPcm(value: Double): Short = (value * 32767).toInt().toShort()

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
        const val ONSET_INTERVAL_FRAMES = SAMPLE_RATE * 60 / 1000
    }
}