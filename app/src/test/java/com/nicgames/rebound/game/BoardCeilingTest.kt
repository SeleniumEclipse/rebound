package com.nicgames.rebound.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class BoardCeilingTest {
    private val minX = Board.LEFT + Board.BALL_RADIUS
    private val maxX = Board.RIGHT - Board.BALL_RADIUS
    private val minY = Board.TOP + Board.BALL_RADIUS

    private fun assertSafeBall(engine: GameEngine, speed: Double) {
        val ball = engine.balls.single()
        assertTrue(ball.x.isFinite() && ball.y.isFinite())
        assertTrue(ball.x in minX..maxX)
        assertTrue(ball.y in minY..(Board.FLOOR - Board.BALL_RADIUS))
        assertEquals(speed, hypot(ball.vx, ball.vy), 1e-7)
        assertTrue(engine.blocks.none { Collision.overlapsBox(ball, it) })
        assertEquals(engine.snapshot(), restored(engine.snapshot()).snapshot())
    }

    @Test fun ceilingMatchesRowZeroWithoutChangingInteriorSpacingOrFloor() {
        assertEquals(20.0, Board.TOP, 0.0)
        assertEquals(Board.BLOCK_TOP, Board.TOP, 0.0)
        assertEquals(24.0, minY, 0.0)
        assertEquals(24.0, minX, 0.0)
        assertEquals(334.0, maxX, 0.0)
        assertEquals(478.0, Board.FLOOR - Board.BALL_RADIUS, 0.0)
        assertEquals(476.0, Board.LAUNCH_Y, 0.0)
        for (column in 0 until Board.COLUMNS) {
            val topBlock = Block(1, column, 0, 10)
            assertEquals(Board.TOP, topBlock.y, 0.0)
            assertEquals(0.0, topBlock.y - Board.TOP, 0.0)
            assertEquals(42.0, Board.BLOCK_SIZE, 0.0)
            assertEquals(5.0, Block(2, column, 1, 10).y - topBlock.y - Board.BLOCK_SIZE, 0.0)
            if (column > 0) {
                assertEquals(4.0, topBlock.x - Block(2, column - 1, 0, 10).x - Board.BLOCK_SIZE, 0.0)
            }
        }
    }

    @Test fun shotsThroughEmptyTopCellsBounceAtTheNewCeilingWithoutEnteringATopCorridor() {
        // Four blocks is a valid generated row, not an impossible full-row fixture.
        val blocks = listOf(1, 2, 4, 5).map { Block(it + 1L, it, 0, 10) }
        for (launchX in listOf(minX + 0.01, 179.0, maxX - 0.01)) {
            val engine = fixture(blocks = blocks, launchX = launchX)
            val before = engine.snapshot()
            val aim = engine.aimPath(-PI / 2).last()
            assertEquals(launchX, aim.x, 1e-9)
            assertEquals(minY, aim.y, 1e-9)
            assertEquals(before, engine.snapshot())
            assertTrue(engine.fire(-PI / 2))
            var frames = 0
            var sawCeilingBounce = false
            while (engine.phase == Phase.FIRING) {
                assertTrue("An empty-column shot must return naturally", frames++ < 300)
                val previousVy = engine.balls.single().vy
                steps(engine, 1)
                assertEquals(0L, engine.totalHits)
                if (engine.balls.isNotEmpty()) {
                    assertSafeBall(engine, 430.0)
                    if (previousVy < 0.0 && engine.balls.single().vy > 0.0) {
                        sawCeilingBounce = true
                        assertTrue(engine.balls.single().y <= minY + 430.0 * STEP + 1e-5)
                    }
                }
            }
            assertTrue(sawCeilingBounce)
            assertEquals(Phase.ADVANCING, engine.phase)
            assertEquals(blocks, engine.blocks)
            assertEquals(1, engine.ballCount)
            assertEquals(launchX, engine.launchX, 1e-9)
        }
    }

    @Test fun upwardShotsIntoUnbrokenRowZeroBlocksHitOnlyTheirUndersides() {
        for (column in 0 until Board.COLUMNS) {
            for (offset in listOf(-0.02, 0.0, 0.02)) {
                val block = Block(1, column, 0, 10)
                val engine = fixture(blocks = listOf(block), launchX = block.x + Board.BLOCK_SIZE / 2)
                val angle = -PI / 2 + offset
                val aim = engine.aimPath(angle).last()
                val undersideY = block.y + Board.BLOCK_SIZE + Board.BALL_RADIUS
                assertEquals(undersideY, aim.y, 1e-9)
                assertTrue(aim.x in block.x..(block.x + Board.BLOCK_SIZE))
                assertTrue(engine.fire(angle))
                var frames = 0
                var sawUndersideHit = false
                while (engine.phase == Phase.FIRING) {
                    assertTrue("A top-row underside shot must return promptly", frames++ < 260)
                    steps(engine, 1)
                    assertTrue("No repeated hits from a ceiling corridor", engine.totalHits <= 1L)
                    if (engine.balls.isNotEmpty()) {
                        assertSafeBall(engine, 430.0)
                        assertTrue(engine.balls.single().y >= undersideY - 1e-7)
                    }
                    if (!sawUndersideHit && engine.totalHits == 1L) {
                        sawUndersideHit = true
                        val impact = engine.impacts.single()
                        assertEquals(block.y + Board.BLOCK_SIZE, impact.y, 1e-9)
                        assertEquals(10, impact.hitsBefore)
                        assertTrue(impact.x in block.x..(block.x + Board.BLOCK_SIZE))
                        assertTrue(engine.balls.single().vy > 0.0)
                    }
                }
                assertTrue(sawUndersideHit)
                assertEquals(Phase.ADVANCING, engine.phase)
                assertEquals(1L, engine.totalHits)
                assertEquals(9, engine.blocks.single().hits)
                assertEquals(0, engine.destroyedBlocks)
                assertEquals(0, engine.blocks.single().row)
            }
        }
    }

    @Test fun rowZeroNearSimultaneousSideWallAndUndersideContactsStayStable() {
        for (right in listOf(false, true)) {
            for ((horizontal, vertical) in listOf(400.0 to 100.0, 300.0 to 300.0, 100.0 to 400.0)) {
                for (timeOffset in listOf(-0.000001, 0.0, 0.000001)) {
                    val block = Block(1, if (right) Board.COLUMNS - 1 else 0, 0, 10)
                    val vx = if (right) horizontal else -horizontal
                    val vy = -vertical
                    val ball = Ball(
                        (if (right) maxX else minX) - vx * 0.005,
                        block.y + Board.BLOCK_SIZE + Board.BALL_RADIUS - vy * (0.005 + timeOffset), vx, vy,
                    )
                    // Start below the block; restore verifies the initial position is legal.
                    val engine = fixture(blocks = listOf(block), balls = listOf(ball))
                    assertSafeBall(engine, hypot(vx, vy))
                    steps(engine, 1)
                    assertEquals(1L, engine.totalHits)
                    assertEquals(-vx, engine.balls.single().vx, 1e-9)
                    assertEquals(-vy, engine.balls.single().vy, 1e-9)
                    assertEquals(block.y + Board.BLOCK_SIZE, engine.impacts.single().y, 1e-9)
                    repeat(30) {
                        assertSafeBall(engine, hypot(vx, vy))
                        steps(engine, 1)
                        assertEquals(1L, engine.totalHits)
                        assertEquals(9, engine.blocks.single().hits)
                    }
                    assertSafeBall(engine, hypot(vx, vy))
                }
            }
        }
    }

    @Test fun ceilingAndExposedRowZeroSideReflectTogetherWithoutAHitStorm() {
        for (right in listOf(false, true)) {
            for ((horizontal, vertical) in listOf(400.0 to 100.0, 300.0 to 300.0, 100.0 to 400.0)) {
                for (timeOffset in listOf(-0.000001, 0.0, 0.000001)) {
                    val block = Block(1, 3, 0, 10)
                    val vx = if (right) -horizontal else horizontal
                    val vy = -vertical
                    val faceX = if (right) block.x + Board.BLOCK_SIZE else block.x
                    val touchX = faceX + if (right) Board.BALL_RADIUS else -Board.BALL_RADIUS
                    // The ball approaches from a free cell beside row zero, never from above it.
                    val ball = Ball(touchX - vx * 0.005, minY - vy * (0.005 + timeOffset), vx, vy)
                    val engine = fixture(blocks = listOf(block), balls = listOf(ball))
                    assertSafeBall(engine, hypot(vx, vy))
                    steps(engine, 1)
                    assertEquals(1L, engine.totalHits)
                    assertEquals(-vx, engine.balls.single().vx, 1e-9)
                    assertEquals(-vy, engine.balls.single().vy, 1e-9)
                    assertEquals(faceX, engine.impacts.single().x, 1e-9)
                    assertTrue(engine.impacts.single().y >= Board.TOP)
                    repeat(30) {
                        assertSafeBall(engine, hypot(vx, vy))
                        steps(engine, 1)
                        assertEquals(1L, engine.totalHits)
                        assertEquals(9, engine.blocks.single().hits)
                    }
                    assertSafeBall(engine, hypot(vx, vy))
                }
            }
        }
    }
}