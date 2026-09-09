package com.nicgames.rebound

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.nicgames.rebound.game.Board
import com.nicgames.rebound.game.Phase
import org.junit.Assert.*
import org.junit.Test

class ReboundBoardEdgesTest : ReboundUiTest() {
    @Test
    @LaunchWith(ReboundFixture.LEGACY_CEILING)
    fun oldCeilingLaneSaveReturnsTrappedBallAndKeepsTheRun() {
        val migrated = snapshot()
        assertEquals(3, migrated.version)
        assertEquals(42, migrated.round)
        assertEquals(3, migrated.ballCount)
        assertEquals(41.0, requireNotNull(migrated.firstReturnX), 0.0)
        assertEquals(1, migrated.balls.size)
        assertEquals(0L, migrated.totalHits)
        assertEquals(9, migrated.blocks.single().hits)
        recreateWithFreshModel()
        assertEquals(migrated, snapshot())
        click("Continue")
        advance(64)
        assertTrue(snapshot().balls.all { it.y >= Board.TOP + Board.BALL_RADIUS })
        assertEquals(3, snapshot().ballCount)
        assertEquals(0, snapshot().pendingLaunches)
    }

    @Test
    @LaunchWith(ReboundFixture.ROUND_42)
    fun visibleWallsAndFloorMeetTheOuterSpawnColumnsWithoutLanes() {
        click("Continue")
        val pixels = compose.onNodeWithTag("game-board").captureToImage().toPixelMap()
        fun red(x: Double, y: Double): Float {
            val at = boardPoint(x, y)
            return pixels[at.x.toInt(), at.y.toInt()].red
        }
        // Below all fixture blocks so these samples isolate the wall itself.
        assertTrue(red(20.0, 300.0) < .7f)
        assertTrue(red(338.0, 300.0) < .7f)
        assertTrue("No old left wall farther outside the tile area", red(12.0, 300.0) > .8f)
        assertTrue("No old right wall farther outside the tile area", red(348.0, 300.0) > .8f)
        assertTrue("Ceiling is flush with the top row", red(180.0, 20.0) < .7f)
        assertTrue("No old ceiling above the spawning area", red(180.0, 8.0) > .8f)
        assertTrue(red(21.0, Board.FLOOR) < .7f)
        assertTrue(red(337.0, Board.FLOOR) < .7f)
        assertTrue(red(14.0, Board.FLOOR) > .8f)
        assertEquals(Board.LEFT, snapshot().blocks.first { it.column == 0 }.x, 0.0)
        assertEquals(Board.RIGHT, snapshot().blocks.first { it.column == 6 }.x + Board.BLOCK_SIZE, 0.0)
        captureScreen("tight-board")
    }

    @Test
    @LaunchWith(ReboundFixture.LEGACY_EDGE)
    fun oldMidVolleySaveMigratesAtAppStartupWithoutLosingTheRun() {
        val migrated = snapshot()
        assertEquals(3, migrated.version)
        assertEquals(42, migrated.round)
        assertEquals(3, migrated.ballCount)
        assertEquals(24.0, migrated.launchX, 0.0)
        assertEquals(24.0, requireNotNull(migrated.firstReturnX), 0.0)
        assertEquals(1, migrated.balls.size)
        assertEquals(1, migrated.pendingLaunches)
        assertEquals(0L, migrated.totalHits)
        assertEquals(9, migrated.blocks.single().hits)
        recreateWithFreshModel()
        assertEquals(migrated, snapshot())
        click("Continue")
        advance(64)
        assertEquals(Phase.FIRING, snapshot().phase)
        assertEquals(3, snapshot().ballCount)
        assertEquals(0, snapshot().pendingLaunches)
        assertTrue(snapshot().balls.all { it.x in 24.0..334.0 })
        assertEquals(0L, snapshot().totalHits)
        assertEquals(9, snapshot().blocks.single().hits)
    }
}