package com.nicgames.rebound.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Continuous circle geometry: a rounded rectangle, NOT a rectangular expanded AABB. */
internal object Collision {
    const val TIME_EPS = 1e-10
    const val SPACE_EPS = 1e-7
    private const val APPROACH_EPS = 1e-9

    data class Contact(val time: Double, val nx: Double, val ny: Double)

    /** Reused by the engine so successful sweep candidates do not allocate Contact objects. */
    class ContactBuffer {
        var time = Double.POSITIVE_INFINITY
        var nx = 0.0
        var ny = 0.0
    }

    fun circleBox(
        x: Double, y: Double, vx: Double, vy: Double, radius: Double,
        left: Double, top: Double, size: Double, duration: Double,
    ): Contact? {
        val result = ContactBuffer()
        return if (circleBox(x, y, vx, vy, radius, left, top, size, duration, result)) {
            Contact(result.time, result.nx, result.ny)
        } else null
    }

    fun circleBox(
        x: Double, y: Double, vx: Double, vy: Double, radius: Double,
        left: Double, top: Double, size: Double, duration: Double, result: ContactBuffer,
    ): Boolean {
        result.time = Double.POSITIVE_INFINITY
        result.nx = 0.0
        result.ny = 0.0
        val right = left + size
        val bottom = top + size

        // Cheap swept bounding-box rejection; the actual narrow-phase below is rounded.
        val endX = x + vx * duration
        val endY = y + vy * duration
        if (max(x, endX) < left - radius - SPACE_EPS ||
            min(x, endX) > right + radius + SPACE_EPS ||
            max(y, endY) < top - radius - SPACE_EPS ||
            min(y, endY) > bottom + radius + SPACE_EPS
        ) return false

        fun consider(t: Double, nx: Double, ny: Double) {
            if (!t.isFinite() || t < -TIME_EPS || t > duration + TIME_EPS) return
            if (vx * nx + vy * ny >= -APPROACH_EPS) return
            val time = t.coerceIn(0.0, duration)
            if (time < result.time) {
                result.time = time
                result.nx = nx
                result.ny = ny
            }
        }

        if (vx > APPROACH_EPS) {
            val t = (left - radius - x) / vx
            val atY = y + vy * t
            if (atY >= top - SPACE_EPS && atY <= bottom + SPACE_EPS) consider(t, -1.0, 0.0)
        } else if (vx < -APPROACH_EPS) {
            val t = (right + radius - x) / vx
            val atY = y + vy * t
            if (atY >= top - SPACE_EPS && atY <= bottom + SPACE_EPS) consider(t, 1.0, 0.0)
        }
        if (vy > APPROACH_EPS) {
            val t = (top - radius - y) / vy
            val atX = x + vx * t
            if (atX >= left - SPACE_EPS && atX <= right + SPACE_EPS) consider(t, 0.0, -1.0)
        } else if (vy < -APPROACH_EPS) {
            val t = (bottom + radius - y) / vy
            val atX = x + vx * t
            if (atX >= left - SPACE_EPS && atX <= right + SPACE_EPS) consider(t, 0.0, 1.0)
        }

        fun corner(cx: Double, cy: Double, sx: Int, sy: Int) {
            val t = circleEntryTime(x, y, vx, vy, cx, cy, radius, duration)
            if (!t.isFinite()) return
            val dx = x + vx * t - cx
            val dy = y + vy * t - cy
            // A corner circle only owns its outward quarter, not the entire circle.
            if (dx * sx < -SPACE_EPS || dy * sy < -SPACE_EPS) return
            val length = hypot(dx, dy)
            if (length > SPACE_EPS) consider(t, dx / length, dy / length)
        }
        corner(left, top, -1, -1)
        corner(right, top, 1, -1)
        corner(left, bottom, -1, 1)
        corner(right, bottom, 1, 1)
        return result.time.isFinite()
    }

    /** First entry of a moving point into a circle, also used for swept pickup collection. */
    fun circleEntry(
        x: Double, y: Double, vx: Double, vy: Double,
        cx: Double, cy: Double, radius: Double, duration: Double,
    ): Double? {
        val time = circleEntryTime(x, y, vx, vy, cx, cy, radius, duration)
        return if (time.isFinite()) time else null
    }

    /** Allocation-free sweep for the simulation; infinity means no entry. */
    fun circleEntryTime(
        x: Double, y: Double, vx: Double, vy: Double,
        cx: Double, cy: Double, radius: Double, duration: Double,
    ): Double {
        val dx = x - cx
        val dy = y - cy
        val a = vx * vx + vy * vy
        val b = dx * vx + dy * vy
        val c = dx * dx + dy * dy - radius * radius
        if (c <= 0.0) return 0.0
        if (a <= 0.0 || b >= 0.0) return Double.POSITIVE_INFINITY
        val discriminant = b * b - a * c
        // Relative tolerance handles round-off for a mathematically tangent ray.
        val tolerance = 1e-14 * max(1.0, b * b + abs(a * c))
        if (discriminant < -tolerance) return Double.POSITIVE_INFINITY
        // This form of the small quadratic root avoids catastrophic cancellation.
        val denominator = -b + sqrt(max(0.0, discriminant))
        if (denominator <= 0.0) return Double.POSITIVE_INFINITY
        val time = c / denominator
        return if (time >= -TIME_EPS && time <= duration + TIME_EPS) {
            time.coerceIn(0.0, duration)
        } else Double.POSITIVE_INFINITY
    }

    fun reflect(ball: Ball, nx: Double, ny: Double) {
        val normalLength = hypot(nx, ny)
        val speed = hypot(ball.vx, ball.vy)
        if (normalLength == 0.0 || speed == 0.0) return
        val ux = nx / normalLength
        val uy = ny / normalLength
        val projection = ball.vx * ux + ball.vy * uy
        val rx = ball.vx - 2.0 * projection * ux
        val ry = ball.vy - 2.0 * projection * uy
        val reflectedSpeed = hypot(rx, ry)
        ball.vx = rx * (speed / reflectedSpeed)
        ball.vy = ry * (speed / reflectedSpeed)
    }

    fun overlapsBox(ball: Ball, block: Block): Boolean {
        val closestX = ball.x.coerceIn(block.x, block.x + Board.BLOCK_SIZE)
        val closestY = ball.y.coerceIn(block.y, block.y + Board.BLOCK_SIZE)
        return hypot(ball.x - closestX, ball.y - closestY) < Board.BALL_RADIUS - SPACE_EPS
    }
}