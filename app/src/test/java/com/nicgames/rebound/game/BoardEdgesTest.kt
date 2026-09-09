package com.nicgames.rebound.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class BoardEdgesTest {
    private val minX = Board.LEFT + Board.BALL_RADIUS
    private val maxX = Board.RIGHT - Board.BALL_RADIUS

    private fun assertSafeBall(engine: GameEngine, speed: Double) {
        val ball = engine.balls.single()
        assertTrue(ball.x in minX..maxX)
        assertTrue(ball.y in (Board.TOP + Board.BALL_RADIUS)..(Board.FLOOR - Board.BALL_RADIUS))
        assertEquals(speed, hypot(ball.vx, ball.vy), 1e-7)
        assertTrue(engine.blocks.none { Collision.overlapsBox(ball, it) })
        assertEquals(engine.snapshot(), restored(engine.snapshot()).snapshot())
    }

    @Test fun sideWallsAreFlushWithTheUnchangedSevenColumnGrid() {
        assertEquals(7, Board.COLUMNS)
        assertEquals(20.0, Board.BLOCK_LEFT, 0.0)
        assertEquals(46.0, Board.COLUMN_STEP, 0.0)
        assertEquals(42.0, Board.BLOCK_SIZE, 0.0)
        assertEquals(20.0, Board.LEFT, 0.0)
        assertEquals(338.0, Board.RIGHT, 0.0)
        assertEquals(24.0, minX, 0.0)
        assertEquals(334.0, maxX, 0.0)
        assertEquals(20.0, Board.TOP, 0.0)
        assertEquals(482.0, Board.FLOOR, 0.0)
        assertEquals(476.0, Board.LAUNCH_Y, 0.0)
        for (row in 0..9) {
            assertEquals(Board.LEFT, Block(1, 0, row, 1).x, 0.0)
            assertEquals(Board.RIGHT, Block(2, Board.COLUMNS - 1, row, 1).x + Board.BLOCK_SIZE, 0.0)
            for (column in 0 until Board.COLUMNS) {
                val block = Block(1, column, row, 1)
                val pickup = Pickup(2, column, row)
                assertEquals(20.0 + 46.0 * column, block.x, 0.0)
                assertEquals(20.0 + 47.0 * row, block.y, 0.0)
                assertEquals(block.x + Board.BLOCK_SIZE / 2, pickup.x, 0.0)
                assertEquals(block.y + Board.BLOCK_SIZE / 2, pickup.y, 0.0)
                if (column > 0) {
                    val previous = Block(3, column - 1, row, 1)
                    assertEquals(4.0, block.x - previous.x - Board.BLOCK_SIZE, 0.0)
                }
            }
        }
        assertEquals(0.0, Block(1, 0, 0, 1).y - Board.TOP, 0.0)
    }

    @Test fun aimUsesTightSideLimitsAndSeesTheUndersideOfBothEdgeBlocks() {
        for (right in listOf(false, true)) {
            val edge = if (right) maxX else minX
            val origin = edge + if (right) -0.01 else 0.01
            val block = Block(1, if (right) Board.COLUMNS - 1 else 0, 6, 10)
            val engine = fixture(blocks = listOf(block), launchX = origin)
            val before = engine.snapshot()
            val end = engine.aimPath(-PI / 2).last()
            assertEquals(origin, end.x, 1e-9)
            assertEquals(block.y + Board.BLOCK_SIZE + Board.BALL_RADIUS, end.y, 1e-9)
            assertEquals(before, engine.snapshot())
            val wall = fixture().aimPath(if (right) -0.12 else -PI + 0.12).last()
            assertEquals(edge, wall.x, 1e-9)
        }
    }

    @Test fun nearWallUpwardShotsHitTheUndersideOnceInsteadOfEnteringASideLane() {
        for (right in listOf(false, true)) {
            val inward = if (right) -1.0 else 1.0
            for (offset in listOf(-0.08, -0.02, 0.0, 0.02, 0.08)) {
                for (wallDistance in listOf(0.0, 0.01)) {
                    val block = Block(1, if (right) Board.COLUMNS - 1 else 0, 6, 10)
                    val engine = fixture(
                        blocks = listOf(block),
                        launchX = (if (right) maxX else minX) + inward * wallDistance,
                    )
                    assertTrue(engine.fire(-PI / 2 + inward * offset))
                    var frames = 0
                    var sawUndersideHit = false
                    while (engine.phase == Phase.FIRING) {
                        assertTrue("An edge shot must bounce down and return promptly", frames++ < 100)
                        steps(engine, 1)
                        assertTrue("No side-lane hit storm", engine.totalHits <= 1L)
                        if (engine.balls.isNotEmpty()) assertSafeBall(engine, 430.0)
                        if (!sawUndersideHit && engine.totalHits == 1L) {
                            sawUndersideHit = true
                            val impact = engine.impacts.single()
                            assertEquals(block.y + Board.BLOCK_SIZE, impact.y, 1e-9)
                            assertTrue(impact.x in block.x..(block.x + Board.BLOCK_SIZE))
                            assertTrue(engine.balls.single().vy > 0.0)
                        }
                    }
                    assertTrue(sawUndersideHit)
                    assertEquals(Phase.ADVANCING, engine.phase)
                    assertEquals(1L, engine.totalHits)
                    assertEquals(9, engine.blocks.single().hits)
                    assertEquals(1, engine.ballCount)
                    assertEquals(0, engine.destroyedBlocks)
                    assertTrue(engine.launchX in minX..maxX)
                    assertNotNull(GameEngine.restore(engine.snapshot()))
                }
            }
        }
    }

    @Test fun simultaneousAndNearSimultaneousWallBlockContactsReflectWithoutRepeatedDamage() {
        for (right in listOf(false, true)) {
            val inward = if (right) -1.0 else 1.0
            for (underside in listOf(false, true)) {
                for ((horizontal, vertical) in listOf(400.0 to 100.0, 300.0 to 300.0, 100.0 to 400.0)) {
                    for (timeOffset in listOf(-0.000001, 0.0, 0.000001)) {
                        val block = Block(1, if (right) Board.COLUMNS - 1 else 0, 3, 10)
                        val vx = -inward * horizontal
                        val vy = if (underside) -vertical else vertical
                        val touchY = if (underside) block.y + Board.BLOCK_SIZE + Board.BALL_RADIUS
                            else block.y - Board.BALL_RADIUS
                        val ball = Ball(
                            (if (right) maxX else minX) - vx * 0.005,
                            touchY - vy * (0.005 + timeOffset), vx, vy,
                        )
                        val speed = hypot(vx, vy)
                        val engine = fixture(blocks = listOf(block), balls = listOf(ball))
                        steps(engine, 1)
                        assertEquals(1L, engine.totalHits)
                        assertEquals(-vx, engine.balls.single().vx, 1e-9)
                        assertEquals(-vy, engine.balls.single().vy, 1e-9)
                        assertSafeBall(engine, speed)
                        repeat(30) {
                            steps(engine, 1)
                            assertEquals(1L, engine.totalHits)
                            assertEquals(9, engine.blocks.single().hits)
                            assertSafeBall(engine, speed)
                        }
                    }
                }
            }
        }
    }

    @Test fun exposedInnerCornersOfBothEdgeBlocksStillUseCircularReflection() {
        for (right in listOf(false, true)) {
            val block = Block(1, if (right) Board.COLUMNS - 1 else 0, 3, 10)
            val ball = Ball(
                if (right) block.x - 11.2 else block.x + Board.BLOCK_SIZE + 11.2,
                block.y - 4.4, if (right) 400.0 else -400.0, 100.0,
            )
            val engine = fixture(blocks = listOf(block), balls = listOf(ball))
            steps(engine, 3)
            assertEquals(1L, engine.totalHits)
            assertEquals(if (right) -208.0 else 208.0, engine.balls.single().vx, 1e-7)
            assertEquals(-356.0, engine.balls.single().vy, 1e-7)
            repeat(30) {
                assertSafeBall(engine, hypot(400.0, 100.0))
                steps(engine, 1)
                assertEquals(1L, engine.totalHits)
                assertEquals(9, engine.blocks.single().hits)
            }
        }
    }
}