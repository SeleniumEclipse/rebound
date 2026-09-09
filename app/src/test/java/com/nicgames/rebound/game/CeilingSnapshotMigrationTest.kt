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

class CeilingSnapshotMigrationTest {
    private val minX = Board.LEFT + Board.BALL_RADIUS
    private val maxX = Board.RIGHT - Board.BALL_RADIUS
    private val minY = Board.TOP + Board.BALL_RADIUS

    private fun oldVolley(
        version: Int, balls: List<Ball>, blocks: List<Block> = emptyList(),
        pending: Int = 0, firstReturnX: Double? = null,
    ): GameSnapshot = emptySnapshot().copy(
        version = version, phase = Phase.FIRING, blocks = blocks, balls = balls,
        ballCount = balls.size + pending + (if (firstReturnX == null) 0 else 1),
        pendingLaunches = pending, launchCountdown = if (pending > 0) STEP else 0.0,
        firstReturnX = firstReturnX, shotElapsed = 0.11,
    )

    private fun detached(s: GameSnapshot): GameSnapshot = s.copy(
        blocks = s.blocks.map { it.copy() }, pickups = s.pickups.map { it.copy() },
        balls = s.balls.map { it.copy() }, impacts = s.impacts.map { it.copy() },
    )

    @Test fun oldClearCeilingBallsClampAndReflectOnlyOutwardVerticalVelocityWithoutSharingInput() {
        for (version in listOf(1, 2)) {
            for (y in listOf(12.0, 16.0, 23.999)) {
                for (vy in listOf(-400.0, 0.0, 400.0)) {
                    val ball = Ball(179.0, y, 100.0, vy)
                    val source = oldVolley(
                        version, listOf(ball), listOf(Block(1, 0, 0, 10)), pending = 2, firstReturnX = 123.0,
                    ).copy(
                        pickups = listOf(Pickup(2, 5, 0)), impacts = listOf(Impact(100.0, 100.0, 8, 0.03)),
                        totalHits = 7, destroyedBlocks = 1, collectedBalls = 2, accumulator = STEP / 3,
                    )
                    val before = detached(source)
                    val engine = restored(source)
                    val expectedBall = ball.copy(y = minY, vy = if (vy < 0.0) -vy else vy)
                    assertEquals(source.copy(version = 3, balls = listOf(expectedBall)), engine.snapshot())
                    assertEquals(before, source)
                    assertNotSame(source.blocks, engine.blocks)
                    assertNotSame(source.blocks.single(), engine.blocks.single())
                    assertNotSame(source.pickups, engine.pickups)
                    assertNotSame(source.pickups.single(), engine.pickups.single())
                    assertNotSame(source.balls, engine.balls)
                    assertNotSame(ball, engine.balls.single())
                    assertNotSame(source.impacts, engine.impacts)
                    assertNotSame(source.impacts.single(), engine.impacts.single())
                    assertEquals(hypot(100.0, vy), hypot(engine.balls.single().vx, engine.balls.single().vy), 0.0)
                    steps(engine, 1)
                    assertEquals(before, source)
                    val after = engine.snapshot()
                    source.blocks.single().hits = 99
                    source.pickups.single().row = 8
                    source.impacts.single().age = 0.17
                    ball.y = 200.0
                    ball.vy = 1.0
                    assertEquals(after, engine.snapshot())
                    assertEquals(after, restored(after).snapshot())
                }
            }
        }
    }

