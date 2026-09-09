package com.nicgames.rebound.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class CollisionTest {
    private fun hit(ball: Ball, duration: Double = 1.0): Collision.Contact? = Collision.circleBox(
        ball.x, ball.y, ball.vx, ball.vy, 4.0, 100.0, 100.0, 42.0, duration,
    )

    @Test fun leftFaceReflectsOnlyHorizontalVelocity() {
        val ball = Ball(80.0, 120.0, 430.0, 0.0)
        val contact = requireNotNull(hit(ball))
        assertEquals(16.0 / 430.0, contact.time, 1e-12)
        Collision.reflect(ball, contact.nx, contact.ny)
        assertEquals(-430.0, ball.vx, 1e-10)
        assertEquals(0.0, ball.vy, 1e-10)
    }

    @Test fun rightFaceReflectsOnlyHorizontalVelocity() {
        val ball = Ball(164.0, 120.0, -430.0, 0.0)
        val contact = requireNotNull(hit(ball))
        assertEquals(18.0 / 430.0, contact.time, 1e-12)
        assertEquals(1.0, contact.nx, 1e-12)
        Collision.reflect(ball, contact.nx, contact.ny)
        assertEquals(430.0, ball.vx, 1e-10)
    }

    @Test fun topFaceReflectsOnlyVerticalVelocity() {
        val ball = Ball(120.0, 80.0, 0.0, 430.0)
        val contact = requireNotNull(hit(ball))
        assertEquals(16.0 / 430.0, contact.time, 1e-12)
        Collision.reflect(ball, contact.nx, contact.ny)
        assertEquals(0.0, ball.vx, 1e-10)
        assertEquals(-430.0, ball.vy, 1e-10)
    }

    @Test fun bottomFaceReflectsOnlyVerticalVelocity() {
        val ball = Ball(120.0, 164.0, 0.0, -430.0)
        val contact = requireNotNull(hit(ball))
        assertEquals(18.0 / 430.0, contact.time, 1e-12)
        Collision.reflect(ball, contact.nx, contact.ny)
        assertEquals(430.0, ball.vy, 1e-10)
    }

    @Test fun obliqueCornerUsesCircularNormalNotTwoAxisFlips() {
        val ball = Ball(88.8, 95.6, 400.0, 100.0)
        val speed = hypot(ball.vx, ball.vy)
        val contact = requireNotNull(hit(ball))
        assertEquals(0.02, contact.time, 1e-10)
        assertEquals(-0.8, contact.nx, 1e-10)
        assertEquals(-0.6, contact.ny, 1e-10)
        Collision.reflect(ball, contact.nx, contact.ny)
        assertEquals(-208.0, ball.vx, 1e-8)
        assertEquals(-356.0, ball.vy, 1e-8)
        assertEquals(speed, hypot(ball.vx, ball.vy), 1e-10)
    }

    @Test fun exactDiagonalCornerHasUnitNormal() {
        val ball = Ball(80.0, 80.0, 300.0, 300.0)
        val contact = requireNotNull(hit(ball))
        assertEquals((20.0 - 4.0 / sqrt(2.0)) / 300.0, contact.time, 1e-10)
        assertEquals(1.0, hypot(contact.nx, contact.ny), 1e-12)
        Collision.reflect(ball, contact.nx, contact.ny)
        assertEquals(-300.0, ball.vx, 1e-9)
        assertEquals(-300.0, ball.vy, 1e-9)
    }

    @Test fun expandedAabbCornerIsNotFalselySolid() {
        // This segment enters the expanded box but stops before the actual rounded corner.
        assertNull(hit(Ball(95.9, 96.2, 430.0, 0.0), 0.004))
    }

    @Test fun tangentDoesNotDamageOrReverseABall() {
        assertNull(hit(Ball(80.0, 96.0, 430.0, 0.0)))
    }

    @Test fun touchingAndMovingAwayDoesNotCreateAnotherContact() {
        assertNull(hit(Ball(96.0, 120.0, -430.0, 0.0), 0.01))
    }

    @Test fun highSpeedSweepDoesNotTunnelThroughAWholeBlock() {
        val ball = Ball(20.0, 120.0, 10_000.0, 0.0)
        val contact = requireNotNull(hit(ball, 0.025))
        assertEquals(0.0076, contact.time, 1e-12)
        Collision.reflect(ball, contact.nx, contact.ny)
        assertEquals(-10_000.0, ball.vx, 1e-8)
    }

    @Test fun sweptPickupFindsEntryEvenWhenBothSegmentEndsAreOutside() {
        val time = Collision.circleEntry(20.0, 100.0, 10_000.0, 0.0, 100.0, 100.0, 13.0, 0.02)
        assertNotNull(time)
        assertEquals(0.0067, requireNotNull(time), 1e-12)
    }

    @Test fun sweptPickupDoesNotCollectANearMiss() {
        assertNull(Collision.circleEntry(20.0, 114.0, 10_000.0, 0.0, 100.0, 100.0, 13.0, 0.02))
    }

    @Test fun pickupAlreadyOverlappingIsCollectedAtStart() {
        assertEquals(0.0, requireNotNull(Collision.circleEntry(
            100.0, 100.0, 430.0, 0.0, 100.0, 100.0, 13.0, 0.01,
        )), 0.0)
    }

    @Test fun repeatedReflectionsPreserveSpeed() {
        val ball = Ball(0.0, 0.0, 317.0, -289.0)
        val speed = hypot(ball.vx, ball.vy)
        repeat(10_000) {
            Collision.reflect(ball, 0.8, 0.6)
            Collision.reflect(ball, 0.0, 1.0)
        }
        assertEquals(speed, hypot(ball.vx, ball.vy), 1e-7)
    }

    @Test fun reusableContactBufferUpdatesNormalsAndClearsBothKindsOfMiss() {
        val result = Collision.ContactBuffer()
        assertTrue(Collision.circleBox(
            88.8, 95.6, 400.0, 100.0, 4.0, 100.0, 100.0, 42.0, 1.0, result,
        ))
        assertEquals(0.02, result.time, 1e-10)
        assertEquals(-0.8, result.nx, 1e-10)
        assertEquals(-0.6, result.ny, 1e-10)
        // Broad-phase rejection must not leave the previous successful hit in the buffer.
        assertFalse(Collision.circleBox(
            20.0, 20.0, 430.0, 0.0, 4.0, 100.0, 100.0, 42.0, 0.01, result,
        ))
        assertEquals(Double.POSITIVE_INFINITY, result.time, 0.0)
        assertEquals(0.0, result.nx, 0.0)
        assertEquals(0.0, result.ny, 0.0)
        assertTrue(Collision.circleBox(
            120.0, 164.0, 0.0, -430.0, 4.0, 100.0, 100.0, 42.0, 1.0, result,
        ))
        assertEquals(18.0 / 430.0, result.time, 1e-12)
        assertEquals(0.0, result.nx, 0.0)
        assertEquals(1.0, result.ny, 0.0)
        // A swept AABB overlap can still miss the actual rounded corner.
        assertFalse(Collision.circleBox(
            95.9, 96.2, 430.0, 0.0, 4.0, 100.0, 100.0, 42.0, 0.004, result,
        ))
        assertEquals(Double.POSITIVE_INFINITY, result.time, 0.0)
        assertEquals(0.0, result.nx, 0.0)
        assertEquals(0.0, result.ny, 0.0)
    }
}