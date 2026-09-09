package com.nicgames.rebound.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class GameEngineTest {
    @Test fun geometryMatchesUiCoordinates() {
        val block = Block(1, 3, 2, 4)
        val pickup = Pickup(2, 3, 2)
        assertEquals(158.0, block.x, 0.0)
        assertEquals(114.0, block.y, 0.0)
        assertEquals(179.0, pickup.x, 0.0)
        assertEquals(135.0, pickup.y, 0.0)
        block.row++
        assertEquals(161.0, block.y, 0.0)
    }

    @Test fun startingBoardIsApproachableAndHasGapsAndOnePickup() {
        repeat(100) { seed ->
            val engine = GameEngine(seed.toLong())
            assertEquals(Phase.AIMING, engine.phase)
            assertEquals(1, engine.round)
            assertEquals(1, engine.ballCount)
            assertEquals(180.0, engine.launchX, 0.0)
            assertTrue(engine.blocks.size in 2..4)
            assertEquals(1, engine.pickups.size)
            assertTrue(engine.blocks.all { it.hits == 1 && it.row == 0 })
            val occupied = engine.blocks.map { it.column } + engine.pickups.map { it.column }
            assertEquals(occupied.size, occupied.toSet().size)
            assertTrue(Board.COLUMNS - occupied.size >= 2)
        }
    }

    @Test fun fireReleasesImmediatelyAndOnlyFromAiming() {
        val engine = fixture()
        assertTrue(engine.fire(-PI / 2))
        assertEquals(1, engine.balls.size)
        assertEquals(Board.LAUNCH_Y, engine.balls.single().y, 0.0)
        assertEquals(430.0, hypot(engine.balls.single().vx, engine.balls.single().vy), 1e-10)
        val firing = engine.snapshot()
        assertFalse(engine.fire(-1.0))
        assertEquals(firing, engine.snapshot())
        assertTrue(engine.recall())
        assertFalse(engine.fire(-1.0))
    }

    @Test fun invalidAnglesAreRejectedWithoutChangingAnything() {
        val engine = fixture()
        val before = engine.snapshot()
        for (angle in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            0.0, PI / 2, -PI, -0.1199, -PI + 0.1199, -5.0, 10.0)) {
            assertFalse("Invalid angle: $angle", engine.fire(angle))
            assertTrue(engine.aimPath(angle).isEmpty())
            assertEquals(before, engine.snapshot())
        }
    }

    @Test fun bothLegalAngleLimitsAreAccepted() {
        assertTrue(fixture().fire(-PI + 0.12))
        assertTrue(fixture().fire(-0.12))
    }

    @Test fun invalidTimeDoesNotChangeSimulationOrAccumulator() {
        val engine = fixture()
        engine.fire(-1.0)
        val before = engine.snapshot()
        for (dt in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            engine.tick(dt)
            assertEquals(before, engine.snapshot())
        }
    }

    @Test fun hugeInputDeltaIsClampedRatherThanFastForwarded() {
        val a = fixture()
        val b = fixture()
        a.fire(-1.0)
        b.fire(-1.0)
        a.tick(1e300)
        b.tick(0.25)
        assertSameState(a, b)
    }

    @Test fun accumulatorWaitsUntilAWholeFixedStepExists() {
        val engine = fixture()
        engine.fire(-PI / 2)
        engine.tick(STEP / 2)
        assertEquals(Board.LAUNCH_Y, engine.balls.single().y, 0.0)
        engine.tick(STEP / 2)
        assertEquals(Board.LAUNCH_Y - 430.0 * STEP, engine.balls.single().y, 1e-10)
    }

    @Test fun splitDeltasProduceTheSameFixedStepSimulation() {
        val a = GameEngine(345)
        val b = GameEngine(345)
        a.fire(-1.7)
        b.fire(-1.7)
        repeat(20) {
            a.tick(0.25)
            repeat(25) { b.tick(0.01) }
            assertSameState(a, b)
        }
    }

    @Test fun streamLaunchesEveryFortyFiveMillisecondsWithinFixedSteps() {
        val engine = fixture(ballCount = 5)
        engine.fire(-PI / 2)
        steps(engine, 5)
        assertEquals(1, engine.balls.size)
        steps(engine, 1)
        assertEquals(2, engine.balls.size)
        assertEquals(Board.LAUNCH_Y - 430.0 * 0.05, engine.balls[0].y, 1e-9)
        assertEquals(Board.LAUNCH_Y - 430.0 * 0.005, engine.balls[1].y, 1e-9)
        steps(engine, 5)
        assertEquals(3, engine.balls.size)
        assertEquals(2, engine.snapshot().pendingLaunches)
    }

    @Test fun faceHitDealsDamageOnceAndBouncesDown() {
        val engine = fixture(blocks = listOf(Block(1, 3, 6, 3)))
        engine.fire(-PI / 2)
        steps(engine, 60)
        assertEquals(2, engine.blocks.single().hits)
        assertEquals(1L, engine.totalHits)
        assertEquals(0, engine.destroyedBlocks)
        assertTrue(engine.balls.single().vy > 0.0)
        steps(engine, 6)
        assertEquals(1L, engine.totalHits)
    }

    @Test fun zeroHitBlockDisappearsImmediatelyAndStillReflectsTheBall() {
        val engine = fixture(blocks = listOf(Block(1, 3, 6, 1)))
        engine.fire(-PI / 2)
        steps(engine, 40)
        assertTrue(engine.blocks.isEmpty())
        assertEquals(1L, engine.totalHits)
        assertEquals(1, engine.destroyedBlocks)
        assertTrue(engine.balls.single().vy > 0.0)
        assertEquals(1, engine.impacts.single().hitsBefore)
    }

    @Test fun sharedBlockIsDestroyedByTheEarliestBallInEitherArrayOrder() {
        val original = restored(sharedBlockRaceSnapshot())
        val reversed = restored(sharedBlockRaceSnapshot(reverseBalls = true))
        for (engine in listOf(original, reversed)) {
            steps(engine, 1)
            assertEquals(1L, engine.totalHits)
            assertEquals(1, engine.destroyedBlocks)
            assertTrue(engine.blocks.isEmpty())
            val farther = engine.balls.single { it.x == 170.0 }
            val nearer = engine.balls.single { it.x == 190.0 }
            assertEquals(-430.0, farther.vy, 0.0)
            assertEquals(430.0, nearer.vy, 1e-10)
            assertEquals(351.0 - 430.0 * STEP, farther.y, 1e-10)
            assertEquals(348.0 + 430.0 * (STEP - 1.0 / 430.0) + 1e-5, nearer.y, 1e-10)
            assertEquals(190.0, engine.impacts.single().x, 0.0)
            assertTrue(engine.balls.all { hypot(it.vx, it.vy) == 430.0 })
            assertNotNull(GameEngine.restore(engine.snapshot()))
        }
        assertSameStateIgnoringBallOrder(original, reversed)
        for (engine in listOf(original, reversed)) {
            steps(engine, 36)
            assertEquals(Phase.FIRING, engine.phase)
            assertEquals(190.0, requireNotNull(engine.snapshot().firstReturnX), 0.0)
            finishTurn(engine)
            assertEquals(Phase.AIMING, engine.phase)
            assertEquals(190.0, engine.launchX, 0.0)
        }
        assertSameState(original, reversed)
    }

    @Test fun simultaneousSharedBlockHitsUseGeometryRatherThanArrayOrder() {
        val start = sharedBlockRaceSnapshot().copy(balls = listOf(
            Ball(170.0, 349.0, 0.0, -430.0), Ball(190.0, 349.0, 0.0, -430.0),
        ))
        val original = restored(start)
        val reversed = restored(start.copy(balls = start.balls.reversed()))
        for (engine in listOf(original, reversed)) {
            steps(engine, 1)
            assertEquals(1L, engine.totalHits)
            assertTrue(engine.blocks.isEmpty())
            assertEquals(430.0, engine.balls.single { it.x == 170.0 }.vy, 1e-10)
            assertEquals(-430.0, engine.balls.single { it.x == 190.0 }.vy, 0.0)
        }
        assertSameStateIgnoringBallOrder(original, reversed)
        finishTurn(original)
        finishTurn(reversed)
        assertEquals(170.0, original.launchX, 0.0)
        assertSameState(original, reversed)
    }

    @Test fun invalidatedContactIsRequeuedBeforeAnotherBallsLaterCollision() {
        val start = fixture(
            blocks = listOf(Block(1, 3, 6, 1), Block(2, 3, 5, 1)),
            balls = listOf(
                Ball(190.0, 348.43, 0.0, -430.0),
                Ball(170.0, 360.0, 0.0, -10_000.0),
                Ball(180.0, 248.0, 0.0, 430.0),
            ),
        ).snapshot()
        val original = restored(start)
        val reversed = restored(start.copy(balls = start.balls.reversed()))
        for (engine in listOf(original, reversed)) {
            steps(engine, 1)
            // The first ball removes the lower block at .001s. The fast ball must
            // then hit the upper block at .0059s, before the third ball at ~.007s.
            assertEquals(2L, engine.totalHits)
            assertEquals(2, engine.destroyedBlocks)
            assertTrue(engine.blocks.isEmpty())
            assertEquals(430.0, engine.balls.single { it.x == 190.0 }.vy, 1e-10)
            assertEquals(10_000.0, engine.balls.single { it.x == 170.0 }.vy, 1e-10)
            assertEquals(430.0, engine.balls.single { it.x == 180.0 }.vy, 0.0)
            assertEquals(listOf(190.0, 170.0), engine.impacts.map { it.x })
            assertNotNull(GameEngine.restore(engine.snapshot()))
        }
        assertSameStateIgnoringBallOrder(original, reversed)
    }

    @Test fun destroyingOneOfTwoTiedCornersRecomputesTheSurvivingNormal() {
        val start = fixture(
            blocks = listOf(Block(1, 3, 4, 1), Block(2, 4, 4, 10)),
            balls = listOf(Ball(202.0, 202.0, 0.0, 430.0), Ball(190.0, 203.0, 0.0, 430.0)),
        ).snapshot()
        val original = restored(start)
        val reversed = restored(start.copy(balls = start.balls.reversed()))
        for (engine in listOf(original, reversed)) {
            val gapBall = engine.balls.single { it.x == 202.0 }
            steps(engine, 1)
            assertEquals(2L, engine.totalHits)
            assertEquals(2L, engine.blocks.single().id)
            assertEquals(9, engine.blocks.single().hits)
            assertEquals(-215.0, gapBall.vy, 1e-7)
            assertTrue(gapBall.vx < -370.0)
            assertEquals(430.0, hypot(gapBall.vx, gapBall.vy), 1e-7)
            assertNotNull(GameEngine.restore(engine.snapshot()))
        }
        assertSameStateIgnoringBallOrder(original, reversed)
    }

    @Test fun engineUsesObliqueCircularCornerReflection() {
        val engine = fixture(
            blocks = listOf(Block(1, 3, 3, 5)),
            balls = listOf(Ball(146.8, 156.6, 400.0, 100.0)),
        )
        steps(engine, 3)
        assertEquals(4, engine.blocks.single().hits)
        assertEquals(-208.0, engine.balls.single().vx, 1e-7)
        assertEquals(-356.0, engine.balls.single().vy, 1e-7)
    }

    @Test fun sideWallMirrorsOnlyRelevantComponent() {
        val engine = fixture(balls = listOf(Ball(Board.LEFT + Board.BALL_RADIUS + 1.0, 100.0, -400.0, 100.0)))
        steps(engine, 1)
        assertEquals(400.0, engine.balls.single().vx, 1e-10)
        assertEquals(100.0, engine.balls.single().vy, 1e-10)
    }

    @Test fun exactTwoWallCornerNormalizesReflectionAndPreservesSpeed() {
        val engine = fixture(balls = listOf(Ball(
            Board.LEFT + Board.BALL_RADIUS + 1.0, Board.TOP + Board.BALL_RADIUS + 1.0, -300.0, -300.0,
        )))
        steps(engine, 1)
        assertEquals(300.0, engine.balls.single().vx, 1e-9)
        assertEquals(300.0, engine.balls.single().vy, 1e-9)
        assertEquals(hypot(300.0, 300.0), hypot(engine.balls.single().vx, engine.balls.single().vy), 1e-9)
    }

    @Test fun unequalVelocityWallCornerMirrorsRatherThanSwapsComponents() {
        val engine = fixture(balls = listOf(Ball(
            Board.LEFT + Board.BALL_RADIUS + 2.0, Board.TOP + Board.BALL_RADIUS + 0.5, -400.0, -100.0,
        )))
        steps(engine, 1)
        assertEquals(400.0, engine.balls.single().vx, 1e-9)
        assertEquals(100.0, engine.balls.single().vy, 1e-9)
    }

    @Test fun simultaneousAdjacentCornersHitBothBlocksOnceWithoutEnteringTheNarrowGap() {
        val engine = fixture(
            blocks = listOf(Block(1, 3, 4, 10), Block(2, 4, 4, 10)),
            balls = listOf(Ball(202.0, 190.0, 0.0, 430.0)),
        )
        steps(engine, 6)
        assertEquals(2L, engine.totalHits)
        assertTrue(engine.blocks.all { it.hits == 9 })
        assertEquals(0.0, engine.balls.single().vx, 1e-8)
        assertEquals(-430.0, engine.balls.single().vy, 1e-8)
        assertTrue(engine.balls.single().y < 204.54)
        assertNotNull(GameEngine.restore(engine.snapshot()))
    }

    @Test fun fastBallCannotTunnelEvenWithMaximumFrameDelta() {
        val engine = fixture(
            blocks = listOf(Block(1, 3, 3, 5)),
            balls = listOf(Ball(180.0, 460.0, 0.0, -10_000.0)),
        )
        engine.tick(0.25)
        assertEquals(1L, engine.totalHits)
        assertEquals(4, engine.blocks.single().hits)
        assertTrue(engine.balls.isEmpty())
        assertEquals(Phase.ADVANCING, engine.phase)
    }

    @Test fun manyBallsConsumeAPickupOnlyOnceAndGainItForNextVolley() {
        val engine = fixture(
            pickups = listOf(Pickup(1, 3, 7)),
            balls = List(20) { Ball(179.0, 400.0, 0.0, -430.0) },
        )
        steps(engine, 6)
        assertTrue(engine.pickups.isEmpty())
        assertEquals(20, engine.ballCount)
        assertEquals(20, engine.balls.size)
        assertEquals(1, engine.snapshot().collectedBalls)
        engine.recall()
        assertEquals(21, engine.ballCount)
    }

    @Test fun pickupDoesNotAppendAnExtraBallToTheCurrentLaunchQueue() {
        val engine = fixture(pickups = listOf(Pickup(1, 3, 8)), ballCount = 2, launchX = 179.0)
        engine.fire(-PI / 2)
        steps(engine, 18)
        assertTrue(engine.pickups.isEmpty())
        assertEquals(2, engine.balls.size)
        assertEquals(2, engine.ballCount)
        assertEquals(0, engine.snapshot().pendingLaunches)
        engine.recall()
        finishTurn(engine)
        assertEquals(3, engine.ballCount)
        engine.fire(-PI / 2)
        steps(engine, 12)
        assertEquals(3, engine.balls.size)
    }

    @Test fun pickupCannotBeCollectedThroughAnInterveningBlock() {
        val engine = fixture(
            blocks = listOf(Block(1, 3, 6, 10)),
            pickups = listOf(Pickup(2, 3, 5)),
        )
        engine.fire(-PI / 2)
        steps(engine, 60)
        assertEquals(1, engine.pickups.size)
        assertEquals(0, engine.snapshot().collectedBalls)
    }

    @Test fun highSpeedPickupCollectionUsesTheTravelSegmentNotOnlyTheEndPosition() {
        val engine = fixture(
            pickups = listOf(Pickup(1, 3, 4)),
            balls = listOf(Ball(179.0, 450.0, 0.0, -10_000.0)),
        )
        steps(engine, 3)
        assertTrue(engine.pickups.isEmpty())
        assertEquals(1, engine.snapshot().collectedBalls)
        assertEquals(1, engine.ballCount)
    }

    @Test fun firstLandingIsChronologicalRatherThanBallListOrder() {
        val engine = fixture(balls = listOf(
            Ball(70.0, 475.0, 0.0, 430.0),
            Ball(270.0, 477.0, 0.0, 430.0),
        ))
        steps(engine, 1)
        assertEquals(Phase.ADVANCING, engine.phase)
        assertEquals(270.0, engine.launchX, 1e-10)
        assertTrue(engine.balls.isEmpty())
    }

    @Test fun simultaneousFloorReturnsUseGeometryRatherThanArrayOrder() {
        val start = fixture(balls = listOf(
            Ball(270.0, 477.0, 0.0, 430.0), Ball(70.0, 477.0, 0.0, 430.0),
        )).snapshot()
        val original = restored(start)
        val reversed = restored(start.copy(balls = start.balls.reversed()))
        for (engine in listOf(original, reversed)) {
            steps(engine, 1)
            assertEquals(Phase.ADVANCING, engine.phase)
            assertEquals(70.0, engine.launchX, 0.0)
        }
        assertSameState(original, reversed)
    }

    @Test fun allActiveBallsMustReturnBeforeAdvancing() {
        val engine = fixture(balls = listOf(
            Ball(270.0, 477.0, 0.0, 430.0),
            Ball(70.0, 100.0, 0.0, -430.0),
        ))
        steps(engine, 1)
        assertEquals(Phase.FIRING, engine.phase)
        assertEquals(1, engine.balls.size)
        assertEquals(270.0, requireNotNull(engine.snapshot().firstReturnX), 0.0)
        engine.recall()
        assertEquals(270.0, engine.launchX, 0.0)
    }

    @Test fun emptyAirDoesNotEndVolleyWhileLaunchQueueIsPending() {
        val engine = restored(emptySnapshot().copy(
            phase = Phase.FIRING, ballCount = 2, pendingLaunches = 1, launchCountdown = 0.04,
            balls = listOf(Ball(230.0, 477.0, 0.0, 430.0)),
        ))
        steps(engine, 1)
        assertEquals(Phase.FIRING, engine.phase)
        assertTrue(engine.balls.isEmpty())
        assertEquals(1, engine.snapshot().pendingLaunches)
        steps(engine, 4)
        assertEquals(1, engine.balls.size)
        assertEquals(Phase.FIRING, engine.phase)
        assertEquals(180.0, engine.balls.single().x, 1e-9)
    }

    @Test fun recallCancelsQueueWithoutInventingDamageOrChangingLaunchPoint() {
        val engine = fixture(blocks = listOf(Block(1, 3, 0, 100)), ballCount = 999)
        assertFalse(engine.recall())
        engine.fire(-PI / 2)
        steps(engine, 2)
        assertTrue(engine.snapshot().pendingLaunches > 900)
        assertTrue(engine.recall())
        assertEquals(Phase.ADVANCING, engine.phase)
        assertEquals(0, engine.snapshot().pendingLaunches)
        assertTrue(engine.balls.isEmpty())
        assertEquals(0L, engine.totalHits)
        assertEquals(100, engine.blocks.single().hits)
        assertEquals(180.0, engine.launchX, 0.0)
        assertFalse(engine.recall())
    }

    @Test fun recallKeepsAlreadyDealtDamage() {
        val engine = fixture(blocks = listOf(Block(1, 3, 6, 5)))
        engine.fire(-PI / 2)
        steps(engine, 40)
        engine.recall()
        assertEquals(4, engine.blocks.single().hits)
        assertEquals(1L, engine.totalHits)
    }

    @Test fun rowAnimationCommitsExactlyOnceAfterItsDuration() {
        val engine = fixture(blocks = listOf(Block(1, 3, 7, 5)))
        engine.fire(-1.0)
        engine.recall()
        steps(engine, 26)
        assertEquals(Phase.ADVANCING, engine.phase)
        assertEquals(7, engine.blocks.single().row)
        assertTrue(engine.advanceProgress in 0.98..0.99)
        steps(engine, 1)
        assertEquals(Phase.AIMING, engine.phase)
        assertEquals(8, engine.blocks.single { it.id == 1L }.row)
        assertEquals(2, engine.round)
        assertEquals(0.0, engine.advanceProgress, 0.0)
        assertTrue(engine.blocks.count { it.row == 0 } in 2..4)
        val after = engine.snapshot()
        engine.tick(0.25)
        assertEquals(after, engine.snapshot())
    }

    @Test fun bottomBlockEndsGameWithoutIncrementingRoundOrSpawning() {
        val engine = fixture(blocks = listOf(Block(1, 3, 8, 5)))
        engine.fire(-1.0)
        engine.recall()
        steps(engine, 27)
        assertEquals(Phase.GAME_OVER, engine.phase)
        assertEquals(1, engine.round)
        assertEquals(9, engine.blocks.single().row)
        assertTrue(engine.pickups.isEmpty())
        assertFalse(engine.fire(-1.0))
        assertFalse(engine.recall())
        assertNotNull(GameEngine.restore(engine.snapshot()))
    }

    @Test fun pickupsFallingOffBoardExpireButNeverCauseGameOver() {
        val engine = fixture(pickups = listOf(Pickup(1, 2, 9)))
        engine.fire(-1.0)
        engine.recall()
        steps(engine, 27)
        assertEquals(Phase.AIMING, engine.phase)
        assertTrue(engine.pickups.none { it.id == 1L })
    }

    @Test fun aimingNeverDescendsOrAddsRowsRegardlessOfWaitingTime() {
        val engine = GameEngine(444)
        val before = engine.snapshot()
        repeat(500) { engine.tick(0.25) }
        assertEquals(before, engine.snapshot())
    }

    @Test fun equalSeedsGenerateIdenticalRowsAcrossRounds() {
        val a = GameEngine(-123456789)
        val b = GameEngine(-123456789)
        repeat(9) {
            assertSameState(a, b)
            a.fire(-1.0)
            b.fire(-1.0)
            a.recall()
            b.recall()
            steps(a, 27)
            steps(b, 27)
        }
        assertSameState(a, b)
    }

    @Test fun laterRowsScaleStrengthAndSometimesHaveDoubleStrength() {
        var sawDouble = false
        repeat(60) { seed ->
            val engine = restored(emptySnapshot(seed.toLong()).copy(round = 20))
            engine.fire(-1.0)
            engine.recall()
            steps(engine, 27)
            assertEquals(21, engine.round)
            assertTrue(engine.blocks.all { it.hits in 15..55 })
            if (engine.blocks.any { it.hits > 28 }) sawDouble = true
        }
        assertTrue("Some seeded rows should include the occasional strong block", sawDouble)
    }

    @Test fun aimStopsAtFirstRadiusAwareBlockHitAndNeverMutatesState() {
        val engine = fixture(blocks = listOf(Block(1, 3, 6, 5)))
        val before = engine.snapshot()
        val path = engine.aimPath(-PI / 2)
        assertEquals(2, path.size)
        assertEquals(Vec2(180.0, Board.LAUNCH_Y), path[0])
        assertEquals(180.0, path[1].x, 1e-9)
        assertEquals(348.0, path[1].y, 1e-9)
        repeat(20) { engine.aimPath(-0.5 - it * 0.1) }
        assertEquals(before, engine.snapshot())
    }

    @Test fun aimStopsAtFirstWallInEmptyBoard() {
        val engine = fixture()
        val path = engine.aimPath(-0.12)
        assertEquals(2, path.size)
        assertEquals(Board.RIGHT - Board.BALL_RADIUS, path.last().x, 1e-9)
        assertTrue(path.last().y < Board.LAUNCH_Y)
    }

    @Test fun horizontalFixtureCannotKeepVolleyAliveBeyondDeadline() {
        val engine = fixture(balls = listOf(Ball(180.0, 250.0, 430.0, 0.0)))
        repeat(95) { engine.tick(0.25) }
        assertEquals(Phase.FIRING, engine.phase)
        engine.tick(0.25)
        assertEquals(24.0, engine.shotElapsed, 1e-10)
        assertEquals(Phase.ADVANCING, engine.phase)
        assertTrue(engine.balls.isEmpty())
    }

    @Test fun maximumVolleyReleasesEveryBallAndClearsBottomBlockWithoutRecall() {
        val engine = fixture(blocks = listOf(Block(1, 3, 8, 999)), ballCount = 999)
        assertTrue(engine.fire(-PI / 2))
        finishTurn(engine)
        assertEquals(Phase.AIMING, engine.phase)
        assertEquals(2, engine.round)
        assertEquals(999L, engine.totalHits)
        assertEquals(1, engine.destroyedBlocks)
        assertTrue(engine.blocks.none { it.id == 1L })
        assertEquals(999, engine.ballCount)
        assertEquals(0, engine.snapshot().pendingLaunches)
        assertTrue(engine.balls.isEmpty())
        assertTrue("The last launch at 44.91s must hit and return naturally", engine.shotElapsed in 44.91..45.1)
    }

    @Test fun saturatedBallCountStillConsumesPickupButNeverExceedsCap() {
        val engine = fixture(
            pickups = listOf(Pickup(1, 3, 7)),
            balls = listOf(Ball(179.0, 400.0, 0.0, -430.0)),
            ballCount = 999,
        )
        steps(engine, 6)
        assertTrue(engine.pickups.isEmpty())
        engine.recall()
        assertEquals(999, engine.ballCount)
    }

    @Test fun visualImpactsAreBoundedAndExpireWithoutSuppressingDamage() {
        val engine = fixture(
            blocks = listOf(Block(1, 3, 3, 1000)),
            balls = List(250) { Ball(180.0, 208.0, 0.0, -430.0) },
        )
        steps(engine, 1)
        assertEquals(250L, engine.totalHits)
        assertEquals(750, engine.blocks.single().hits)
        assertEquals(64, engine.impacts.size)
        assertTrue(engine.impacts.all { it.age == 0.0 })
        steps(engine, 23)
        assertTrue(engine.impacts.isEmpty())
        assertEquals(250L, engine.totalHits)
    }

    @Test fun maximumCrowdedSliceWithDistinctContactTimesKeepsDamageAndImpactsOrdered() {
        val start = fixture(
            blocks = listOf(Block(1, 3, 3, 1000)),
            balls = List(999) { Ball(180.0, 208.0 + it * 0.001, 0.0, -430.0) },
        ).snapshot()
        val original = restored(start)
        val reversed = restored(start.copy(balls = start.balls.reversed()))
        for (engine in listOf(original, reversed)) {
            steps(engine, 1)
            assertEquals(999L, engine.totalHits)
            assertEquals(1, engine.blocks.single().hits)
            assertEquals(999, engine.balls.size)
            assertTrue(engine.balls.all { it.vy > 0.0 && hypot(it.vx, it.vy) == 430.0 })
            assertEquals(64, engine.impacts.size)
            assertEquals((1000 downTo 937).toList(), engine.impacts.map { it.hitsBefore })
            assertNotNull(GameEngine.restore(engine.snapshot()))
        }
        assertSameStateIgnoringBallOrder(original, reversed)
    }
}