    @Test fun inBoundsOlderVersionsPreserveEveryFieldAndAllFutureSteps() {
        for (version in listOf(1, 2)) {
            val original = fixture(
                blocks = listOf(Block(1, 3, 0, 20)), pickups = listOf(Pickup(2, 2, 1)), ballCount = 12,
            )
            assertTrue(original.fire(-PI / 2))
            original.tick(0.237)
            val current = original.snapshot()
            assertTrue(current.pendingLaunches > 0)
            assertTrue(current.accumulator > 0.0)
            val source = detached(current).copy(version = version)
            val before = detached(source)
            val resumed = restored(source)
            assertEquals(current, resumed.snapshot())
            repeat(240) {
                original.tick(0.017)
                resumed.tick(0.017)
                assertEquals(original.snapshot(), resumed.snapshot())
            }
            finishTurn(original)
            finishTurn(resumed)
            assertEquals(original.snapshot(), resumed.snapshot())
            assertTrue(resumed.round > current.round)
            assertEquals(before, source)
        }
    }

    @Test fun trappedCeilingBallsReturnWithoutDamageWhileOtherBallsAndQueuesContinue() {
        for (version in listOf(1, 2)) {
            for (y in listOf(12.0, 16.0)) {
                for (vy in listOf(-430.0, 430.0)) {
                    for (marker in listOf(null, 123.0)) {
                        val safe = Ball(87.0, 300.0, 0.0, -430.0)
                        val source = oldVolley(
                            version, listOf(Ball(179.0, y, 0.0, vy), safe), listOf(Block(1, 3, 0, 10)),
                            pending = 2, firstReturnX = marker,
                        ).copy(
                            // A swept return would wrongly collect this pickup below the trapped ball.
                            pickups = listOf(Pickup(2, 3, 7)), collectedBalls = 1,
                            totalHits = 7, destroyedBlocks = 1, impacts = listOf(Impact(179.0, 62.0, 11, 0.03)),
                        )
                        assertTrue(source.balls.none { ball -> source.blocks.any { Collision.overlapsBox(ball, it) } })
                        val before = detached(source)
                        val engine = restored(source)
                        assertEquals(source.copy(
                            version = 3, balls = listOf(safe), firstReturnX = marker ?: 179.0,
                        ), engine.snapshot())
                        assertEquals(Phase.FIRING, engine.phase)
                        assertEquals(marker ?: 179.0, engine.nextLaunchX, 0.0)
                        steps(engine, 1)
                        assertTrue(engine.balls.first().y < safe.y)
                        assertEquals(180.0, engine.balls.last().x, 1e-9)
                        assertEquals(Board.LAUNCH_Y, engine.balls.last().y, 1e-9)
                        assertEquals(1, engine.snapshot().pendingLaunches)
                        assertEquals(7L, engine.totalHits)
                        assertEquals(10, engine.blocks.single().hits)
                        assertEquals(source.pickups, engine.pickups)
                        assertEquals(1, engine.snapshot().collectedBalls)
                        assertEquals(before, source)
                        assertNotNull(GameEngine.restore(engine.snapshot()))
                        assertTrue(engine.recall())
                        assertEquals(source.ballCount + 1, engine.ballCount)
                        assertFalse(engine.recall())
                        assertEquals(source.ballCount + 1, engine.ballCount)
                    }
                }
            }
        }
    }

    @Test fun allCeilingBallsReturnedStillWaitForQueuedLaunches() {
        for (version in listOf(1, 2)) {
            val source = oldVolley(
                version, listOf(Ball(179.0, 12.0, 0.0, 430.0)), listOf(Block(1, 3, 0, 10)), pending = 1,
            ).copy(collectedBalls = 2)
            val engine = restored(source)
            assertEquals(source.copy(version = 3, balls = emptyList(), firstReturnX = 179.0), engine.snapshot())
            assertEquals(Phase.FIRING, engine.phase)
            assertTrue(engine.balls.isEmpty())
            assertEquals(2, engine.ballCount)
            assertEquals(2, engine.snapshot().collectedBalls)
            assertEquals(engine.snapshot(), restored(engine.snapshot()).snapshot())
            steps(engine, 1)
            assertEquals(Phase.FIRING, engine.phase)
            assertEquals(0, engine.snapshot().pendingLaunches)
            assertEquals(180.0, engine.balls.single().x, 1e-9)
            assertEquals(Board.LAUNCH_Y, engine.balls.single().y, 1e-9)
            assertEquals(179.0, engine.nextLaunchX, 0.0)
            assertEquals(2, engine.ballCount)
            assertEquals(0L, engine.totalHits)
            assertTrue(engine.recall())
            assertEquals(4, engine.ballCount)
            assertFalse(engine.recall())
            assertEquals(4, engine.ballCount)
        }
    }

