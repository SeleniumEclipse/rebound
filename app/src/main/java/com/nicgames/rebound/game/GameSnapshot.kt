package com.nicgames.rebound.game

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Complete, versioned simulation state. Use GameEngine.restore rather than trusting saved input.
 * Every mutable element is copied both when saving and when restoring.
 */
@Serializable(with = GameSnapshotSerializer::class)
data class GameSnapshot(
    val version: Int = 2,
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

/**
 * Old JSON used encodeDefaults=false, so a missing version means version 1, NOT
 * the current constructor default. The wire default stays at 1; new version-2
 * snapshots consequently always write their version even with default JSON settings.
 */
internal object GameSnapshotSerializer : KSerializer<GameSnapshot> {
    override val descriptor: SerialDescriptor = SnapshotData.serializer().descriptor

    override fun serialize(encoder: Encoder, value: GameSnapshot) {
        encoder.encodeSerializableValue(SnapshotData.serializer(), SnapshotData(
            version = value.version, phase = value.phase, round = value.round,
            ballCount = value.ballCount, launchX = value.launchX, blocks = value.blocks,
            pickups = value.pickups, balls = value.balls, impacts = value.impacts,
            shotElapsed = value.shotElapsed, advanceProgress = value.advanceProgress,
            totalHits = value.totalHits, destroyedBlocks = value.destroyedBlocks,
            rngState = value.rngState, nextId = value.nextId, pendingLaunches = value.pendingLaunches,
            launchCountdown = value.launchCountdown, shotAngle = value.shotAngle,
            firstReturnX = value.firstReturnX, collectedBalls = value.collectedBalls,
            accumulator = value.accumulator,
        ))
    }

    override fun deserialize(decoder: Decoder): GameSnapshot {
        val data = decoder.decodeSerializableValue(SnapshotData.serializer())
        return GameSnapshot(
            version = data.version, phase = data.phase, round = data.round,
            ballCount = data.ballCount, launchX = data.launchX, blocks = data.blocks,
            pickups = data.pickups, balls = data.balls, impacts = data.impacts,
            shotElapsed = data.shotElapsed, advanceProgress = data.advanceProgress,
            totalHits = data.totalHits, destroyedBlocks = data.destroyedBlocks,
            rngState = data.rngState, nextId = data.nextId, pendingLaunches = data.pendingLaunches,
            launchCountdown = data.launchCountdown, shotAngle = data.shotAngle,
            firstReturnX = data.firstReturnX, collectedBalls = data.collectedBalls,
            accumulator = data.accumulator,
        )
    }

    @Serializable
    private data class SnapshotData(
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
}