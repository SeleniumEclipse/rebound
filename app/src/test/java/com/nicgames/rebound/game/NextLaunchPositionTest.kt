package com.nicgames.rebound.game

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class NextLaunchPositionTest {
    private val volleyOrigin = 180.0
    private val firstLandingX = 70.0
    private val airborneX = 280.0

    private fun approachingFloor(x: Double, distance: Double): Ball =
        Ball(x, Board.FLOOR - Board.BALL_RADIUS - distance, 0.0, 430.0)

    private fun airborneBall(): Ball =
        Ball(airborneX, Board.LAUNCH_Y - 100.0, 0.0, -430.0)

    private fun queuedVolleyBeforeFirstLanding(): GameEngine = restored(emptySnapshot().copy(
        // Both airborne balls plus both queued balls are accounted for: none has returned.
        // Using fixture(balls = ..., ballCount = 4) would incorrectly prefill firstReturnX.
        phase = Phase.FIRING,
        ballCount = 4,
        launchX = volleyOrigin,
        balls = listOf(approachingFloor(firstLandingX, 1.0), airborneBall()),
        pendingLaunches = 2,
        launchCountdown = 2 * STEP,
        shotElapsed = 0.09 - 2 * STEP,
        shotAngle = -PI / 2,
        firstReturnX = null,
    ))

    @Test fun markerMatchesLaunchOriginUntilAnyBallReturns() {
        val fresh = GameEngine(123L)
        assertEquals(Phase.AIMING, fresh.phase)
        assertNull(fresh.snapshot().firstReturnX)
        assertEquals(fresh.launchX, fresh.nextLaunchX, 0.0)

        val engine = fixture(ballCount = 3, launchX = 127.0)
        assertEquals(127.0, engine.nextLaunchX, 0.0)
        assertTrue(engine.fire(-PI / 2))
        assertEquals(127.0, engine.nextLaunchX, 0.0)
        steps(engine, 1)

        assertEquals(Phase.FIRING, engine.phase)
        assertNull(engine.snapshot().firstReturnX)
        assertEquals(127.0, engine.launchX, 0.0)
        assertEquals(engine.launchX, engine.nextLaunchX, 0.0)
        assertEquals(2, engine.snapshot().pendingLaunches)
    }

    @Test fun firstLandingMovesMarkerImmediatelyWhileAnotherBallIsAirborne() {
        val engine = fixture(
            balls = listOf(approachingFloor(firstLandingX, 1.0), airborneBall()),
            launchX = volleyOrigin,
        )
        assertNull(engine.snapshot().firstReturnX)
        assertEquals(volleyOrigin, engine.nextLaunchX, 0.0)

        steps(engine, 1)

        assertEquals(Phase.FIRING, engine.phase)
        assertEquals(airborneX, engine.balls.single().x, 0.0)
        assertTrue(engine.balls.single().y < Board.FLOOR - Board.BALL_RADIUS)
        assertEquals(firstLandingX, requireNotNull(engine.snapshot().firstReturnX), 0.0)
        assertEquals(firstLandingX, engine.nextLaunchX, 0.0)
        assertEquals(volleyOrigin, engine.launchX, 0.0)
    }

    @Test fun pendingBallsStillLaunchFromOldOriginAfterMarkerMoves() {
        val engine = queuedVolleyBeforeFirstLanding()
        assertNull(engine.snapshot().firstReturnX)
        steps(engine, 1)
        assertEquals(firstLandingX, engine.nextLaunchX, 0.0)
        assertEquals(2, engine.snapshot().pendingLaunches)
        assertEquals(1, engine.balls.size)

        // The next release is exactly at the end of this fixed step.
        steps(engine, 1)
        assertEquals(Phase.FIRING, engine.phase)
        assertEquals(1, engine.snapshot().pendingLaunches)
        assertEquals(2, engine.balls.size)
        assertEquals(volleyOrigin, engine.balls.last().x, 1e-10)
        assertEquals(Board.LAUNCH_Y, engine.balls.last().y, 1e-10)
        assertEquals(firstLandingX, engine.nextLaunchX, 0.0)
        assertEquals(volleyOrigin, engine.launchX, 0.0)

        steps(engine, 6)
        assertEquals(Phase.FIRING, engine.phase)
        assertEquals(0, engine.snapshot().pendingLaunches)
        assertEquals(3, engine.balls.size)
        assertEquals(airborneX, engine.balls.first().x, 0.0)
        for (released in engine.balls.drop(1)) {
            assertEquals(volleyOrigin, released.x, 1e-10)
            assertTrue(released.vy < 0.0)
        }
        assertEquals(firstLandingX, engine.nextLaunchX, 0.0)
        assertEquals(volleyOrigin, engine.launchX, 0.0)
    }

    @Test fun laterLandingsDoNotReplaceFirstMarkerWhileVolleyContinues() {
        val engine = fixture(
            balls = listOf(
                approachingFloor(firstLandingX, 1.0),
                approachingFloor(220.0, 5.0),
                approachingFloor(310.0, 9.0),
                airborneBall(),
            ),
            launchX = volleyOrigin,
        )
        assertNull(engine.snapshot().firstReturnX)

        // These distances produce one landing per fixed step, leaving one ball airborne.
        for (remainingBalls in 3 downTo 1) {
            steps(engine, 1)
            assertEquals(Phase.FIRING, engine.phase)
            assertEquals(remainingBalls, engine.balls.size)
            assertEquals(firstLandingX, requireNotNull(engine.snapshot().firstReturnX), 0.0)
            assertEquals(firstLandingX, engine.nextLaunchX, 0.0)
            assertEquals(volleyOrigin, engine.launchX, 0.0)
        }
        assertEquals(airborneX, engine.balls.single().x, 0.0)
    }

    @Test fun jsonRestoreAfterFirstLandingPreservesMarkerQueueAndContinuation() {
        val original = queuedVolleyBeforeFirstLanding()
        steps(original, 1)
        original.tick(STEP / 2)
        val saved = original.snapshot()
        assertEquals(Phase.FIRING, saved.phase)
        assertEquals(firstLandingX, requireNotNull(saved.firstReturnX), 0.0)
        assertEquals(2, saved.pendingLaunches)
        assertEquals(STEP, saved.launchCountdown, 1e-10)
        assertEquals(STEP / 2, saved.accumulator, 1e-10)

        val json = Json.encodeToString(saved)
        val decoded = Json.decodeFromString<GameSnapshot>(json)
        assertEquals(saved, decoded)
        val resumed = restored(decoded)
        assertSameState(original, resumed)
        assertEquals(firstLandingX, resumed.nextLaunchX, 0.0)
        assertEquals(volleyOrigin, resumed.launchX, 0.0)

        original.tick(STEP / 2)
        resumed.tick(STEP / 2)
        assertSameState(original, resumed)
        assertEquals(1, resumed.snapshot().pendingLaunches)
        assertEquals(2, resumed.balls.size)
        assertEquals(volleyOrigin, resumed.balls.last().x, 1e-10)
        assertEquals(Board.LAUNCH_Y, resumed.balls.last().y, 1e-10)
        repeat(6) {
            steps(original, 1)
            steps(resumed, 1)
            assertSameState(original, resumed)
            assertEquals(Phase.FIRING, resumed.phase)
            assertEquals(firstLandingX, resumed.nextLaunchX, 0.0)
            assertEquals(volleyOrigin, resumed.launchX, 0.0)
        }
        assertEquals(0, resumed.snapshot().pendingLaunches)
        assertEquals(3, resumed.balls.size)
        for (released in resumed.balls.drop(1)) {
            assertEquals(volleyOrigin, released.x, 1e-10)
        }

        finishTurn(original)
        finishTurn(resumed)
        assertSameState(original, resumed)
        assertEquals(Phase.AIMING, resumed.phase)
        assertEquals(firstLandingX, resumed.launchX, 0.0)
        assertEquals(firstLandingX, resumed.nextLaunchX, 0.0)
    }

    @Test fun finishingVolleyCommitsEarlyMarkerOnlyAfterLastBallReturns() {
        val engine = fixture(
            balls = listOf(
                approachingFloor(firstLandingX, 1.0),
                approachingFloor(airborneX, 5.0),
            ),
            launchX = volleyOrigin,
        )
        steps(engine, 1)
        assertEquals(Phase.FIRING, engine.phase)
        assertEquals(airborneX, engine.balls.single().x, 0.0)
        assertEquals(firstLandingX, engine.nextLaunchX, 0.0)
        assertEquals(volleyOrigin, engine.launchX, 0.0)

        steps(engine, 1)
        assertEquals(Phase.ADVANCING, engine.phase)
        assertTrue(engine.balls.isEmpty())
        assertEquals(0, engine.snapshot().pendingLaunches)
        assertNull(engine.snapshot().firstReturnX)
        assertEquals(firstLandingX, engine.launchX, 0.0)
        assertEquals(firstLandingX, engine.nextLaunchX, 0.0)

        finishTurn(engine)
        assertEquals(Phase.AIMING, engine.phase)
        assertEquals(firstLandingX, engine.nextLaunchX, 0.0)
        assertTrue(engine.fire(-PI / 2))
        assertEquals(firstLandingX, engine.balls.single().x, 0.0)
        assertEquals(Board.LAUNCH_Y, engine.balls.single().y, 0.0)
    }

    @Test fun chronologicallyFirstLandingWinsRegardlessOfBallArrayOrder() {
        for (reverseBalls in listOf(false, true)) {
            // Both land within one step. The earlier landing is second in the initial
            // array and farther right, so neither array order nor lowest x may win.
            val balls = listOf(
                approachingFloor(70.0, 3.0),
                approachingFloor(310.0, 1.0),
                airborneBall(),
            )
            val engine = fixture(
                balls = if (reverseBalls) balls.reversed() else balls,
                launchX = volleyOrigin,
            )
            assertNull(engine.snapshot().firstReturnX)

            steps(engine, 1)

            assertEquals(Phase.FIRING, engine.phase)
            assertEquals(airborneX, engine.balls.single().x, 0.0)
            assertEquals(310.0, requireNotNull(engine.snapshot().firstReturnX), 0.0)
            assertEquals(310.0, engine.nextLaunchX, 0.0)
            assertEquals(volleyOrigin, engine.launchX, 0.0)
        }
    }
}