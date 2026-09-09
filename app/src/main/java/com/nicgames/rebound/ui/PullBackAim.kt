package com.nicgames.rebound.ui

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/** Gesture displacement in board units; the shot travels opposite the pull.
 * Anchoring to the initial touch allows aiming anywhere, with room below the finger. */
object PullBackAim {
    const val DEAD_ZONE = 8.0
    fun angle(dx: Double, dy: Double): Double? {
        if (!dx.isFinite() || !dy.isFinite() || dy <= 0.0 || hypot(dx, dy) < DEAD_ZONE) return null
        return atan2(-dy, -dx).coerceIn(-PI + .13, -.13)
    }
}