package com.nicgames.rebound

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nicgames.rebound.game.Board
import com.nicgames.rebound.game.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.atan2

@RunWith(AndroidJUnit4::class)
class ReboundGameplayTest : ReboundUiTest() {
    @Test
    @LaunchWith(ReboundFixture.ROUND_42, helpSeen = false)
    fun realPointerDragAimsUntilReleaseThenFiresAndMovesBalls() {
        click("Continue")
        assertFalse(modelValue { it.helpSeen })
        compose.onNodeWithText("Pull back to aim. Release to shoot.").assertIsDisplayed()
        val before = snapshot()
        val board = compose.onNodeWithTag("game-board")
        val start = boardPoint(180.0, 250.0)
        val end = boardPoint(84.0, 400.0)
        val expectedAngle = atan2(-150.0, 96.0)

        // Keep the same real pointer down across calls, proving that moving is not firing.
        board.performTouchInput {
            down(start)
            moveTo(end, delayMillis = 160L)
        }
        frame()
        assertTrue(modelValue { it.aiming })
        assertEquals(expectedAngle, modelValue { it.angle }, 0.0001)
        assertEquals("Dragging alone must not launch or alter the engine", before, snapshot())

        board.performTouchInput { up() }
        frame()
        val fired = snapshot()
        assertEquals(Phase.FIRING, fired.phase)
        assertFalse(modelValue { it.aiming })
        assertEquals(expectedAngle, fired.shotAngle, 0.0001)
        assertTrue(fired.balls.isNotEmpty())
        assertEquals(expectedAngle, atan2(fired.balls.first().vy, fired.balls.first().vx), 0.0001)
        assertTrue("A successful pointer shot records that the tutorial was seen",
            targetContext.getSharedPreferences("rebound", 0).getBoolean("helpSeen", false))
        compose.onNodeWithText("Pull back to aim. Release to shoot.").assertDoesNotExist()

        advance(192L)
        val moving = snapshot()
        assertEquals(Phase.FIRING, moving.phase)
        assertTrue(moving.shotElapsed > fired.shotElapsed)
        assertTrue(moving.balls.first().y < fired.balls.first().y)
        assertTrue(moving.pendingLaunches < fired.pendingLaunches)
        board.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "Round 42. 42 balls. 7 blocks. Balls moving.",
        ))
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun cancelledPointerGestureDoesNotShootOrLeaveAimActive() {
        click("Continue")
        val before = snapshot()
        val board = compose.onNodeWithTag("game-board")
        val start = boardPoint(180.0, 250.0)
        val end = boardPoint(270.0, 400.0)
        board.performTouchInput { down(start); moveTo(end, delayMillis = 96L) }
        frame()
        assertTrue(modelValue { it.aiming })

        board.performTouchInput { cancel() }
        frame()
        advance(320L)
        assertFalse(modelValue { it.aiming })
        assertEquals("ACTION_CANCEL is not a released shot", before, snapshot())
        assertEquals(before, requireNotNull(GameStore(targetContext).load()).engine)
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun pauseFreezesEveryPhysicsFieldAndResumeRestartsMovement() {
        val initial = snapshot()
        click("Continue")
        advance(160L)
        assertTrue("Positive control: the UI frame loop must be running", snapshot().shotElapsed > initial.shotElapsed)

        click("Pause")
        assertScreen(Screen.PAUSE)
        compose.onNodeWithText("PAUSED").assertIsDisplayed()
        val paused = snapshot()
        assertEquals(Phase.FIRING, paused.phase)
        assertEquals(paused, requireNotNull(GameStore(targetContext).load()).engine)
        captureScreen("paused")

        advance(2_000L)
        assertEquals("Pause must freeze balls, queues, RNG, effects and accumulator", paused, snapshot())
        click("Resume")
        advance(192L)
        assertScreen(Screen.PLAY)
        val resumed = snapshot()
        assertEquals(paused.round, resumed.round)
        assertTrue(resumed.shotElapsed > paused.shotElapsed)
        assertTrue(resumed.balls != paused.balls)
        compose.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun homeContinueKeepsTheCurrentVolleyAndSystemBackPausesIt() {
        click("Continue")
        advance(96L)
        back()
        assertScreen(Screen.PAUSE)
        val paused = snapshot()

        click("Home")
        assertScreen(Screen.HOME)
        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.onNodeWithText("Play").assertDoesNotExist()
        advance(1_000L)
        assertEquals(paused, snapshot())
        assertEquals(paused, requireNotNull(GameStore(targetContext).load()).engine)

        // Check immediately, before advancing a UI frame: Continue must not replace the run.
        compose.onNodeWithText("Continue").performClick()
        assertEquals(paused, snapshot())
        frame()
        advance(160L)
        compose.onNodeWithTag("round").assertTextEquals("42")
        assertTrue(snapshot().shotElapsed > paused.shotElapsed)
        back()
        assertScreen(Screen.PAUSE)
        val pausedAgain = snapshot()
        advance(500L)
        assertEquals(pausedAgain, snapshot())
        back()
        assertScreen(Screen.PLAY)
        compose.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    @LaunchWith(ReboundFixture.BEFORE_LOSS)
    fun validAdvancingFixtureReachesResultsAndPlayAgainStartsANewRun() {
        assertEquals(Phase.ADVANCING, snapshot().phase)
        assertEquals(8, snapshot().blocks.single().row)
        click("Continue")
        advance(320L)

        assertScreen(Screen.RESULTS)
        compose.onNodeWithText("GAME\nOVER").assertIsDisplayed()
        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithText("73").assertIsDisplayed()
        val terminal = snapshot()
        assertEquals(Phase.GAME_OVER, terminal.phase)
        assertEquals(9, terminal.blocks.single().row)
        assertEquals(terminal, requireNotNull(GameStore(targetContext).load()).engine)
        assertFalse(modelValue { it.canContinue })
        assertTouchTarget(compose.onNodeWithText("Play again"))
        assertTouchTarget(compose.onNodeWithText("Home"))
        captureScreen("results")
        advance(640L)
        assertEquals(terminal, snapshot())

        click("Play again")
        assertScreen(Screen.PLAY)
        compose.onNodeWithTag("round").assertTextEquals("1")
        compose.onNodeWithTag("ball-count").assertTextEquals("1")
        assertEquals(Phase.AIMING, snapshot().phase)
        assertEquals(73, modelValue { it.best })
        assertEquals(0L, snapshot().totalHits)
    }
}