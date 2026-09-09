package com.nicgames.rebound

import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteOrder
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ReboundAudioTest : ReboundUiTest() {
    private fun waitForAudio() { compose.waitUntil(10_000) { compose.activity.audioReady } }

    @Test
    fun soundTestRequestsRealPlaybackAndNeverChangesMediaVolume() {
        waitForAudio()
        val manager = targetContext.getSystemService(AudioManager::class.java)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        assertEquals(AudioManager.STREAM_MUSIC, compose.activity.volumeControlStream)
        click("Settings")
        val count = compose.activity.soundPlaybackCount
        click("Test sound")
        assertEquals(count + 1, compose.activity.soundPlaybackCount)
        assertEquals(volume, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
        click("Sound")
        compose.onNodeWithText("Test sound").assertIsNotEnabled()
        assertEquals(count + 1, compose.activity.soundPlaybackCount)
        captureScreen("settings-audio")
    }

    @Test
    @LaunchWith(ReboundFixture.NEXT_HIT)
    fun realPhysicsImpactTriggersLoadedSoundPlayback() {
        waitForAudio()
        modelValue { if (!it.sound) it.toggleSound() }
        val count = compose.activity.soundPlaybackCount
        click("Continue")
        advance(96)
        assertTrue("Fixture must actually hit a block", snapshot().totalHits > 0)
        assertTrue("Rendered game's impact callback must reach SoundPool", compose.activity.soundPlaybackCount > count)
    }

    @Test
    @LaunchWith(ReboundFixture.RAPID_HITS)
    fun threeCloselySpacedImpactFramesAllRequestPops() {
        waitForAudio()
        modelValue { if (!it.sound) it.toggleSound() }
        val initial = compose.activity.soundPlaybackCount
        click("Continue")
        advance(96)
        assertEquals(3L, snapshot().totalHits)
        assertEquals("Each separate hit-bearing frame must reach audio", 3, compose.activity.soundPlaybackCount - initial)
    }

    @Test
    fun bundledRecordedPopDecodesToSignalAndMatchesLicensedSource() {
        val bytes = targetContext.resources.openRawResource(R.raw.block_pop).use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("Exact unmodified CC0 source preview", "09a72dba775c2407cd2227fb5fceb385d5dd18979fc1715f4d07c63d5f444a83", hash)
        val credit = targetContext.resources.openRawResource(R.raw.audio_credit).bufferedReader().use { it.readText() }
        assertTrue(credit.contains("Mafon2") && credit.contains("CC0"))
        val extractor = MediaExtractor()
        targetContext.resources.openRawResourceFd(R.raw.block_pop).use { fd ->
            extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
        val format = extractor.getTrackFormat(0)
        assertTrue(format.getLong(MediaFormat.KEY_DURATION) in 100_000L..500_000L)
        extractor.selectTrack(0)
        val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
        var sum = 0.0
        var samples = 0
        var ended = false
        try {
            decoder.configure(format, null, null, 0)
            decoder.start()
            var inputEnded = false
            val info = MediaCodec.BufferInfo()
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (!ended && android.os.SystemClock.uptimeMillis() < deadline) {
                if (!inputEnded) {
                    val input = decoder.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        val data = requireNotNull(decoder.getInputBuffer(input))
                        val length = extractor.readSampleData(data, 0)
                        if (length < 0) {
                            inputEnded = true
                            decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            decoder.queueInputBuffer(input, 0, length, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val output = decoder.dequeueOutputBuffer(info, 10_000)
                if (output >= 0) {
                    val data = requireNotNull(decoder.getOutputBuffer(output)).duplicate().order(ByteOrder.LITTLE_ENDIAN)
                    data.position(info.offset); data.limit(info.offset + info.size)
                    while (data.remaining() >= 2) { val sample = data.short / 32768.0; sum += sample * sample; samples++ }
                    ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(output, false)
                }
            }
            decoder.stop()
        } finally { decoder.release(); extractor.release() }
        assertTrue("Recorded pop must finish decoding", ended)
        assertTrue("Decoded sample contains a usable recording", samples > 4000)
        assertTrue("Pop must not be silent", kotlin.math.sqrt(sum / samples) > .02)
    }

    @Test
    fun nearbyHitsAreNotDiscardedByTheOld55MillisecondGate() {
        val audio = compose.runOnIdle { GameAudio(targetContext) }
        try {
            compose.waitUntil(10_000) { audio.ready }
            compose.runOnIdle {
                assertTrue(audio.hit())
                assertTrue("An immediately following hit must also play", audio.hit())
                assertTrue("A third close hit must also play", audio.hit())
                assertEquals(3, audio.playbackCount)
                audio.stop()
                assertTrue(audio.hit())
                assertEquals(4, audio.playbackCount)
            }
        } finally { compose.runOnIdle { audio.release() } }
        assertFalse("A released pool cannot play", audio.hit())
    }
}