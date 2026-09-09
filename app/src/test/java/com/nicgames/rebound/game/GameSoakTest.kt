package com.nicgames.rebound.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class GameSoakTest {
    @Test(timeout = 120_000) fun manyAnglesAndRoundsWithTwoHundredFiftyBallsRemainBoundedAndRestorable() {
        var engine = restored(GameEngine(981723).snapshot().copy(ballCount = 250))
        val angles = doubleArrayOf(-PI + 0.12, -2.7, -2.25, -1.85, -PI / 2, -1.25, -0.8, -0.35, -0.12)
        var completedTurns = 0
        var sawManyActiveBalls = false
        repeat(27) { turn ->
            if (engine.phase == Phase.GAME_OVER) {
                engine = restored(GameEngine(981723L + turn).snapshot().copy(ballCount = 250))
            }
            assertTrue(engine.fire(angles[turn % angles.size]))
            val deadline = volleyDeadlineForTest(engine.ballCount)
            val frameLimit = turnFrameLimit(engine.ballCount)
            var frames = 0
            while (engine.phase == Phase.FIRING || engine.phase == Phase.ADVANCING) {
                assertTrue("Turn $turn must complete within its queue-adjusted deadline", frames++ < frameLimit)
                val hitsBefore = engine.totalHits
                engine.tick(0.25)
                assertTrue(engine.totalHits >= hitsBefore)
                assertTrue(engine.ballCount in 250..999)
                assertTrue(engine.impacts.size <= 64)
                assertTrue(engine.advanceProgress >= 0.0 && engine.advanceProgress < 1.0)
                assertTrue(engine.shotElapsed <= deadline)
                if (engine.balls.size >= 200) sawManyActiveBalls = true
                for (ball in engine.balls) {
                    assertTrue(ball.x.isFinite() && ball.y.isFinite())
                    assertTrue(ball.x in 16.0..344.0)
                    assertTrue(ball.y in 12.0..478.0)
                    assertEquals(430.0, hypot(ball.vx, ball.vy), 1e-7)
                    assertTrue(engine.blocks.none { Collision.overlapsBox(ball, it) })
                }
                assertTrue(engine.blocks.all { it.hits > 0 })
                if (frames % 3 == 0) {
                    val saved = engine.snapshot()
                    val resumed = GameEngine.restore(saved)
                    assertNotNull("Every reachable state must be restorable at turn $turn frame $frames", resumed)
                    assertEquals(saved, requireNotNull(resumed).snapshot())
                    // Actually continue from restored state to catch repeated-save drift.
                    engine = resumed
                }
            }
            completedTurns++
            val rowGroups = engine.blocks.groupBy { it.row }
            assertTrue(rowGroups.values.all { it.size <= 4 })
            val cells = engine.blocks.map { it.row to it.column } + engine.pickups.map { it.row to it.column }
            assertEquals(cells.size, cells.toSet().size)
        }
        assertEquals(27, completedTurns)
        assertTrue("At least one shallow volley should have over 200 balls airborne together", sawManyActiveBalls)
    }

    @Test fun deadlineCancelsAHugePendingQueueWithoutManufacturingHits() {
        val deadline = volleyDeadlineForTest(999)
        val engine = restored(emptySnapshot().copy(
            phase = Phase.FIRING, ballCount = 999,
            pendingLaunches = 998, launchCountdown = 0.02, shotElapsed = deadline - 0.01,
            balls = listOf(Ball(180.0, 250.0, 430.0, 0.0)),
            blocks = listOf(Block(1, 3, 0, 100)),
        ))
        steps(engine, 2)
        assertEquals(Phase.ADVANCING, engine.phase)
        assertEquals(deadline, engine.shotElapsed, 0.0)
        assertTrue(engine.balls.isEmpty())
        assertEquals(0, engine.snapshot().pendingLaunches)
        assertEquals(0L, engine.totalHits)
        assertEquals(100, engine.blocks.single().hits)
    }

    @Test fun lastScheduledBallRetainsItsFullTravelBudgetAtMaximumBallCount() {
        val deadline = volleyDeadlineForTest(999)
        val engine = restored(emptySnapshot().copy(
            phase = Phase.FIRING, ballCount = 999, shotElapsed = deadline - 0.02,
            balls = listOf(Ball(180.0, 250.0, 430.0, 0.0)), firstReturnX = 190.0,
        ))
        steps(engine, 2)
        assertEquals(Phase.FIRING, engine.phase)
        steps(engine, 1)
        assertEquals(Phase.ADVANCING, engine.phase)
        assertEquals(68.91, engine.shotElapsed, 1e-10)
        assertEquals(190.0, engine.launchX, 0.0)
        assertTrue(engine.balls.isEmpty())
        assertNotNull(GameEngine.restore(engine.snapshot()))
    }

    @Test fun highSpeedWallBouncesOverThousandsOfStepsStayFiniteAndBounded() {
        val engine = fixture(balls = listOf(Ball(180.0, 250.0, 10_000.0, 0.0)))
        repeat(2800) {
            engine.tick(STEP)
            assertEquals(Phase.FIRING, engine.phase)
            val ball = engine.balls.single()
            assertTrue(ball.x.isFinite() && ball.y.isFinite())
            assertTrue(ball.x in 16.0..344.0)
            assertEquals(10_000.0, hypot(ball.vx, ball.vy), 1e-7)
        }
        assertNotNull(GameEngine.restore(engine.snapshot()))
        finishTurn(engine)
        assertEquals(Phase.AIMING, engine.phase)
    }
}