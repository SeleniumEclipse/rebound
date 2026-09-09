package com.nicgames.rebound.game

import kotlinx.serialization.Serializable

/**
 * Complete, versioned simulation state. Use GameEngine.restore rather than trusting saved input.
 * Every mutable element is copied both when saving and when restoring.
 */
@Serializable
data class GameSnapshot(
    val version: Int = 1,
    val phase: Phase,
    val round: Int,
    val ballCount: Int,
    val launchX: Double,
    val blocks: List<Block>,
    val pickups: List<Pickup>,
    val balls: List<Ball>,
    val impacts: List<Impact>,
    val shotElapsed: Double,
    val advanceProgress: Double,
    val totalHits: Long,
    val destroyedBlocks: Int,
    val rngState: Long,
    val nextId: Long,
    val pendingLaunches: Int,
    val launchCountdown: Double,
    val shotAngle: Double,
    val firstReturnX: Double?,
    val collectedBalls: Int,
    val accumulator: Double,
)