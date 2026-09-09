package com.nicgames.rebound.game

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class GameSnapshotTest {
    private fun jsonRestore(engine: GameEngine): GameEngine {
        val encoded = Json.encodeToString(engine.snapshot())
        return restored(Json.decodeFromString<GameSnapshot>(encoded))
    }

    @Test fun aimingJsonRoundTripIsExactIncludingRandomGeneratorState() {
        val original = GameEngine(Long.MIN_VALUE)
        val resumed = jsonRestore(original)
        assertEquals(original.snapshot(), resumed.snapshot())
        original.fire(-1.0)
        resumed.fire(-1.0)
        original.recall()
        resumed.recall()
        steps(original, 27)
        steps(resumed, 27)
        assertEquals(original.snapshot(), resumed.snapshot())
    }

    @Test fun midFlightJsonPreservesQueueRemainderAndEverySubsequentStep() {
        val original = fixture(ballCount = 30)
        original.fire(-1.25)
        original.tick(0.123)
        assertTrue(original.snapshot().accumulator > 0.0)
        assertTrue(original.snapshot().pendingLaunches > 20)
        val resumed = jsonRestore(original)
        assertEquals(original.snapshot(), resumed.snapshot())
        repeat(240) {
            original.tick(0.017)
            resumed.tick(0.017)
            assertEquals(original.snapshot(), resumed.snapshot())
        }
        finishTurn(original)
        finishTurn(resumed)
        assertEquals(original.snapshot(), resumed.snapshot())
    }

    @Test fun maximumVolleyJsonRestoreAfterTwentyFourSecondsStillLaunchesEveryBall() {
        val original = fixture(blocks = listOf(Block(1, 3, 8, 999)), ballCount = 999)
        original.fire(-PI / 2)
        repeat(100) { original.tick(0.25) }
        original.tick(0.003)
        assertEquals(Phase.FIRING, original.phase)
        assertTrue(original.shotElapsed > 24.0)
        assertTrue(original.snapshot().pendingLaunches > 0)
        assertTrue(original.snapshot().accumulator > 0.0)
        assertTrue(original.blocks.single().hits in 1..998)
        val resumed = jsonRestore(original)
        assertSameState(original, resumed)
        var frames = 0
        while (original.phase == Phase.FIRING || original.phase == Phase.ADVANCING) {
            assertTrue(frames++ < turnFrameLimit(999))
            original.tick(0.25)
            resumed.tick(0.25)
            assertSameState(original, resumed)
        }
        assertEquals(Phase.AIMING, resumed.phase)
        assertEquals(999L, resumed.totalHits)
        assertEquals(1, resumed.destroyedBlocks)
        assertTrue(resumed.blocks.none { it.id == 1L })
        assertEquals(999, resumed.ballCount)
        assertTrue(resumed.shotElapsed in 44.91..45.1)
    }

    @Test fun sharedBlockChronologySurvivesJsonBeforeAndAfterTheHitInEitherArrayOrder() {
        for (reverseBalls in listOf(false, true)) {
            val original = restored(sharedBlockRaceSnapshot(reverseBalls))
            original.tick(STEP / 2)
            var resumed = jsonRestore(original)
            original.tick(STEP / 2)
            resumed.tick(STEP / 2)
            assertSameState(original, resumed)
            assertEquals(430.0, resumed.balls.single { it.x == 190.0 }.vy, 1e-10)
            assertEquals(-430.0, resumed.balls.single { it.x == 170.0 }.vy, 0.0)
            resumed = jsonRestore(resumed)
            repeat(240) {
                original.tick(STEP)
                resumed.tick(STEP)
                assertSameState(original, resumed)
            }
            finishTurn(original)
            finishTurn(resumed)
            assertSameState(original, resumed)
            assertEquals(190.0, resumed.launchX, 0.0)
        }
    }

    @Test fun collectedPickupsAndFirstReturnSurviveMidVolleySave() {
        val original = restored(emptySnapshot().copy(
            phase = Phase.FIRING, ballCount = 40,
            balls = listOf(Ball(179.0, 400.0, 0.0, -430.0)),
            pickups = listOf(Pickup(1, 3, 7)),
            pendingLaunches = 38, launchCountdown = 0.041,
            shotElapsed = 0.2, firstReturnX = 70.0,
        ))
        original.tick(0.053)
        assertEquals(1, original.snapshot().collectedBalls)
        assertTrue(original.pickups.isEmpty())
        assertEquals(70.0, requireNotNull(original.snapshot().firstReturnX), 0.0)
        val resumed = jsonRestore(original)
        original.recall()
        resumed.recall()
        assertSameState(original, resumed)
        assertEquals(41, resumed.ballCount)
        assertEquals(70.0, resumed.launchX, 0.0)
    }

    @Test fun partiallyAdvancedJsonCommitsRowsExactlyOnce() {
        val original = fixture(blocks = listOf(Block(1, 3, 7, 5)))
        original.fire(-1.0)
        original.recall()
        original.tick(0.103)
        assertEquals(Phase.ADVANCING, original.phase)
        assertEquals(7, original.blocks.single().row)
        assertTrue(original.advanceProgress > 0.4)
        val resumed = jsonRestore(original)
        repeat(30) {
            original.tick(STEP)
            resumed.tick(STEP)
            assertEquals(original.snapshot(), resumed.snapshot())
        }
        assertEquals(8, resumed.blocks.single { it.id == 1L }.row)
        assertEquals(2, resumed.round)
        assertTrue(resumed.blocks.count { it.row == 0 } in 2..4)
    }

    @Test fun saveImmediatelyBeforeLossDoesNotLoseTwiceOnRestore() {
        val original = fixture(blocks = listOf(Block(1, 3, 8, 5)))
        original.fire(-1.0)
        original.recall()
        steps(original, 26)
        val resumed = jsonRestore(original)
        steps(original, 1)
        steps(resumed, 1)
        assertSameState(original, resumed)
        assertEquals(Phase.GAME_OVER, resumed.phase)
        assertEquals(9, resumed.blocks.single().row)
        val terminal = jsonRestore(resumed)
        steps(terminal, 120)
        assertSameState(resumed, terminal)
    }

    @Test fun snapshotCopiesAllMutableEntitiesAndListContainers() {
        val engine = fixture(
            blocks = listOf(Block(1, 3, 6, 10)),
            pickups = listOf(Pickup(2, 2, 4)),
            balls = listOf(Ball(180.0, 360.0, 0.0, -430.0)),
        )
        steps(engine, 4)
        val expected = engine.snapshot()
        val detached = engine.snapshot()
        assertNotSame(engine.blocks, detached.blocks)
        assertNotSame(engine.blocks[0], detached.blocks[0])
        assertNotSame(engine.pickups[0], detached.pickups[0])
        assertNotSame(engine.balls[0], detached.balls[0])
        assertNotSame(engine.impacts[0], detached.impacts[0])
        detached.blocks[0].hits = 12345
        detached.blocks[0].row = 0
        detached.pickups[0].row = 0
        detached.balls[0].x = 33.0
        detached.impacts[0].age = 0.17
        assertEquals(expected, engine.snapshot())
    }

    @Test fun restoreCopiesItsInputRatherThanRetainingMutableSavedEntities() {
        val original = fixture(
            blocks = listOf(Block(1, 3, 6, 10)),
            pickups = listOf(Pickup(2, 2, 4)),
            balls = listOf(Ball(180.0, 360.0, 0.0, -430.0)),
        )
        steps(original, 4)
        val input = original.snapshot()
        val resumed = restored(input)
        val expected = resumed.snapshot()
        input.blocks[0].hits = 1
        input.pickups[0].row = 8
        input.balls[0].vy = 17.0
        input.impacts[0].age = 0.17
        assertEquals(expected, resumed.snapshot())
        assertNotSame(input.balls[0], resumed.balls[0])
    }

    @Test fun invalidScalarSnapshotsAreRejected() {
        val base = emptySnapshot()
        val invalid = listOf(
            "unknown version" to base.copy(version = 2),
            "zero round" to base.copy(round = 0),
            "zero balls" to base.copy(ballCount = 0),
            "too many balls" to base.copy(ballCount = 1000),
            "nonfinite launch" to base.copy(launchX = Double.NaN),
            "launch outside wall" to base.copy(launchX = 345.0),
            "nonfinite elapsed" to base.copy(shotElapsed = Double.POSITIVE_INFINITY),
            "negative elapsed" to base.copy(shotElapsed = -0.1),
            "elapsed beyond deadline" to base.copy(shotElapsed = 24.1),
            "negative remainder" to base.copy(accumulator = -0.01),
            "full unprocessed step" to base.copy(accumulator = STEP),
            "nonfinite remainder" to base.copy(accumulator = Double.NaN),
            "nonfinite progress" to base.copy(advanceProgress = Double.NaN),
            "negative progress" to base.copy(advanceProgress = -0.1),
            "progress while aiming" to base.copy(advanceProgress = 0.5),
            "uncommitted complete advance" to base.copy(phase = Phase.ADVANCING, advanceProgress = 1.0),
            "negative hits" to base.copy(totalHits = -1),
            "negative destruction count" to base.copy(destroyedBlocks = -1),
            "destruction without hits" to base.copy(destroyedBlocks = 1),
            "invalid future ID" to base.copy(nextId = 0),
            "overflowing future IDs" to base.copy(nextId = Long.MAX_VALUE),
            "nonfinite launch angle" to base.copy(shotAngle = Double.NaN),
            "downward launch angle" to base.copy(shotAngle = PI / 2),
            "unearned pickup while aiming" to base.copy(collectedBalls = 1),
            "spurious return while aiming" to base.copy(firstReturnX = 100.0),
            "spurious game over" to base.copy(phase = Phase.GAME_OVER),
        )
        for ((label, snapshot) in invalid) assertNull(label, GameEngine.restore(snapshot))
    }

    @Test fun invalidBoardGeometryIdsAndRowOccupancyAreRejected() {
        val base = emptySnapshot()
        val invalid = listOf(
            "nonpositive ID" to base.copy(blocks = listOf(Block(0, 0, 0, 1))),
            "future ID reused" to base.copy(blocks = listOf(Block(1000, 0, 0, 1))),
            "duplicate ID" to base.copy(blocks = listOf(Block(1, 0, 0, 1), Block(1, 2, 0, 1))),
            "duplicate cell" to base.copy(blocks = listOf(Block(1, 0, 0, 1), Block(2, 0, 0, 1))),
            "outside column" to base.copy(blocks = listOf(Block(1, 7, 0, 1))),
            "negative row" to base.copy(blocks = listOf(Block(1, 0, -1, 1))),
            "bottom block in active game" to base.copy(blocks = listOf(Block(1, 0, 9, 1))),
            "dead block" to base.copy(blocks = listOf(Block(1, 0, 0, 0))),
            "negative block strength" to base.copy(blocks = listOf(Block(1, 0, 0, -1))),
            "more than four blocks in row" to base.copy(blocks = List(5) { Block(it + 1L, it, 0, 1) }),
            "oversized board" to base.copy(blocks = List(71) { Block(it + 1L, 0, 0, 1) }),
            "pickup in block cell" to base.copy(blocks = listOf(Block(1, 0, 0, 1)), pickups = listOf(Pickup(2, 0, 0))),
            "pickup with shared ID" to base.copy(blocks = listOf(Block(1, 0, 0, 1)), pickups = listOf(Pickup(1, 1, 0))),
            "multiple pickups in row" to base.copy(pickups = listOf(Pickup(1, 0, 0), Pickup(2, 1, 0))),
            "pickup below board" to base.copy(pickups = listOf(Pickup(1, 0, 10))),
            "pickup outside board" to base.copy(pickups = listOf(Pickup(1, -1, 0))),
        )
        for ((label, snapshot) in invalid) assertNull(label, GameEngine.restore(snapshot))
    }

    @Test fun invalidBallPositionsVelocitiesAndLaunchQueuesAreRejected() {
        val base = fixture(balls = listOf(Ball(180.0, 400.0, 0.0, -430.0))).snapshot()
        val invalid = listOf(
            "airborne ball while aiming" to base.copy(phase = Phase.AIMING),
            "empty completed volley still firing" to base.copy(balls = emptyList()),
            "too many active balls" to base.copy(balls = List(2) { base.balls.single().copy() }),
            "negative queue" to base.copy(pendingLaunches = -1),
            "queue plus air exceeds ball count" to base.copy(pendingLaunches = 1),
            "nonfinite countdown" to base.copy(launchCountdown = Double.NaN),
            "spurious countdown" to base.copy(launchCountdown = 0.01),
            "empty countdown with pending release" to base.copy(ballCount = 2, pendingLaunches = 1),
            "excessive countdown" to base.copy(ballCount = 2, pendingLaunches = 1, launchCountdown = 0.1),
            "impossible first return" to base.copy(firstReturnX = 42.0),
            "missing first return" to base.copy(ballCount = 2),
            "nonfinite return" to base.copy(firstReturnX = Double.NaN),
            "return outside walls" to base.copy(firstReturnX = 350.0),
            "already timed out" to base.copy(shotElapsed = 24.0),
            "negative collection" to base.copy(collectedBalls = -1),
            "unbounded collection" to base.copy(collectedBalls = 71),
            "nonfinite position" to base.copy(balls = listOf(Ball(Double.NaN, 400.0, 0.0, -430.0))),
            "left of wall" to base.copy(balls = listOf(Ball(15.0, 400.0, 0.0, -430.0))),
            "above ceiling" to base.copy(balls = listOf(Ball(180.0, 11.0, 0.0, -430.0))),
            "below landing floor" to base.copy(balls = listOf(Ball(180.0, 479.0, 0.0, -430.0))),
            "nonfinite velocity" to base.copy(balls = listOf(Ball(180.0, 400.0, Double.POSITIVE_INFINITY, -430.0))),
            "motionless ball" to base.copy(balls = listOf(Ball(180.0, 400.0, 0.0, 0.0))),
            "unsafe extreme velocity" to base.copy(balls = listOf(Ball(180.0, 400.0, 0.0, -10_001.0))),
            "ball inside solid block" to base.copy(
                blocks = listOf(Block(1, 3, 6, 5)), balls = listOf(Ball(180.0, 330.0, 0.0, -430.0)),
            ),
        )
        for ((label, snapshot) in invalid) assertNull(label, GameEngine.restore(snapshot))
    }

    @Test fun snapshotElapsedLimitIncludesTheEntireLaunchQueueButStillRejectsTimeouts() {
        for (count in listOf(1, 2, 250, 999)) {
            val deadline = volleyDeadlineForTest(count)
            val firing = fixture(
                balls = listOf(Ball(180.0, 250.0, 430.0, 0.0)), ballCount = count,
            ).snapshot().copy(shotElapsed = deadline - STEP)
            assertNotNull(GameEngine.restore(firing))
            assertNull(GameEngine.restore(firing.copy(shotElapsed = deadline)))
            assertNull(GameEngine.restore(firing.copy(shotElapsed = deadline + 0.001)))
            val completed = firing.copy(
                phase = Phase.ADVANCING, balls = emptyList(), firstReturnX = null,
                shotElapsed = deadline,
            )
            assertNotNull(GameEngine.restore(completed))
            assertNull(GameEngine.restore(completed.copy(shotElapsed = deadline + 0.001)))
        }
        assertEquals(68.91, volleyDeadlineForTest(999), 1e-10)
    }

    @Test fun invalidOrUnboundedVisualEffectsAreRejected() {
        val base = emptySnapshot()
        val invalid = listOf(
            Impact(Double.NaN, 100.0, 1),
            Impact(100.0, Double.POSITIVE_INFINITY, 1),
            Impact(100.0, 100.0, 1, Double.NaN),
            Impact(100.0, 100.0, 1, -0.1),
            Impact(100.0, 100.0, 1, 0.18),
            Impact(100.0, 100.0, 0),
            Impact(500.0, 100.0, 1),
        )
        for (impact in invalid) assertNull(GameEngine.restore(base.copy(impacts = listOf(impact))))
        assertNull(GameEngine.restore(base.copy(impacts = List(65) { Impact(100.0, 100.0, 1) })))
    }

    @Test fun originalAndRestoredGamesContinueIdenticallyThroughSeveralFutureRounds() {
        val original = restored(GameEngine(897).snapshot().copy(ballCount = 12))
        original.fire(-1.7)
        original.tick(0.237)
        val resumed = jsonRestore(original)
        finishTurn(original)
        finishTurn(resumed)
        repeat(8) { index ->
            assertSameState(original, resumed)
            if (original.phase == Phase.AIMING) {
                val angle = -0.3 - index * 0.3
                original.fire(angle)
                resumed.fire(angle)
                repeat(7) {
                    original.tick(0.25)
                    resumed.tick(0.25)
                    assertSameState(original, resumed)
                }
                finishTurn(original)
                finishTurn(resumed)
            }
        }
        assertSameState(original, resumed)
    }
}