package com.nicgames.rebound

import android.media.AudioManager
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    fun bundledHitSoundContainsAudibleRangePcmInsteadOfSilence() {
        val bytes = targetContext.resources.openRawResource(R.raw.block_hit).use { it.readBytes() }
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44_100, buffer.getInt(24))
        assertEquals(16, buffer.getShort(34).toInt())
        val samples = (bytes.size - 44) / 2
        var sum = 0.0
        for (i in 0 until samples) { val sample = buffer.getShort(44 + i * 2) / 32768.0; sum += sample * sample }
        assertTrue("Hit should be long enough to hear", samples / 44100.0 > .06)
        assertTrue("Hit resource must contain signal", kotlin.math.sqrt(sum / samples) > .1)
    }
}