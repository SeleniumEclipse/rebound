package com.nicgames.rebound.game

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class LegacySnapshotMigrationTest {
    private fun oldEdge(right: Boolean): Double = if (right) 344.0 else 16.0
    private fun newEdge(right: Boolean): Double =
        if (right) Board.RIGHT - Board.BALL_RADIUS else Board.LEFT + Board.BALL_RADIUS

    private fun edgeBlock(right: Boolean): Block = Block(1, if (right) Board.COLUMNS - 1 else 0, 6, 10)

    private fun legacyVolley(
        balls: List<Ball>, blocks: List<Block> = emptyList(), launchX: Double = 180.0,
        pending: Int = 0, firstReturnX: Double? = null,
    ): GameSnapshot = emptySnapshot().copy(
        version = 1, phase = Phase.FIRING, balls = balls, blocks = blocks, launchX = launchX,
        ballCount = balls.size + pending + (if (firstReturnX == null) 0 else 1),
        pendingLaunches = pending, launchCountdown = if (pending > 0) STEP else 0.0,
        firstReturnX = firstReturnX, shotElapsed = 0.11,
    )

    private fun detached(s: GameSnapshot): GameSnapshot = s.copy(
        blocks = s.blocks.map { it.copy() }, pickups = s.pickups.map { it.copy() },
        balls = s.balls.map { it.copy() }, impacts = s.impacts.map { it.copy() },
    )

    @Test fun newSnapshotsDefaultToVersionThreeAndAlwaysWriteItToJson() {
        val engine = fixture(ballCount = 10)
        engine.fire(-1.25)
        engine.tick(0.123)
        val saved = engine.snapshot()
        assertEquals(3, saved.version)
        for (json in listOf(Json, Json { encodeDefaults = true }, Json { ignoreUnknownKeys = true })) {
            val text = json.encodeToString(saved)
            assertEquals(JsonPrimitive(3), json.parseToJsonElement(text).jsonObject["version"])
            val decoded = json.decodeFromString<GameSnapshot>(text)
            assertEquals(saved, decoded)
            assertEquals(saved, restored(decoded).snapshot())
        }
    }

    @Test fun missingVersionInOldJsonStillDecodesAsLegacyAndMigratesBothEdges() {
        for (right in listOf(false, true)) {
            val legacy = legacyVolley(
                balls = listOf(Ball(oldEdge(right), 250.0, if (right) 100.0 else -100.0, -400.0)),
                launchX = oldEdge(right), pending = 2, firstReturnX = oldEdge(!right),
            )
            val fields = Json.parseToJsonElement(Json.encodeToString(legacy)).jsonObject
            for (explicitVersion in listOf(false, true)) {
                val objectFields = fields.toMutableMap()
                if (explicitVersion) objectFields["version"] = JsonPrimitive(1) else objectFields.remove("version")
                val decoded = Json.decodeFromString<GameSnapshot>(JsonObject(objectFields).toString())
                assertEquals(legacy, decoded)
                assertEquals(1, decoded.version)
                val migrated = restored(decoded).snapshot()
                assertEquals(3, migrated.version)
                assertEquals(newEdge(right), migrated.launchX, 0.0)
                assertEquals(newEdge(!right), requireNotNull(migrated.firstReturnX), 0.0)
                assertEquals(newEdge(right), migrated.balls.single().x, 0.0)
                assertEquals(2, migrated.pendingLaunches)
                assertEquals(migrated, restored(migrated).snapshot())
            }
        }
    }

    @Test fun legacyLaunchMigrationPreservesAimingAdvancingAndGameOverState() {
        for (right in listOf(false, true)) {
            for (phase in listOf(Phase.AIMING, Phase.ADVANCING, Phase.GAME_OVER)) {
                val legacy = emptySnapshot(Long.MIN_VALUE).copy(
                    version = 1, phase = phase, launchX = oldEdge(right), round = 9, ballCount = 12,
                    advanceProgress = if (phase == Phase.ADVANCING) 0.45 else 0.0,
                    blocks = listOf(edgeBlock(right).copy(row = if (phase == Phase.GAME_OVER) 9 else 4)),
                    pickups = listOf(Pickup(2, 3, 5)), accumulator = STEP / 3,
                    totalHits = 27, destroyedBlocks = 4,
                )
                val engine = restored(legacy)
                assertEquals(legacy.copy(version = 3, launchX = newEdge(right)), engine.snapshot())
                assertEquals(newEdge(right), engine.nextLaunchX, 0.0)
                assertEquals(engine.snapshot(), restored(engine.snapshot()).snapshot())
            }
        }
    }

    @Test fun inBoundsLegacyMidVolleyPreservesEveryFieldPhysicsStepAndFutureRandomRow() {
        val original = fixture(
            blocks = listOf(Block(1, 3, 6, 50)), pickups = listOf(Pickup(2, 2, 4)), ballCount = 30,
        )
        original.fire(-PI / 2)
        original.tick(0.25)
        original.tick(0.153)
        val current = original.snapshot()
        assertEquals(Phase.FIRING, current.phase)
        assertTrue(current.totalHits > 0)
        assertTrue(current.pendingLaunches > 0)
        assertTrue(current.accumulator > 0.0)
        val legacy = current.copy(version = 1)
        val input = detached(legacy)
        val migrated = restored(legacy)
        assertEquals(current, migrated.snapshot())
        assertEquals(input, legacy)
        repeat(240) {
            original.tick(0.017)
            migrated.tick(0.017)
            assertEquals(original.snapshot(), migrated.snapshot())
        }
        finishTurn(original)
        finishTurn(migrated)
        assertEquals(original.snapshot(), migrated.snapshot())
        assertTrue(original.round > current.round)
    }

    @Test fun legacyFirstReturnIsClampedSeparatelyAndQueuedBallsUseTheClampedOriginalLaunch() {
        for (right in listOf(false, true)) {
            val legacy = legacyVolley(
                balls = listOf(Ball(180.0, 250.0, 0.0, -430.0)), launchX = oldEdge(right),
                pending = 2, firstReturnX = oldEdge(!right),
            )
            val engine = restored(legacy)
            assertEquals(legacy.copy(
                version = 3, launchX = newEdge(right), firstReturnX = newEdge(!right),
            ), engine.snapshot())
            assertEquals(newEdge(!right), engine.nextLaunchX, 0.0)
            steps(engine, 1)
            assertEquals(2, engine.balls.size)
            assertEquals(1, engine.snapshot().pendingLaunches)
            assertEquals(newEdge(right), engine.balls.last().x, 1e-9)
            assertEquals(Board.LAUNCH_Y, engine.balls.last().y, 1e-9)
            assertEquals(newEdge(!right), engine.nextLaunchX, 0.0)
            assertEquals(newEdge(right), engine.launchX, 0.0)
            assertNotNull(GameEngine.restore(engine.snapshot()))
        }
    }

    @Test fun legacyBallsInClearSpaceClampAndReflectOnlyAnOutwardHorizontalVelocity() {
        for (right in listOf(false, true)) {
            val outward = if (right) 1.0 else -1.0
            for (x in listOf(oldEdge(right), newEdge(right) + outward * 0.001)) {
                for (vx in listOf(-100.0, 0.0, 100.0)) {
                    val ball = Ball(x, 250.0, vx, -400.0)
                    val legacy = legacyVolley(listOf(ball), blocks = listOf(Block(1, 3, 6, 10)))
                    val expectedBall = ball.copy(
                        x = newEdge(right), vx = if (vx * outward > 0.0) -vx else vx,
                    )
                    val input = detached(legacy)
                    val engine = restored(legacy)
                    assertEquals(legacy.copy(version = 3, balls = listOf(expectedBall)), engine.snapshot())
                    assertEquals(input, legacy)
                    assertNotSame(ball, engine.balls.single())
                    assertEquals(hypot(vx, -400.0), hypot(engine.balls.single().vx, engine.balls.single().vy), 0.0)
                    steps(engine, 1)
                    assertEquals(0L, engine.totalHits)
                    assertTrue(engine.balls.single().x in newEdge(false)..newEdge(true))
                    assertNotNull(GameEngine.restore(engine.snapshot()))
                }
            }
        }
    }

    @Test fun clampingOntoAnUndersideTangentKeepsTheBallAndWaitsForARealHit() {
        for (right in listOf(false, true)) {
            val block = edgeBlock(right)
            val ball = Ball(oldEdge(right), block.y + Board.BLOCK_SIZE + Board.BALL_RADIUS, 0.0, -430.0)
            val legacy = legacyVolley(listOf(ball), blocks = listOf(block))
            val engine = restored(legacy)
            assertEquals(legacy.copy(version = 3, balls = listOf(ball.copy(x = newEdge(right)))), engine.snapshot())
            assertEquals(0L, engine.totalHits)
            assertNull(engine.snapshot().firstReturnX)
            steps(engine, 1)
            assertEquals(1L, engine.totalHits)
            assertEquals(9, engine.blocks.single().hits)
            assertEquals(430.0, engine.balls.single().vy, 1e-9)
            assertNotNull(GameEngine.restore(engine.snapshot()))
        }
    }

    @Test fun removedLaneBallThatWouldOverlapReturnsWithoutDamageOrLosingBallsAndOthersContinue() {
        for (right in listOf(false, true)) {
            val safe = Ball(180.0, 300.0, 0.0, -430.0)
            val legacy = legacyVolley(
                balls = listOf(Ball(oldEdge(right), 320.0, 0.0, -430.0), safe),
                blocks = listOf(edgeBlock(right)), launchX = oldEdge(right), pending = 2,
            ).copy(
                totalHits = 7, destroyedBlocks = 1, collectedBalls = 1,
                pickups = listOf(Pickup(2, if (right) Board.COLUMNS - 1 else 0, 7)),
                impacts = listOf(Impact(100.0, 100.0, 8, 0.03)),
            )
            val input = detached(legacy)
            val engine = restored(legacy)
            assertEquals(legacy.copy(
                version = 3, launchX = newEdge(right), firstReturnX = newEdge(right), balls = listOf(safe),
            ), engine.snapshot())
            assertEquals(input, legacy)
            assertEquals(4, engine.ballCount)
            assertEquals(Phase.FIRING, engine.phase)
            steps(engine, 1)
            assertTrue(engine.balls.first().y < safe.y)
            assertEquals(newEdge(right), engine.balls.last().x, 1e-9)
            assertEquals(1, engine.snapshot().pendingLaunches)
            assertEquals(7L, engine.totalHits)
            assertEquals(10, engine.blocks.single().hits)
            assertEquals(1, engine.pickups.size)
            assertEquals(1, engine.snapshot().collectedBalls)
            assertEquals(newEdge(right), engine.nextLaunchX, 0.0)
            assertTrue(engine.recall())
            assertEquals(5, engine.ballCount)
            assertFalse(engine.recall())
            assertEquals(5, engine.ballCount)
            assertNotNull(GameEngine.restore(engine.snapshot()))
        }
    }

    @Test fun forcedReturnNeverReplacesAnExistingLandingMarker() {
        for (right in listOf(false, true)) {
            for (marker in listOf(123.0, oldEdge(!right))) {
                val legacy = legacyVolley(
                    balls = listOf(Ball(oldEdge(right), 320.0, 0.0, -430.0)),
                    blocks = listOf(edgeBlock(right)), pending = 1, firstReturnX = marker,
                )
                val engine = restored(legacy)
                assertEquals(marker.coerceIn(newEdge(false), newEdge(true)), engine.nextLaunchX, 0.0)
                assertEquals(legacy.copy(
                    version = 3, balls = emptyList(), firstReturnX = marker.coerceIn(newEdge(false), newEdge(true)),
                ), engine.snapshot())
                steps(engine, 1)
                assertEquals(Phase.FIRING, engine.phase)
                assertEquals(180.0, engine.balls.single().x, 1e-9)
                assertEquals(marker.coerceIn(newEdge(false), newEdge(true)), engine.nextLaunchX, 0.0)
            }
        }
    }

    @Test fun allActiveBallsReturnedDuringMigrationStillWaitForPendingLaunches() {
        for (right in listOf(false, true)) {
            val legacy = legacyVolley(
                balls = listOf(Ball(oldEdge(right), 320.0, 0.0, -430.0)),
                blocks = listOf(edgeBlock(right)), launchX = oldEdge(right), pending = 1,
            ).copy(collectedBalls = 2)
            val engine = restored(legacy)
            assertEquals(Phase.FIRING, engine.phase)
            assertTrue(engine.balls.isEmpty())
            assertEquals(1, engine.snapshot().pendingLaunches)
            assertEquals(2, engine.ballCount)
            assertEquals(2, engine.snapshot().collectedBalls)
            assertEquals(newEdge(right), engine.nextLaunchX, 0.0)
            assertEquals(engine.snapshot(), restored(engine.snapshot()).snapshot())
            steps(engine, 1)
            assertEquals(Phase.FIRING, engine.phase)
            assertEquals(1, engine.balls.size)
            assertEquals(0, engine.snapshot().pendingLaunches)
            assertEquals(newEdge(right), engine.balls.single().x, 1e-9)
            assertEquals(2, engine.ballCount)
            assertEquals(0L, engine.totalHits)
            assertTrue(engine.recall())
            assertEquals(4, engine.ballCount)
        }
    }

    @Test fun returningAllRemainingBallsCommitsEarnedPickupsAndAdvancesExactlyOnce() {
        for (reverse in listOf(false, true)) {
            val balls = listOf(Ball(oldEdge(false), 320.0, 0.0, -430.0), Ball(oldEdge(true), 320.0, 0.0, -430.0))
            val legacy = legacyVolley(
                balls = if (reverse) balls.reversed() else balls,
                blocks = listOf(edgeBlock(false), edgeBlock(true).copy(id = 2)),
            ).copy(
                collectedBalls = 2, totalHits = 7, destroyedBlocks = 1,
                pickups = listOf(Pickup(3, 3, 7)), accumulator = STEP / 3,
            )
            val input = detached(legacy)
            val engine = restored(legacy)
            val expected = legacy.copy(
                version = 3, phase = Phase.ADVANCING, balls = emptyList(), ballCount = 4,
                launchX = newEdge(false), firstReturnX = null, collectedBalls = 0,
            )
            assertEquals(expected, engine.snapshot())
            assertEquals(input, legacy)
            assertFalse(engine.recall())
            val resumed = restored(Json.decodeFromString<GameSnapshot>(Json.encodeToString(engine.snapshot())))
            assertEquals(expected, resumed.snapshot())
            steps(engine, 27)
            steps(resumed, 27)
            assertEquals(engine.snapshot(), resumed.snapshot())
            assertEquals(Phase.AIMING, engine.phase)
            assertEquals(2, engine.round)
            assertEquals(4, engine.ballCount)
            assertEquals(7L, engine.totalHits)
            assertEquals(1, engine.destroyedBlocks)
            assertTrue(engine.blocks.filter { it.id <= 2 }.all { it.row == 7 && it.hits == 10 })
            assertEquals(8, engine.pickups.single { it.id == 3L }.row)
            val after = engine.snapshot()
            engine.tick(0.25)
            val idle = engine.snapshot()
            assertEquals(after.accumulator, idle.accumulator, 1e-12)
            assertEquals(after.copy(accumulator = idle.accumulator), idle)
        }
    }

    @Test fun migrationFinishingAVolleyPreservesTheBallCapAndExistingMarker() {
        val legacy = legacyVolley(
            balls = listOf(Ball(oldEdge(false), 320.0, 0.0, -430.0)),
            blocks = listOf(edgeBlock(false)), firstReturnX = 123.0,
        ).copy(ballCount = 999, collectedBalls = 2)
        val engine = restored(legacy)
        assertEquals(Phase.ADVANCING, engine.phase)
        assertEquals(999, engine.ballCount)
        assertEquals(123.0, engine.launchX, 0.0)
        assertEquals(0, engine.snapshot().collectedBalls)
        assertNull(engine.snapshot().firstReturnX)
        assertEquals(engine.snapshot(), restored(engine.snapshot()).snapshot())
    }

    @Test fun migrationDoesNotMutateOrRetainAnyMutableInputEntities() {
        val legacy = legacyVolley(
            balls = listOf(
                Ball(oldEdge(false), 320.0, 0.0, -430.0),
                Ball(oldEdge(true), 250.0, 100.0, -400.0),
                Ball(180.0, 360.0, 0.0, -430.0),
            ),
            blocks = listOf(edgeBlock(false)), launchX = oldEdge(false), pending = 1,
        ).copy(pickups = listOf(Pickup(2, 2, 4)), impacts = listOf(Impact(20.0, 320.0, 10, 0.01)))
        val before = detached(legacy)
        val engine = restored(legacy)
        assertEquals(before, legacy)
        assertNotSame(legacy.blocks.single(), engine.blocks.single())
        assertNotSame(legacy.pickups.single(), engine.pickups.single())
        assertNotSame(legacy.impacts.single(), engine.impacts.single())
        assertNotSame(legacy.balls[1], engine.balls[0])
        assertNotSame(legacy.balls[2], engine.balls[1])
        steps(engine, 1)
        assertEquals(before, legacy)
        val expected = engine.snapshot()
        legacy.blocks.single().hits = 99
        legacy.pickups.single().row = 8
        legacy.impacts.single().age = 0.17
        legacy.balls.forEach { it.x = 100.0; it.vx = 1.0 }
        assertEquals(expected, engine.snapshot())
        assertNotNull(GameEngine.restore(expected))
    }

    @Test fun corruptLegacyStateIsRejectedBeforeAnyClampingOrForcedReturn() {
        val base = legacyVolley(
            balls = listOf(Ball(180.0, 400.0, 0.0, -430.0)),
            blocks = listOf(edgeBlock(false)), launchX = oldEdge(false), firstReturnX = 120.0,
        )
        val invalid = listOf(
            "nonfinite launch" to base.copy(launchX = Double.NaN),
            "launch outside old left wall" to base.copy(launchX = 15.999),
            "launch outside old right wall" to base.copy(launchX = 344.001),
            "nonfinite first landing" to base.copy(firstReturnX = Double.POSITIVE_INFINITY),
            "landing outside old left wall" to base.copy(firstReturnX = 15.999),
            "landing outside old right wall" to base.copy(firstReturnX = 344.001),
            "missing first landing" to base.copy(firstReturnX = null),
            "nonfinite ball x" to base.copy(balls = listOf(Ball(Double.NaN, 250.0, 0.0, -430.0))),
            "nonfinite ball y" to base.copy(balls = listOf(Ball(16.0, Double.NEGATIVE_INFINITY, 0.0, -430.0))),
            "ball outside old left wall" to base.copy(balls = listOf(Ball(15.999, 320.0, 0.0, -430.0))),
            "ball outside old right wall" to base.copy(balls = listOf(Ball(344.001, 320.0, 0.0, -430.0))),
            "ball above original ceiling" to base.copy(balls = listOf(Ball(16.0, 11.99, 0.0, -430.0))),
            "ball below unchanged floor" to base.copy(balls = listOf(Ball(16.0, 478.01, 0.0, -430.0))),
            "nonfinite velocity in removed lane" to base.copy(balls = listOf(Ball(16.0, 320.0, Double.NaN, -430.0))),
            "motionless ball in removed lane" to base.copy(balls = listOf(Ball(16.0, 320.0, 0.0, 0.0))),
            "excessive speed in removed lane" to base.copy(balls = listOf(Ball(16.0, 320.0, 0.0, -10_001.0))),
            "already overlaps old left block" to base.copy(balls = listOf(Ball(17.0, 320.0, 0.0, -430.0))),
            "already overlaps old right block" to base.copy(
                blocks = listOf(edgeBlock(true)), balls = listOf(Ball(341.0, 320.0, 0.0, -430.0)),
            ),
            "already inside an old block" to base.copy(balls = listOf(Ball(41.0, 320.0, 0.0, -430.0))),
            "invalid queued release" to base.copy(pendingLaunches = 1, launchCountdown = 0.0),
            "duplicate block ID" to base.copy(blocks = listOf(edgeBlock(false), edgeBlock(true))),
            "negative earned pickups" to base.copy(collectedBalls = -1),
            "impossible completed volley" to base.copy(balls = emptyList()),
            "already timed out" to base.copy(shotElapsed = volleyDeadlineForTest(base.ballCount)),
        )
        for ((label, invalidSave) in invalid) {
            val before = detached(invalidSave)
            assertNull(label, GameEngine.restore(invalidSave))
            assertEquals(label, before, invalidSave)
        }
        for (version in listOf(-1, 0, 99)) assertNull(GameEngine.restore(base.copy(version = version)))
    }

    @Test fun versionTwoMigrationStillRejectsOldSideLanesAndAcceptsBothBoundaryCenters() {
        for (right in listOf(false, true)) {
            val current = legacyVolley(
                balls = listOf(Ball(180.0, 250.0, 0.0, -430.0)), firstReturnX = 120.0,
            ).copy(version = 2)
            for (outside in listOf(oldEdge(right), newEdge(right) + if (right) 0.001 else -0.001)) {
                val invalid = listOf(
                    current.copy(launchX = outside), current.copy(firstReturnX = outside),
                    current.copy(balls = listOf(Ball(outside, 250.0, 0.0, -430.0))),
                )
                for (s in invalid) {
                    assertNull(GameEngine.restore(s))
                    val decoded = Json.decodeFromString<GameSnapshot>(Json.encodeToString(s))
                    assertEquals(2, decoded.version)
                    assertNull(GameEngine.restore(decoded))
                }
            }
            val boundary = current.copy(
                launchX = newEdge(right), firstReturnX = newEdge(right),
                balls = listOf(Ball(newEdge(right), 250.0, if (right) 100.0 else -100.0, -400.0)),
            )
            val engine = restored(boundary)
            assertEquals(boundary.copy(version = 3), engine.snapshot())
            steps(engine, 1)
            assertTrue(engine.balls.single().x in newEdge(false)..newEdge(true))
            assertNotNull(GameEngine.restore(engine.snapshot()))
        }
    }
}