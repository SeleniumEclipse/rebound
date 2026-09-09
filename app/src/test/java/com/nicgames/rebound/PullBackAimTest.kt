package com.nicgames.rebound

import com.nicgames.rebound.ui.PullBackAim
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class PullBackAimTest {
    @Test fun downwardPullShootsStraightUp() { assertEquals(-PI / 2, PullBackAim.angle(0.0, 100.0)!!, 1e-12) }
    @Test fun downLeftPullShootsUpRight() { assertEquals(-PI / 4, PullBackAim.angle(-100.0, 100.0)!!, 1e-12) }
    @Test fun downRightPullShootsUpLeft() { assertEquals(-3 * PI / 4, PullBackAim.angle(100.0, 100.0)!!, 1e-12) }
    @Test fun dragLengthDoesNotChangeTheAngle() { assertEquals(PullBackAim.angle(20.0, 40.0), PullBackAim.angle(100.0, 200.0)) }
    @Test fun tapAndSmallMovementDoNotShoot() { assertNull(PullBackAim.angle(0.0, 0.0)); assertNull(PullBackAim.angle(3.0, 4.0)) }
    @Test fun forwardPullAndHorizontalDragCancel() { assertNull(PullBackAim.angle(20.0, -40.0)); assertNull(PullBackAim.angle(100.0, 0.0)) }
    @Test fun shallowShotStaysInsidePlayableRange() { assertEquals(-.13, PullBackAim.angle(-100.0, .1)!!, 1e-12); assertEquals(-PI + .13, PullBackAim.angle(100.0, .1)!!, 1e-12) }
    @Test fun invalidInputDoesNotShoot() { assertNull(PullBackAim.angle(Double.NaN, 10.0)); assertNull(PullBackAim.angle(0.0, Double.POSITIVE_INFINITY)) }
}