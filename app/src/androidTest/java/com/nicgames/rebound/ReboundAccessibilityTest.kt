package com.nicgames.rebound

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nicgames.rebound.game.Phase
import com.nicgames.rebound.ui.Coral
import com.nicgames.rebound.ui.DeepViolet
import com.nicgames.rebound.ui.Field
import com.nicgames.rebound.ui.Green
import com.nicgames.rebound.ui.Ink
import com.nicgames.rebound.ui.Orange
import com.nicgames.rebound.ui.Teal
import com.nicgames.rebound.ui.Violet
import com.nicgames.rebound.ui.Yellow
import com.nicgames.rebound.ui.blockColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReboundAccessibilityTest : ReboundUiTest() {
    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun visibleControlsHaveAtLeast48DpTargetsOnTheExistingDisplay() {
        // Observe the existing device. Do not resize it, change font scale or change system settings.
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val density = targetContext.resources.displayMetrics.density
        assertTrue("This static layout check requires the requested 300+dp-wide display", root.width / density >= 300f)
        listOf("Continue", "New game", "How to play", "Settings").forEach {
            assertTouchTarget(compose.onNodeWithText(it))
        }

        click("Settings")
        assertTouchTarget(compose.onNodeWithContentDescription("Back"))
        listOf("Sound", "Vibration", "Animations", "About & licenses").forEach {
            assertTouchTarget(compose.onNodeWithText(it))
        }
        headerBack()
        click("How to play")
        assertTouchTarget(compose.onNodeWithContentDescription("Back"))
        assertTouchTarget(compose.onNodeWithText("Got it"))
        click("Got it")

        click("New game")
        assertTouchTarget(dialogAction("New game"))
        assertTouchTarget(dialogAction("Keep playing"))
        dialogAction("Keep playing").performClick()
        frame()
        click("Continue")
        assertTouchTarget(compose.onNodeWithText("Pause"))
        compose.onNodeWithTag("game-board").assertIsDisplayed()
        click("Pause")
        listOf("Resume", "How to play", "Home").forEach {
            assertTouchTarget(compose.onNodeWithText(it))
        }
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun boardAnnouncesTheRoundAndOffersWorkingNonTouchAimAndShootActions() {
        click("Continue")
        val board = compose.onNodeWithTag("game-board")
        board.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Game board")))
        board.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "Round 42. 42 balls. 7 blocks. Ready to aim.",
        ))
        val actions = board.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Aim left", "Aim right", "Shoot"), actions.map { it.label })
        captureScreen("round42")

        val initialAngle = modelValue { it.angle }
        performBoardAction("Aim left")
        assertEquals(initialAngle - 0.09, modelValue { it.angle }, 0.000001)
        assertTrue(modelValue { it.aiming })
        assertEquals(Phase.AIMING, snapshot().phase)

        performBoardAction("Aim right")
        assertEquals(initialAngle, modelValue { it.angle }, 0.000001)
        performBoardAction("Shoot")
        assertEquals(Phase.FIRING, snapshot().phase)
        assertTouchTarget(compose.onNodeWithText("Speed 1×"))
        advance(160L)
        assertTrue(snapshot().shotElapsed > 0.0)
        board.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "Round 42. 42 balls. 7 blocks. Balls moving.",
        ))
    }

    @Test
    fun productionBlockAndControlTextColorsMeetNormalTextContrast() {
        // Test the actual production palette, including both sides of every strength boundary.
        val strengths = listOf(
            1 to Coral, 9 to Coral, 10 to Orange, 19 to Orange,
            20 to Yellow, 29 to Yellow, 30 to Green, 39 to Green,
            40 to Teal, 59 to Teal, 60 to Violet, 99 to Violet,
            100 to DeepViolet, 999 to DeepViolet, Int.MAX_VALUE to DeepViolet,
        )
        strengths.forEach { (hits, expected) ->
            val background = blockColor(hits)
            assertEquals("Block palette boundary at $hits hits", expected, background)
            val foreground = if (hits >= 100) Color.White else Ink
            assertContrast("Block with $hits hits", foreground, background)
        }
        listOf(
            Triple("Body text", Ink, Field),
            Triple("Dark header and primary action text", Color.White, Ink),
            Triple("Play and play-again buttons", Ink, Coral),
            Triple("Continue, resume and enabled-setting text", Ink, Teal),
            Triple("Speed control and paused round", Teal, Ink),
            Triple("Results round", Coral, Ink),
            Triple("Disabled-setting text", Ink, Color(0xFFDDE1E3)),
        ).forEach { (label, foreground, background) ->
            assertContrast(label, foreground, background)
        }
    }

    private fun assertContrast(label: String, foreground: Color, background: Color) {
        val ratio = ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
        assertTrue("$label contrast was $ratio:1; expected at least 4.5:1", ratio >= 4.5)
    }

    private fun performBoardAction(label: String) {
        // CustomActions is a list, not the AccessibilityAction<T> key accepted by
        // performSemanticsAction in Compose 1.7. Read the real node and invoke on the UI thread.
        val action = compose.onNodeWithTag("game-board").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { assertTrue("Accessibility action failed: $label", action.action()) }
        frame()
    }
}