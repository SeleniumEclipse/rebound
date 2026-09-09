package com.nicgames.rebound

import android.media.AudioManager
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.nicgames.rebound.audio.PopSample
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
        compose.waitUntil(5000) { compose.activity.soundPlayedFrames > 2000 }
        assertTrue("Actual nonzero PCM was written to AudioTrack", compose.activity.soundAudibleFrames > 500)
        assertTrue(compose.activity.soundWrittenFrames > 2000)
        assertEquals(0, compose.activity.soundWriteFailures)
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
        assertTrue("Rendered game's impact callback must reach the audio mixer", compose.activity.soundPlaybackCount > count)
        compose.waitUntil(5000) { compose.activity.soundPlayedFrames > 2000 }
        assertTrue(compose.activity.soundAudibleFrames > 500)
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
    fun bundledUiPopContainsPromptSoftPcmAndRequiredAttribution() {
        val bytes = targetContext.resources.openRawResource(R.raw.ui_pop).use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("Prepared reviewed UI sound", "d2e5adfec812a575e242ba69507a11e1e6f336ec940bbe971a4bfe39b9082919", hash)
        val credit = targetContext.resources.openRawResource(R.raw.audio_credit).bufferedReader().use { it.readText() }
        assertTrue(credit.contains("Marevnik") && credit.contains("Attribution 4.0"))
        val samples = PopSample.decode(bytes)
        assertTrue(samples.size / 44100.0 in .04.. .10)
        assertTrue(samples.indexOfFirst { kotlin.math.abs(it.toInt()) > 655 } < 529)
        assertTrue(samples.maxOf { kotlin.math.abs(it.toInt()) } < 22_000)
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
            }
            compose.waitUntil(5000) { audio.playedFrames > 2500 }
            assertTrue(audio.audibleFrames > 500)
            assertEquals(0, audio.writeFailures)
            val flush = audio.flushCount
            compose.runOnIdle { audio.stop() }
            compose.waitUntil(5000) { audio.flushCount > flush }
            val written = audio.writtenFrames
            val audible = audio.audibleFrames
            compose.runOnIdle { assertTrue(audio.hit()); assertEquals(4, audio.playbackCount) }
            compose.waitUntil(5000) { audio.playedFrames > 2500 }
            assertTrue(audio.writtenFrames > written + 2500)
            assertTrue(audio.audibleFrames > audible + 500)
        } finally { compose.runOnIdle { audio.release() } }
        assertFalse("A released output cannot play", audio.hit())
    }

    @Test
    @LaunchWith(ReboundFixture.SAME_FRAME_HITS)
    fun simultaneousHitsAreNotCollapsedIntoOneAudioRequest() {
        waitForAudio()
        modelValue { if (!it.sound) it.toggleSound() }
        val count = compose.activity.soundPlaybackCount
        click("Continue")
        advance(64)
        assertEquals(3L, snapshot().totalHits)
        assertEquals(3, compose.activity.soundPlaybackCount - count)
        compose.waitUntil(5000) { compose.activity.soundPlayedFrames > 2500 }
        assertTrue(compose.activity.soundAudibleFrames > 500)
        assertEquals(0, compose.activity.soundWriteFailures)
    }

    @Test
    fun isolatedPopsContinueReachingTheDeviceAfterEachAudioBufferDrains() {
        val audio = compose.runOnIdle { GameAudio(targetContext) }
        try {
            var lastWritten = 0L
            var lastAudible = 0L
            repeat(8) {
                compose.runOnIdle { assertTrue(audio.hit()) }
                compose.waitUntil(5000) { audio.idle && audio.writtenFrames > lastWritten + 3000 }
                val expected = audio.writtenFrames
                compose.waitUntil(5000) { audio.playedFrames >= expected }
                assertTrue("Each isolated pop writes fresh nonzero PCM", audio.audibleFrames > lastAudible + 500)
                lastWritten = audio.writtenFrames
                lastAudible = audio.audibleFrames
            }
            assertEquals(8, audio.playbackCount)
            assertEquals(0, audio.writeFailures)
            java.io.File(targetContext.filesDir, "audio-output-evidence.json").writeText(
                """{"isolatedPops":8,"writtenFrames":${audio.writtenFrames},"playedFrames":${audio.playedFrames},"nonzeroFrames":${audio.audibleFrames},"writeFailures":${audio.writeFailures}}""",
            )
        } finally { compose.runOnIdle { audio.release() } }
    }
}