package com.nicgames.rebound.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import kotlin.math.PI
import kotlin.math.ceil

internal const val STEP = 1.0 / 120.0

internal fun volleyDeadlineForTest(ballCount: Int): Double = (ballCount - 1) * 0.045 + 24.0

internal fun turnFrameLimit(ballCount: Int): Int =
    ceil((volleyDeadlineForTest(ballCount) + 1.0) / 0.25).toInt()

internal fun emptySnapshot(seed: Long = 123L): GameSnapshot = GameEngine(seed).snapshot().copy(
    blocks = emptyList(), pickups = emptyList(), nextId = 1_000L,
)

internal fun fixture(
    blocks: List<Block> = emptyList(),
    pickups: List<Pickup> = emptyList(),
    balls: List<Ball> = emptyList(),
    ballCount: Int = balls.size.coerceAtLeast(1),
    launchX: Double = 180.0,
): GameEngine = restored(emptySnapshot().copy(
    phase = if (balls.isEmpty()) Phase.AIMING else Phase.FIRING,
    blocks = blocks, pickups = pickups, balls = balls, ballCount = ballCount,
    launchX = launchX, shotAngle = -PI / 2,
    firstReturnX = if (balls.isNotEmpty() && balls.size < ballCount) launchX else null,
))

internal fun sharedBlockRaceSnapshot(reverseBalls: Boolean = false): GameSnapshot {
    val balls = listOf(Ball(170.0, 351.0, 0.0, -430.0), Ball(190.0, 349.0, 0.0, -430.0))
    return emptySnapshot().copy(
        phase = Phase.FIRING, ballCount = 2, blocks = listOf(Block(1, 3, 6, 1)),
        balls = if (reverseBalls) balls.reversed() else balls,
    )
}

internal fun restored(snapshot: GameSnapshot): GameEngine {
    val engine = GameEngine.restore(snapshot)
    assertNotNull("The test fixture must itself be a valid save", engine)
    return requireNotNull(engine)
}

internal fun steps(engine: GameEngine, count: Int) {
    repeat(count) { engine.tick(STEP) }
}

internal fun finishTurn(engine: GameEngine) {
    repeat(turnFrameLimit(engine.ballCount)) {
        if (engine.phase == Phase.AIMING || engine.phase == Phase.GAME_OVER) return
        engine.tick(0.25)
    }
    throw AssertionError("A volley must finish within its launch-queue duration plus 25 simulated seconds")
}

internal fun assertSameState(expected: GameEngine, actual: GameEngine) {
    val a = expected.snapshot()
    val b = actual.snapshot()
    assertEquals(a.accumulator, b.accumulator, 1e-10)
    assertEquals(a.copy(accumulator = b.accumulator), b)
}

internal fun assertSameStateIgnoringBallOrder(expected: GameEngine, actual: GameEngine) {
    val a = expected.snapshot()
    val b = actual.snapshot()
    val order = compareBy<Ball>({ it.x }, { it.y }, { it.vx }, { it.vy })
    assertEquals(a.accumulator, b.accumulator, 1e-10)
    assertEquals(
        a.copy(balls = a.balls.sortedWith(order), accumulator = b.accumulator),
        b.copy(balls = b.balls.sortedWith(order)),
    )
}