    @Test fun allCeilingReturnsCommitEarnedPickupsOnceAndKeepTheMarkerOrLeftmostTie() {
        for (version in listOf(1, 2)) {
            val markers = if (version == 1) listOf(null, 123.0, 16.0, 344.0) else listOf(null, 123.0)
            for (marker in markers) {
                for (capped in if (marker == null) listOf(false) else listOf(false, true)) {
                    for (reverse in listOf(false, true)) {
                        val balls = listOf(Ball(41.0, 12.0, 0.0, 430.0), Ball(317.0, 16.0, 0.0, 430.0))
                        val base = oldVolley(
                            version, if (reverse) balls.reversed() else balls,
                            listOf(Block(1, 0, 0, 10), Block(2, 6, 0, 10)), firstReturnX = marker,
                        )
                        val source = base.copy(
                            ballCount = if (capped) 999 else base.ballCount,
                            collectedBalls = 2, totalHits = 7, destroyedBlocks = 1,
                            pickups = listOf(Pickup(3, 0, 7)), accumulator = STEP / 3,
                            impacts = listOf(Impact(41.0, 62.0, 11, 0.03)),
                        )
                        val before = detached(source)
                        val engine = restored(source)
                        val expected = source.copy(
                            version = 3, phase = Phase.ADVANCING, balls = emptyList(),
                            launchX = marker?.coerceIn(minX, maxX) ?: 41.0,
                            ballCount = (source.ballCount + 2).coerceAtMost(999),
                            firstReturnX = null, collectedBalls = 0,
                        )
                        assertEquals(expected, engine.snapshot())
                        assertEquals(before, source)
                        assertFalse(engine.recall())
                        val resumed = restored(Json.decodeFromString<GameSnapshot>(Json.encodeToString(expected)))
                        assertEquals(expected, resumed.snapshot())
                        steps(engine, 27)
                        steps(resumed, 27)
                        assertEquals(engine.snapshot(), resumed.snapshot())
                        assertEquals(Phase.AIMING, engine.phase)
                        assertEquals(2, engine.round)
                        assertEquals(expected.ballCount, engine.ballCount)
                        assertEquals(7L, engine.totalHits)
                        assertEquals(1, engine.destroyedBlocks)
                        assertTrue(engine.blocks.filter { it.id <= 2 }.all { it.row == 1 && it.hits == 10 })
                        assertEquals(8, engine.pickups.single { it.id == 3L }.row)
                        val after = engine.snapshot()
                        engine.tick(0.25)
                        val idle = engine.snapshot()
                        assertEquals(after.accumulator, idle.accumulator, 1e-12)
                        assertEquals(after.copy(accumulator = idle.accumulator), idle)
                        assertEquals(before, source)
                    }
                }
            }
        }
    }

