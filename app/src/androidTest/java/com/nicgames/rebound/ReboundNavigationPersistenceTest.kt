package com.nicgames.rebound

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nicgames.rebound.game.GameEngine
import com.nicgames.rebound.game.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReboundNavigationPersistenceTest : ReboundUiTest() {
    @Test
    fun freshHomeStartsAtRoundOneAndShowsTheFirstShotInstruction() {
        assertScreen(Screen.HOME)
        assertNull(modelValue { it.engine })
        compose.onNodeWithText("REBOUND").assertIsDisplayed()
        compose.onNodeWithText("Best round").assertIsDisplayed()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        listOf("Play", "How to play", "Settings").forEach { text ->
            assertTouchTarget(compose.onNodeWithText(text))
        }
        captureScreen("home")

        click("Play")
        assertScreen(Screen.PLAY)
        compose.onNodeWithTag("round").assertTextEquals("1")
        compose.onNodeWithTag("ball-count").assertTextEquals("1")
        compose.onNodeWithText("Drag to aim. Release to shoot.").assertIsDisplayed()
        assertFalse(modelValue { it.helpSeen })
        val game = snapshot()
        assertEquals(Phase.AIMING, game.phase)
        assertTrue(game.balls.isEmpty())
        assertTrue(game.blocks.all { it.row == 0 && it.hits == 1 })
        assertEquals(game, requireNotNull(GameStore(targetContext).load()).engine)
        assertEquals(1, requireNotNull(GameStore(targetContext).load()).best)
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun helpReturnsToItsOriginThroughGotItHeaderBackAndSystemBack() {
        val before = snapshot()
        click("How to play")
        assertScreen(Screen.HELP)
        compose.onNodeWithText("Aim & release").assertIsDisplayed()
        compose.onNodeWithText("Drag anywhere on the board to aim. Lift your finger to send the balls.").assertIsDisplayed()
        click("Got it")
        assertScreen(Screen.HOME)

        click("How to play")
        headerBack()
        assertScreen(Screen.HOME)
        click("Continue")
        click("Pause")
        click("How to play")
        assertScreen(Screen.HELP)
        back()
        assertScreen(Screen.PAUSE)
        compose.onNodeWithText("Resume").assertIsDisplayed()
        assertEquals("Reading help must not change the saved run", before, snapshot())

        click("How to play")
        click("Got it")
        assertScreen(Screen.PAUSE)
        click("Resume")
        assertScreen(Screen.PLAY)
        compose.onNodeWithTag("round").assertTextEquals("42")
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun cancellingNewGamePreservesTheRunAndConfirmingReplacesIt() {
        val before = snapshot()
        val savedBefore = GameStore(targetContext).load()
        click("New game")
        compose.onNodeWithText("Start a new game?").assertIsDisplayed()
        compose.onNodeWithText("Your current run will be replaced.").assertIsDisplayed()
        dialogAction("Keep playing").assertIsDisplayed().performClick()
        frame()
        compose.onNodeWithText("Start a new game?").assertDoesNotExist()
        assertScreen(Screen.HOME)
        assertEquals(before, snapshot())
        assertEquals(savedBefore, GameStore(targetContext).load())

        // Android Back must dismiss the confirmation rather than discarding the run, too.
        click("New game")
        back()
        compose.onNodeWithText("Start a new game?").assertDoesNotExist()
        assertEquals(before, snapshot())
        click("New game")
        // There are two "New game" labels; select the action inside the dialog explicitly.
        dialogAction("New game").performClick()
        frame()
        assertScreen(Screen.PLAY)
        compose.onNodeWithTag("round").assertTextEquals("1")
        assertEquals(1, snapshot().ballCount)
        assertEquals(Phase.AIMING, snapshot().phase)
        assertEquals(73, modelValue { it.best })
        assertEquals(snapshot(), requireNotNull(GameStore(targetContext).load()).engine)
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun midFlightGameStoreRoundTripRestoresAFreshModelAndResumesExactly() {
        click("Continue")
        advance(128L)
        click("Pause")
        val checkpoint = snapshot()
        assertEquals(Phase.FIRING, checkpoint.phase)
        assertTrue(checkpoint.balls.isNotEmpty())
        assertTrue(checkpoint.pendingLaunches > 0)
        assertTrue(checkpoint.launchCountdown > 0.0)
        assertTrue("The fixture must exercise fractional physics time", checkpoint.accumulator > 0.0)

        val saved = requireNotNull(GameStore(targetContext).load())
        assertEquals(checkpoint, saved.engine)
        GameStore(targetContext).write(saved, immediate = true)
        val loaded = requireNotNull(GameStore(targetContext).load())
        assertEquals("Compare every serialized field, not only round/ball count", saved, loaded)

        recreateWithFreshModel()
        assertEquals(checkpoint, snapshot())
        compose.onNodeWithText("Continue").assertIsDisplayed()
        assertEquals(saved.best, modelValue { it.best })

        // Also prove that the stored queue, remainder and RNG produce the same future frames.
        val original = requireNotNull(GameEngine.restore(checkpoint))
        val restored = requireNotNull(GameEngine.restore(requireNotNull(loaded.engine)))
        repeat(90) { step ->
            original.tick(0.017)
            restored.tick(0.017)
            assertEquals("Restored simulation diverged at step $step", original.snapshot(), restored.snapshot())
        }

        click("Continue")
        advance(192L)
        assertScreen(Screen.PLAY)
        compose.onNodeWithTag("round").assertTextEquals("42")
        val resumed = snapshot()
        assertTrue(resumed.shotElapsed > checkpoint.shotElapsed)
        assertTrue(resumed.pendingLaunches < checkpoint.pendingLaunches)
        assertTrue(resumed.balls != checkpoint.balls)
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun bestAndAllSettingsSurviveFreshModelCreationAndANewGame() {
        click("Settings")
        assertScreen(Screen.SETTINGS)
        assertSetting("Sound", false)
        assertSetting("Vibration", false)
        assertSetting("Animations", true)
        click("Sound")
        click("Vibration")
        click("Animations")
        assertSetting("Sound", true)
        assertSetting("Vibration", true)
        assertSetting("Animations", false)

        click("About & licenses")
        assertScreen(Screen.LICENSES)
        compose.onNodeWithText("Version 1.0.0").assertIsDisplayed()
        back()
        assertScreen(Screen.SETTINGS)
        val stored = requireNotNull(GameStore(targetContext).load())
        assertEquals(73, stored.best)
        assertTrue(stored.sound)
        assertTrue(stored.haptics)
        assertFalse(stored.motion)

        recreateWithFreshModel()
        compose.onNodeWithText("73").assertIsDisplayed()
        click("Settings")
        assertSetting("Sound", true)
        assertSetting("Vibration", true)
        assertSetting("Animations", false)
        headerBack()
        click("New game")
        dialogAction("New game").performClick()
        frame()
        assertScreen(Screen.PLAY)
        compose.onNodeWithTag("round").assertTextEquals("1")
        val afterNewGame = requireNotNull(GameStore(targetContext).load())
        assertEquals(73, afterNewGame.best)
        assertTrue(afterNewGame.sound)
        assertTrue(afterNewGame.haptics)
        assertFalse(afterNewGame.motion)
    }
}