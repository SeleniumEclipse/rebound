package com.nicgames.rebound.ui

import com.nicgames.rebound.game.Board
import com.nicgames.rebound.game.Vec2
import kotlin.math.hypot

object AimGuide {
    const val MAX_LENGTH = Board.HEIGHT / 3.0

    /** Direction hint, not a distant hit prediction. Stop sooner at a nearby wall/block. */
    fun end(start: Vec2, firstContact: Vec2): Vec2 {
        val dx = firstContact.x - start.x
        val dy = firstContact.y - start.y
        val length = hypot(dx, dy)
        if (!length.isFinite() || length == 0.0) return start
        val fraction = (MAX_LENGTH / length).coerceAtMost(1.0)
        return Vec2(start.x + dx * fraction, start.y + dy * fraction)
    }
}