    @Test fun versionOneTopCornersClampBothAxesBeforeCheckingBlockOverlap() {
        for (right in listOf(false, true)) {
            val oldX = if (right) 344.0 else 16.0
            val newX = if (right) maxX else minX
            val inward = if (right) -1.0 else 1.0
            for (blocked in listOf(false, true)) {
                for (y in listOf(12.0, 23.999)) {
                    for (vx in listOf(-100.0, 100.0)) {
                        for (vy in listOf(-400.0, 400.0)) {
                            val ball = Ball(oldX, y, vx, vy)
                            val source = oldVolley(
                                1, listOf(ball),
                                listOf(Block(1, if (blocked) (if (right) 6 else 0) else 3, 0, 10)),
                                pending = 1, firstReturnX = if (right) 16.0 else 344.0,
                            ).copy(launchX = oldX)
                            val before = detached(source)
                            val engine = restored(source)
                            val expectedBalls = if (blocked) emptyList() else listOf(ball.copy(
                                x = newX, y = minY, vx = if (vx * inward < 0.0) -vx else vx,
                                vy = if (vy < 0.0) -vy else vy,
                            ))
                            assertEquals(source.copy(
                                version = 3, balls = expectedBalls, launchX = newX,
                                firstReturnX = if (right) minX else maxX,
                            ), engine.snapshot())
                            assertEquals(Phase.FIRING, engine.phase)
                            assertEquals(0L, engine.totalHits)
                            assertEquals(10, engine.blocks.single().hits)
                            steps(engine, 1)
                            assertEquals(newX, engine.balls.last().x, 1e-9)
                            assertEquals(Board.LAUNCH_Y, engine.balls.last().y, 1e-9)
                            assertEquals(before, source)
                            assertNotNull(GameEngine.restore(engine.snapshot()))
                        }
                    }
                }
            }
        }
    }

    @Test fun originalVersionBoundsAndOverlapsAreRejectedBeforeAnyCeilingClampOrReturn() {
        for (version in listOf(1, 2)) {
            val originalMinX = if (version == 1) 16.0 else 24.0
            val originalMaxX = if (version == 1) 344.0 else 334.0
            val source = oldVolley(
                version, listOf(Ball(179.0, 12.0, 0.0, 430.0)), listOf(Block(1, 3, 0, 10)), firstReturnX = 120.0,
            )
            assertNotNull(GameEngine.restore(source))
            val invalid = listOf(
                "launch left of original wall" to source.copy(launchX = originalMinX - 0.001),
                "launch right of original wall" to source.copy(launchX = originalMaxX + 0.001),
                "return left of original wall" to source.copy(firstReturnX = originalMinX - 0.001),
                "return right of original wall" to source.copy(firstReturnX = originalMaxX + 0.001),
                "ball left of original wall" to source.copy(balls = listOf(Ball(originalMinX - 0.001, 12.0, 0.0, 430.0))),
                "ball right of original wall" to source.copy(balls = listOf(Ball(originalMaxX + 0.001, 12.0, 0.0, 430.0))),
                "ball above original ceiling" to source.copy(balls = listOf(Ball(179.0, 11.999, 0.0, 430.0))),
                "ball below floor" to source.copy(balls = listOf(Ball(179.0, 478.001, 0.0, 430.0))),
                "already overlaps row zero" to source.copy(balls = listOf(Ball(179.0, 16.001, 0.0, 430.0))),
                "nonfinite x" to source.copy(balls = listOf(Ball(Double.NaN, 12.0, 0.0, 430.0))),
                "nonfinite y" to source.copy(balls = listOf(Ball(179.0, Double.NEGATIVE_INFINITY, 0.0, 430.0))),
                "nonfinite velocity" to source.copy(balls = listOf(Ball(179.0, 12.0, 0.0, Double.NaN))),
                "motionless trapped ball" to source.copy(balls = listOf(Ball(179.0, 12.0, 0.0, 0.0))),
                "excessive speed" to source.copy(balls = listOf(Ball(179.0, 12.0, 0.0, 10_001.0))),
                "missing old return marker" to source.copy(firstReturnX = null),
                "invalid pending launch" to source.copy(pendingLaunches = 1),
                "unknown version" to source.copy(version = 99),
            )
            for ((label, bad) in invalid) {
                val before = detached(bad)
                assertNull("Version $version: $label", GameEngine.restore(bad))
                assertEquals(before, bad)
            }
        }
    }

