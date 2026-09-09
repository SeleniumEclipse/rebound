package com.nicgames.rebound

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ReboundRenderingTest : ReboundUiTest() {
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