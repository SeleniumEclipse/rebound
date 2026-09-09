package com.nicgames.rebound.game

import kotlinx.serialization.Serializable

object Board {
    const val WIDTH = 360.0
    const val HEIGHT = 504.0
    const val COLUMNS = 7
    const val BLOCK_LEFT = 20.0
    const val BLOCK_TOP = 20.0
    const val COLUMN_STEP = 46.0
    const val BLOCK_SIZE = 42.0
    const val LEFT = BLOCK_LEFT
    const val RIGHT = BLOCK_LEFT + (COLUMNS - 1) * COLUMN_STEP + BLOCK_SIZE
    const val TOP = 8.0
    const val FLOOR = 482.0
    const val LAUNCH_Y = 476.0
    const val BALL_RADIUS = 4.0
    const val ROW_STEP = 47.0
}

@Serializable
enum class Phase { AIMING, FIRING, ADVANCING, GAME_OVER }

@Serializable
data class Vec2(val x: Double, val y: Double)

@Serializable
data class Block(val id: Long, val column: Int, var row: Int, var hits: Int) {
    val x: Double get() = Board.BLOCK_LEFT + column * Board.COLUMN_STEP
    val y: Double get() = Board.BLOCK_TOP + row * Board.ROW_STEP
}

@Serializable
data class Pickup(val id: Long, val column: Int, var row: Int) {
    val x: Double get() = Board.BLOCK_LEFT + Board.BLOCK_SIZE / 2 + column * Board.COLUMN_STEP
    val y: Double get() = Board.BLOCK_TOP + Board.BLOCK_SIZE / 2 + row * Board.ROW_STEP
}

@Serializable
data class Ball(var x: Double, var y: Double, var vx: Double, var vy: Double)

@Serializable
data class Impact(val x: Double, val y: Double, val hitsBefore: Int, var age: Double = 0.0)