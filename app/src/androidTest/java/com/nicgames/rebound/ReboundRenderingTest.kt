package com.nicgames.rebound

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ReboundRenderingTest : ReboundUiTest() {
    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun pullBackGuideDrawsOnlyTheNearestThirdOfTheBoard() {
        click("Continue")
        val board = compose.onNodeWithTag("game-board")
        val before = board.captureToImage().toPixelMap()
        val touch = boardPoint(180.0, 220.0)
        val pulled = boardPoint(180.0, 380.0)
        board.performTouchInput { down(touch); moveTo(pulled, 160) }
        frame()
        val after = board.captureToImage().toPixelMap()
        val cutoff = boardPoint(180.0, 304.0).y.toInt()
        var above = 0
        var below = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (abs(before[x, y].red - after[x, y].red) > .2f) {
                if (y < cutoff) above++ else below++
            }
        }
        org.junit.Assert.assertEquals("No guide or hit marker near distant blocks", 0, above)
        assertTrue("Short guide must actually be visible", below > 50)
        captureScreen("aim-short")
        board.performTouchInput { cancel() }
    }

    @Test
    @LaunchWith(ReboundFixture.MID_FLIGHT)
    fun airborneBallsActuallyRepaintBetweenSimulationFrames() {
        click("Continue")
        val board = compose.onNodeWithTag("game-board")
        val before = board.captureToImage().toPixelMap()
        val prior = snapshot()
        advance(160L)
        val after = board.captureToImage().toPixelMap()
        assertTrue(snapshot().shotElapsed > prior.shotElapsed)
        // Check the lower half: no blocks move here, only the launched balls.
        var changed = 0
        for (y in before.height / 2 until before.height) {
            for (x in 0 until before.width) {
                if (abs(before[x, y].red - after[x, y].red) > .2f) changed++
            }
        }
        assertTrue("Stored positions changed but the board did not repaint: $changed pixels", changed > 50)
    }
}