package com.nicgames.rebound

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nicgames.rebound.game.Phase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ReboundControlsTest : ReboundUiTest() {
    private fun chooseSpeed(value: Float) {
        compose.onNodeWithTag("speed-slider").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(value)) }
        frame()
        modelValue { it.finishSpeedChange() }
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun settingsFromPlayFreezeAndReturnToTheSameVolley() {
        click("Continue")
        advance(96)
        click("Settings")
        assertScreen(Screen.SETTINGS)
        val before = snapshot()
        advance(600)
        assertEquals(before, snapshot())
        chooseSpeed(2.5f)
        compose.onNodeWithTag("speed-value").assertTextEquals("2.5×")
        assertEquals(2.5, modelValue { it.preferredSpeed }, 0.0)
        captureScreen("settings")
        headerBack()
        assertScreen(Screen.PLAY)
        advance(96)
        assertTrue(snapshot().shotElapsed > before.shotElapsed)
        click("Pause")
        click("Settings")
        headerBack()
        assertScreen(Screen.PAUSE)
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun realSliderDragPersistsFractionalSpeedAcrossRestartAndNewGame() {
        click("Settings")
        compose.onNodeWithTag("speed-slider").performTouchInput {
            // A continuous drag has motion samples after crossing touch slop;
            // one jump straight to ACTION_UP only exercises drag recognition.
            // The semantics bounds include horizontal touch-target expansion;
            // start inside the visible track, not in that expanded outer margin.
            swipe(centerLeft + androidx.compose.ui.geometry.Offset(width * .08f, 0f), center, durationMillis = 400)
        }
        frame()
        val selected = modelValue { it.preferredSpeed }
        assertTrue("Slider must select an intermediate speed, got $selected", selected in 3.0..4.0)
        chooseSpeed(2.7f)
        assertEquals(2.7, requireNotNull(GameStore(targetContext).load()).preferredSpeed, 0.0)
        recreateWithFreshModel()
        assertEquals(2.7, modelValue { it.preferredSpeed }, 0.0)
        click("New game")
        dialogAction("New game").performClick()
        frame()
        assertEquals(2.7, modelValue { it.preferredSpeed }, 0.0)
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun temporaryBoostSurvivesPauseButResetsOnNaturalVolleyEnd() {
        modelValue { it.updatePreferredSpeed(2.4) }
        click("Continue")
        click("Speed up")
        assertEquals(6.0, modelValue { it.effectiveSpeed }, 0.0)
        assertEquals(2.4, modelValue { it.preferredSpeed }, 0.0)
        compose.onNodeWithTag("speed-up").assertIsSelected()
        captureScreen("boosted")
        click("Settings")
        assertTrue(modelValue { it.speedUpActive })
        headerBack()
        click("Pause")
        val paused = snapshot()
        advance(300)
        assertEquals(paused, snapshot())
        click("Resume")
        // Deterministic simulation clock, stop precisely when the volley ends.
        modelValue { model ->
            var ticks = 0
            while (model.engine!!.phase == Phase.FIRING && ticks++ < 2_000) model.tick(.01)
            assertTrue(ticks < 2_000)
            assertFalse(model.speedUpActive)
            assertEquals(2.4, model.effectiveSpeed, 0.0)
        }
        frame()
        compose.onNodeWithTag("speed-up").assertDoesNotExist()
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun maxPreferenceHidesBoostAndMidVolleyChangesStaySafe() {
        click("Continue")
        click("Settings")
        chooseSpeed(6f)
        headerBack()
        compose.onNodeWithText("Speed up").assertDoesNotExist()
        assertEquals(6.0, modelValue { it.effectiveSpeed }, 0.0)
        click("Settings")
        chooseSpeed(1.5f)
        headerBack()
        compose.onNodeWithText("Speed up").assertIsDisplayed()
        click("Speed up")
        click("Settings")
        chooseSpeed(6f)
        assertFalse(modelValue { it.speedUpActive })
        chooseSpeed(2f)
        headerBack()
        assertEquals(2.0, modelValue { it.effectiveSpeed }, 0.0)
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun boostedSaveRestoresOnlyForThatVolleyAndCollectResetsIt() {
        modelValue { it.updatePreferredSpeed(3.2) }
        click("Continue")
        click("Speed up")
        click("Pause")
        recreateWithFreshModel()
        assertEquals(3.2, modelValue { it.preferredSpeed }, 0.0)
        assertTrue(modelValue { it.speedUpActive })
        click("Continue")
        modelValue { it.recall() }
        assertFalse(modelValue { it.speedUpActive })
        assertEquals(3.2, modelValue { it.effectiveSpeed }, 0.0)
        assertFalse(requireNotNull(GameStore(targetContext).load()).speedUpActive)
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun fractionalSpeedScalesTimeAndInvalidValuesAreIgnored() {
        modelValue { it.updatePreferredSpeed(2.5) }
        click("Continue")
        val before = snapshot()
        modelValue { it.tick(.04) }
        val after = snapshot()
        assertEquals(.1, after.shotElapsed - before.shotElapsed, .009)
        modelValue { it.updatePreferredSpeed(Double.NaN); it.updatePreferredSpeed(Double.POSITIVE_INFINITY) }
        assertEquals(2.5, modelValue { it.preferredSpeed }, 0.0)
        modelValue { it.updatePreferredSpeed(9.0) }
        assertEquals(6.0, modelValue { it.preferredSpeed }, 0.0)
        modelValue { it.updatePreferredSpeed(-10.0) }
        assertEquals(1.0, modelValue { it.preferredSpeed }, 0.0)
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun backgroundInSettingsReturnsToPauseInsteadOfAutoResuming() {
        click("Continue")
        click("Settings")
        modelValue { it.background() }
        val frozen = snapshot()
        headerBack()
        assertScreen(Screen.PAUSE)
        advance(500)
        assertEquals(frozen, snapshot())
    }

    @Test
    fun legacySaveWithoutNewSpeedFieldsStillLoads() {
        val snapshot = ReboundFixtures.round42()
        val legacy = """{"version":1,"engine":${Json.encodeToString(snapshot)},"best":73,"sound":false,"haptics":false,"motion":true}"""
        assertTrue(targetContext.getSharedPreferences("rebound", 0).edit().putString("game", legacy).commit())
        val loaded = requireNotNull(GameStore(targetContext).load())
        assertEquals(snapshot, loaded.engine)
        assertEquals(1.0, loaded.preferredSpeed, 0.0)
        assertFalse(loaded.speedUpActive)
    }

    @Test
    @LaunchWith(ReboundFixture.FIRST_LANDING)
    fun nextLaunchMarkerRepaintsImmediatelyAndCeilingIsVisible() {
        click("Continue")
        val board = compose.onNodeWithTag("game-board")
        val before = board.captureToImage().toPixelMap()
        advance(32)
        assertEquals(Phase.FIRING, snapshot().phase)
        assertEquals(180.0, snapshot().launchX, 0.0)
        assertEquals(80.0, modelValue { it.engine!!.nextLaunchX }, .0001)
        val after = board.captureToImage().toPixelMap()
        val old = boardPoint(180.0, 491.0)
        val next = boardPoint(80.0, 491.0)
        assertTrue(before[old.x.toInt(), old.y.toInt()].red < .3f)
        assertTrue(after[old.x.toInt(), old.y.toInt()].red > .7f)
        assertTrue(after[next.x.toInt(), next.y.toInt()].red < .3f)
        val ceiling = boardPoint(180.0, com.nicgames.rebound.game.Board.TOP)
        assertTrue("Ceiling must be visibly darker than the empty field", after[ceiling.x.toInt(), ceiling.y.toInt()].red < .7f)
        captureScreen("landing")
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun tappingOrReturningToDragOriginDoesNotFire() {
        click("Continue")
        val before = snapshot()
        val board = compose.onNodeWithTag("game-board")
        val start = boardPoint(180.0, 250.0)
        val pulled = boardPoint(120.0, 350.0)
        board.performTouchInput { click(start) }
        frame()
        assertEquals(before, snapshot())
        board.performTouchInput { down(start); moveTo(pulled, 96); moveTo(start, 96); up() }
        frame()
        assertFalse(modelValue { it.aiming })
        assertEquals(before, snapshot())
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun newGameHasAVisibleOutlineAndPlayControlsDoNotOverlap() {
        val image = compose.onNodeWithText("New game").captureToImage().toPixelMap()
        var darkEdge = 0
        for (x in 2 until image.width - 2) {
            if ((0 until minOf(5, image.height)).any { y -> image[x, y].red < .4f }) darkEdge++
        }
        assertTrue("New game must have a drawn border", darkEdge > image.width / 2)
        click("Continue")
        val settings = compose.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
        val pause = compose.onNodeWithText("Pause").fetchSemanticsNode().boundsInRoot
        val score = compose.onNodeWithTag("round").fetchSemanticsNode().boundsInRoot
        assertTrue(settings.right <= pause.left)
        assertTrue(score.right <= settings.left)
    }
}