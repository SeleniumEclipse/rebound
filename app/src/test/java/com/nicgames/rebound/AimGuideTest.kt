package com.nicgames.rebound

import com.nicgames.rebound.game.Board
import com.nicgames.rebound.game.Vec2
import com.nicgames.rebound.ui.AimGuide
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class AimGuideTest {
    private val start = Vec2(180.0, Board.LAUNCH_Y)
    @Test fun distantTargetIsNotRevealed() {
        val end = AimGuide.end(start, Vec2(180.0, 20.0))
        assertEquals(Board.HEIGHT / 3, start.y - end.y, 1e-10)
        assertEquals(start.x, end.x, 0.0)
    }
    @Test fun diagonalHintHasTheSameLengthAndDirection() {
        val end = AimGuide.end(start, Vec2(300.0, 100.0))
        assertEquals(AimGuide.MAX_LENGTH, hypot(end.x - start.x, end.y - start.y), 1e-10)
        assertEquals(120.0 / -376.0, (end.x - start.x) / (end.y - start.y), 1e-10)
    }
    @Test fun nearbyCollisionStopsHintBeforeItPassesThroughABlock() {
        val hit = Vec2(200.0, 420.0)
        assertEquals(hit, AimGuide.end(start, hit))
    }
    @Test fun zeroLengthAndInvalidPathsAreHarmless() {
        assertEquals(start, AimGuide.end(start, start))
        assertEquals(start, AimGuide.end(start, Vec2(Double.NaN, 0.0)))
    }
}