    @Test fun currentSnapshotsRejectOldCeilingSpaceWhileBoundaryCentersRemainValidInAllVersions() {
        val current = oldVolley(3, listOf(Ball(179.0, 100.0, 0.0, 430.0)), firstReturnX = 120.0)
        assertNotNull(GameEngine.restore(current))
        for (y in listOf(12.0, 16.0, 23.999)) {
            val above = current.copy(balls = listOf(Ball(179.0, y, 0.0, 430.0)))
            val before = detached(above)
            assertNull(GameEngine.restore(above))
            // A downward-moving ball above an unbroken first row is not a valid v3 fixture either.
            assertNull(GameEngine.restore(above.copy(blocks = listOf(Block(1, 3, 0, 10)))))
            assertEquals(before, above)
        }
        for (x in listOf(16.0, 23.999, 334.001, 344.0)) {
            assertNull(GameEngine.restore(current.copy(launchX = x)))
            assertNull(GameEngine.restore(current.copy(firstReturnX = x)))
            assertNull(GameEngine.restore(current.copy(balls = listOf(Ball(x, minY, 0.0, 430.0)))))
        }
        for (version in listOf(1, 2, 3)) {
            for (x in listOf(minX, 179.0, maxX)) {
                for (vy in listOf(-430.0, 430.0)) {
                    val source = current.copy(version = version, balls = listOf(Ball(x, minY, 0.0, vy)))
                    val engine = restored(source)
                    // An unchanged boundary position must not reflect early during restore.
                    assertEquals(source.copy(version = 3), engine.snapshot())
                    steps(engine, 1)
                    assertEquals(430.0, engine.balls.single().vy, 1e-9)
                    assertTrue(engine.balls.single().y > minY)
                    assertEquals(0L, engine.totalHits)
                    assertNotNull(GameEngine.restore(engine.snapshot()))
                }
            }
        }
    }

    @Test fun ceilingMigrationsRoundTripAsVersionThreeAndMissingWireVersionStillMeansOne() {
        for (json in listOf(Json, Json { encodeDefaults = true }, Json { ignoreUnknownKeys = true })) {
            for (version in listOf(1, 2)) {
                val oldX = if (version == 1) 16.0 else 24.0
                val source = oldVolley(
                    version, listOf(Ball(oldX, 12.0, -100.0, -400.0)), pending = 2, firstReturnX = 120.0,
                ).copy(launchX = oldX)
                val fields = json.parseToJsonElement(json.encodeToString(source)).jsonObject.toMutableMap()
                if (version == 1) fields.remove("version")
                else assertEquals(JsonPrimitive(2), fields["version"])
                val decoded = json.decodeFromString<GameSnapshot>(JsonObject(fields).toString())
                assertEquals(version, decoded.version)
                assertEquals(source, decoded)
                val engine = restored(decoded)
                val saved = engine.snapshot()
                assertEquals(3, saved.version)
                assertEquals(minX, saved.launchX, 0.0)
                // Only version 1 moves x; a version-2 ball already at its wall bounces on tick.
                assertEquals(listOf(Ball(minX, minY, if (version == 1) 100.0 else -100.0, 400.0)), saved.balls)
                val encoded = json.encodeToString(saved)
                assertEquals(JsonPrimitive(3), json.parseToJsonElement(encoded).jsonObject["version"])
                val resumed = restored(json.decodeFromString<GameSnapshot>(encoded))
                assertEquals(saved, resumed.snapshot())
                repeat(12) {
                    steps(engine, 1)
                    steps(resumed, 1)
                    assertEquals(engine.snapshot(), resumed.snapshot())
                }
            }
            for (x in listOf(15.999, 344.001)) {
                val corrupt = oldVolley(1, listOf(Ball(x, 12.0, 0.0, 430.0)))
                val fields = json.parseToJsonElement(json.encodeToString(corrupt)).jsonObject.toMutableMap()
                fields.remove("version")
                val decoded = json.decodeFromString<GameSnapshot>(JsonObject(fields).toString())
                assertEquals(1, decoded.version)
                assertNull(GameEngine.restore(decoded))
            }
        }
    }
}