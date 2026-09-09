package com.nicgames.rebound.game

import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Offline, single-threaded simulation. All time values are simulated seconds. */
class GameEngine private constructor(seed: Long, initialize: Boolean) {
    constructor(seed: Long = System.currentTimeMillis()) : this(seed, true)

    var phase: Phase = Phase.AIMING
        private set
    var round: Int = 1
        private set
    var ballCount: Int = 1
        private set
    var launchX: Double = 180.0
        private set
    var shotElapsed: Double = 0.0
        private set
    var advanceProgress: Double = 0.0
        private set
    var totalHits: Long = 0
        private set
    var destroyedBlocks: Int = 0
        private set

    private val mutableBlocks = ArrayList<Block>()
    private val mutablePickups = ArrayList<Pickup>()
    private val mutableBalls = ArrayList<Ball>()
    private val mutableImpacts = ArrayList<Impact>(MAX_IMPACTS)
    val blocks: List<Block> get() = mutableBlocks
    val pickups: List<Pickup> get() = mutablePickups
    val balls: List<Ball> get() = mutableBalls
    val impacts: List<Impact> get() = mutableImpacts

    private val random = SeededRandom(seed)
    private var nextId = 1L
    private var pendingLaunches = 0
    private var launchCountdown = 0.0
    private var shotAngle = -PI / 2.0
    private var firstReturnX: Double? = null
    /** Next turn's marker, available as soon as the first ball lands. The current
     * volley still emits its queued balls from the unchanged launchX. */
    val nextLaunchX: Double get() = firstReturnX ?: launchX
    private var collectedBalls = 0
    private var accumulator = 0.0

    // Reused scratch storage; it is not simulation state and never escapes this engine.
    private val flights = ArrayList<BallFlight>()
    private val collisionQueue = PriorityQueue<BallFlight>()
    private val blockContact = Collision.ContactBuffer()
    private val contactBlocks = ArrayList<Block>(4)
    private var contactTime = Double.POSITIVE_INFINITY
    private var contactNx = 0.0
    private var contactNy = 0.0
    private var contactFloor = false
    private var contactWallNx = 0.0
    private var contactWallNy = 0.0

    init {
        if (initialize) spawnRow()
    }

    /** Angles are in screen coordinates: negative y points upward. Invalid input is a no-op. */
    fun fire(angle: Double): Boolean {
        if (phase != Phase.AIMING || !validAngle(angle)) return false
        shotAngle = angle
        shotElapsed = 0.0
        advanceProgress = 0.0
        firstReturnX = null
        collectedBalls = 0
        pendingLaunches = ballCount - 1
        launchCountdown = if (pendingLaunches > 0) RELEASE_INTERVAL else 0.0
        phase = Phase.FIRING
        releaseBall()
        return true
    }

    /** Invalid/negative deltas are ignored; one call accepts at most 250 milliseconds. */
    fun tick(seconds: Double) {
        if (!seconds.isFinite() || seconds <= 0.0) return
        accumulator += seconds.coerceAtMost(0.25)
        while (accumulator + ACCUMULATOR_EPS >= FIXED_STEP) {
            val remainder = accumulator - FIXED_STEP
            // Canonicalize sub-picosecond arithmetic residue, not genuine partial steps.
            accumulator = if (remainder < ACCUMULATOR_EPS) 0.0 else remainder
            ageImpacts()
            when (phase) {
                Phase.FIRING -> stepVolley(FIXED_STEP)
                Phase.ADVANCING -> stepAdvance()
                Phase.AIMING, Phase.GAME_OVER -> Unit
            }
        }
    }

    /** Keeps earned damage/pickups, discards unlaunched balls and unfinished travel. */
    fun recall(): Boolean {
        if (phase != Phase.FIRING) return false
        finishVolley()
        return true
    }

    /** Radius-aware origin plus the first wall/block contact. No hidden state is changed. */
    fun aimPath(angle: Double): List<Vec2> {
        if (!validAngle(angle)) return emptyList()
        val origin = Vec2(launchX, Board.LAUNCH_Y)
        val vx = cos(angle)
        val vy = sin(angle)
        var distance = 10_000.0
        if (vx < 0.0) distance = min(distance, (MIN_X - origin.x) / vx)
        if (vx > 0.0) distance = min(distance, (MAX_X - origin.x) / vx)
        distance = min(distance, (MIN_Y - origin.y) / vy)
        for (block in mutableBlocks) {
            if (Collision.circleBox(
                origin.x, origin.y, vx, vy, Board.BALL_RADIUS,
                block.x, block.y, Board.BLOCK_SIZE, distance, blockContact,
            )) distance = min(distance, blockContact.time)
        }
        return listOf(origin, Vec2(origin.x + vx * distance, origin.y + vy * distance))
    }

    fun snapshot(): GameSnapshot = GameSnapshot(
        phase = phase, round = round, ballCount = ballCount, launchX = launchX,
        blocks = mutableBlocks.map { it.copy() }, pickups = mutablePickups.map { it.copy() },
        balls = mutableBalls.map { it.copy() }, impacts = mutableImpacts.map { it.copy() },
        shotElapsed = shotElapsed, advanceProgress = advanceProgress,
        totalHits = totalHits, destroyedBlocks = destroyedBlocks,
        rngState = random.state, nextId = nextId, pendingLaunches = pendingLaunches,
        launchCountdown = launchCountdown, shotAngle = shotAngle,
        firstReturnX = firstReturnX, collectedBalls = collectedBalls, accumulator = accumulator,
    )

    private fun releaseBall() {
        mutableBalls.add(Ball(launchX, Board.LAUNCH_Y, cos(shotAngle) * SPEED, sin(shotAngle) * SPEED))
    }

    private fun stepVolley(step: Double) {
        val deadline = volleyDeadline(ballCount)
        var remaining = step
        while (remaining > ACCUMULATOR_EPS && phase == Phase.FIRING) {
            val untilTimeout = (deadline - shotElapsed).coerceAtLeast(0.0)
            var slice = min(remaining, untilTimeout)
            if (pendingLaunches > 0) slice = min(slice, launchCountdown.coerceAtLeast(0.0))

            if (slice > 0.0) {
                moveBalls(slice)
                shotElapsed = min(deadline, shotElapsed + slice)
                if (pendingLaunches > 0) launchCountdown -= slice
                remaining -= slice
            }
            if (shotElapsed >= deadline - ACCUMULATOR_EPS) {
                shotElapsed = deadline
                finishVolley()
                return
            }
            if (pendingLaunches > 0 && launchCountdown <= ACCUMULATOR_EPS) {
                releaseBall()
                pendingLaunches--
                launchCountdown = if (pendingLaunches > 0) RELEASE_INTERVAL else 0.0
            }
            if (pendingLaunches == 0 && mutableBalls.isEmpty()) {
                finishVolley()
                return
            }
        }
    }

    /**
     * One board sweep per ball initially, then only per resolved/invalidated event.
     * No-contact slices are O(balls * blocks), not O(balls squared). Queue updates
     * cost O(log balls); a removed block can invalidate each waiting entry only once.
     */
    private fun moveBalls(duration: Double) {
        val count = mutableBalls.size
        collisionQueue.clear()
        while (flights.size < count) flights.add(BallFlight())
        for (index in 0 until count) {
            val flight = flights[index]
            flight.ball = mutableBalls[index]
            flight.elapsed = 0.0
            flight.contacts = 0
            flight.landed = false
            flight.stopped = false
            scheduleContact(flight, duration)
        }

        while (collisionQueue.isNotEmpty()) {
            val flight = collisionQueue.remove()
            // Geometry only disappears during a slice. A cached contact with a removed
            // block is a LOWER bound on this ball's next event, so re-querying here cannot
            // skip an earlier collision. Damage alone does not invalidate other contacts.
            if (flight.blocks.any { it.hits == 0 }) {
                scheduleContact(flight, duration)
                continue
            }

            advanceBall(flight, flight.time)
            val ball = flight.ball
            if (flight.floor) {
                ball.y = LANDING_Y
                flight.landed = true
                if (firstReturnX == null) firstReturnX = ball.x.coerceIn(MIN_X, MAX_X)
                continue
            }

            for (block in flight.blocks) {
                val before = block.hits
                block.hits--
                if (totalHits < Long.MAX_VALUE) totalHits++
                // No Impact allocation after the visual budget is full, even in a huge volley.
                if (mutableImpacts.size < MAX_IMPACTS) {
                    mutableImpacts.add(Impact(
                        ball.x.coerceIn(block.x, block.x + Board.BLOCK_SIZE),
                        ball.y.coerceIn(block.y, block.y + Board.BLOCK_SIZE), before,
                    ))
                }
                if (block.hits == 0) {
                    mutableBlocks.remove(block)
                    if (destroyedBlocks < Int.MAX_VALUE) destroyedBlocks++
                }
            }
            // Two simultaneous wall contacts mirror BOTH relevant components, rather than
            // reflecting against their bisector (which incorrectly swaps unequal speeds).
            if (flight.wallNx != 0.0) ball.vx = -ball.vx
            if (flight.wallNy != 0.0) ball.vy = -ball.vy
            if (flight.blocks.isNotEmpty() && ball.vx * flight.nx + ball.vy * flight.ny < 0.0) {
                Collision.reflect(ball, flight.nx, flight.ny)
            }
            val pushX = flight.nx + flight.wallNx
            val pushY = flight.ny + flight.wallNy
            val length = hypot(pushX, pushY)
            if (length <= 1e-12) {
                flight.stopped = true
                continue
            }
            val nx = pushX / length
            val ny = pushY / length
            // Separate contacts explicitly rather than integrating an unchecked leftover segment.
            ball.x = (ball.x + nx * PUSH_OUT).coerceIn(MIN_X, MAX_X)
            ball.y = (ball.y + ny * PUSH_OUT).coerceIn(MIN_Y, LANDING_Y)
            flight.contacts++
            flight.stopped = flight.contacts >= MAX_CONTACTS_PER_SLICE
            if (!flight.stopped) scheduleContact(flight, duration)
        }

        // All balls share the event clock, but positions are integrated lazily: unrelated
        // balls need not be moved/re-swept after every event. This is equivalent to moving
        // every ball to each earliest contact, without quadratic work in a crowded volley.
        // No ball/ball collisions exist; pickups have no owner and are removed only once.
        var writeIndex = 0
        for (index in 0 until count) {
            val flight = flights[index]
            if (flight.landed) continue
            if (!flight.stopped) advanceBall(flight, duration)
            mutableBalls[writeIndex++] = flight.ball
        }
        while (mutableBalls.size > writeIndex) mutableBalls.removeAt(mutableBalls.lastIndex)
        // Pathological contact clusters consume time in place, never tunnel through geometry.
        // The volley deadline also bounds a trapped/near-horizontal ball's lifetime.
    }

    private fun advanceBall(flight: BallFlight, time: Double) {
        val travel = (time - flight.elapsed).coerceAtLeast(0.0)
        val ball = flight.ball
        collectAlong(ball, travel)
        ball.x += ball.vx * travel
        ball.y += ball.vy * travel
        flight.elapsed = time
    }

    private fun scheduleContact(flight: BallFlight, duration: Double) {
        val remaining = duration - flight.elapsed
        if (remaining <= ACCUMULATOR_EPS) return
        findContact(flight.ball, remaining)
        flight.blocks.clear()
        if (!contactTime.isFinite()) return
        flight.time = flight.elapsed + contactTime
        flight.nx = contactNx
        flight.ny = contactNy
        flight.wallNx = contactWallNx
        flight.wallNy = contactWallNy
        flight.floor = contactFloor
        // Avoid Collection.addAll's temporary array on every contact.
        for (index in contactBlocks.indices) flight.blocks.add(contactBlocks[index])
        flight.contactX = flight.ball.x + flight.ball.vx * contactTime
        flight.contactY = flight.ball.y + flight.ball.vy * contactTime
        collisionQueue.add(flight)
    }

    private fun findContact(ball: Ball, duration: Double) {
        contactTime = Double.POSITIVE_INFINITY
        contactNx = 0.0
        contactNy = 0.0
        contactFloor = false
        contactWallNx = 0.0
        contactWallNy = 0.0
        contactBlocks.clear()

        fun consider(
            t: Double, nx: Double, ny: Double, block: Block? = null,
            floor: Boolean = false, wall: Boolean = false,
        ) {
            if (!t.isFinite() || t < -Collision.TIME_EPS || t > duration + Collision.TIME_EPS) return
            val time = t.coerceIn(0.0, duration)
            if (time < contactTime - Collision.TIME_EPS) {
                contactTime = time
                contactNx = if (wall) 0.0 else nx
                contactNy = if (wall) 0.0 else ny
                contactWallNx = if (wall) nx else 0.0
                contactWallNy = if (wall) ny else 0.0
                contactFloor = floor
                contactBlocks.clear()
                if (block != null) contactBlocks.add(block)
            } else if (abs(time - contactTime) <= Collision.TIME_EPS) {
                contactTime = min(contactTime, time)
                if (wall) {
                    contactWallNx += nx
                    contactWallNy += ny
                } else {
                    contactNx += nx
                    contactNy += ny
                }
                contactFloor = contactFloor || floor
                if (block != null) contactBlocks.add(block)
            }
        }

        if (ball.vx < 0.0) consider((MIN_X - ball.x) / ball.vx, 1.0, 0.0, wall = true)
        if (ball.vx > 0.0) consider((MAX_X - ball.x) / ball.vx, -1.0, 0.0, wall = true)
        if (ball.vy < 0.0) consider((MIN_Y - ball.y) / ball.vy, 0.0, 1.0, wall = true)
        if (ball.vy > 0.0) consider((LANDING_Y - ball.y) / ball.vy, 0.0, -1.0, floor = true)
        for (block in mutableBlocks) {
            if (!Collision.circleBox(
                ball.x, ball.y, ball.vx, ball.vy, Board.BALL_RADIUS,
                block.x, block.y, Board.BLOCK_SIZE, duration, blockContact,
            )) continue
            consider(blockContact.time, blockContact.nx, blockContact.ny, block)
        }
    }

    private fun collectAlong(ball: Ball, duration: Double) {
        var index = 0
        while (index < mutablePickups.size) {
            val pickup = mutablePickups[index]
            val time = Collision.circleEntryTime(
                ball.x, ball.y, ball.vx, ball.vy, pickup.x, pickup.y,
                PICKUP_RADIUS + Board.BALL_RADIUS, duration,
            )
            if (time.isFinite()) {
                mutablePickups.removeAt(index)
                collectedBalls++
            } else index++
        }
    }

    private fun finishVolley() {
        mutableBalls.clear()
        pendingLaunches = 0
        launchCountdown = 0.0
        launchX = (firstReturnX ?: launchX).coerceIn(MIN_X, MAX_X)
        ballCount = (ballCount + collectedBalls).coerceAtMost(MAX_BALLS)
        collectedBalls = 0
        firstReturnX = null
        advanceProgress = 0.0
        phase = Phase.ADVANCING
    }

    private fun stepAdvance() {
        advanceProgress = (advanceProgress + FIXED_STEP / ADVANCE_DURATION).coerceAtMost(1.0)
        if (advanceProgress < 1.0 - ACCUMULATOR_EPS) return
        for (block in mutableBlocks) block.row++
        for (pickup in mutablePickups) pickup.row++
        mutablePickups.removeAll { it.y + PICKUP_RADIUS > LANDING_Y }
        if (mutableBlocks.any { it.y + Board.BLOCK_SIZE >= LANDING_Y }) {
            advanceProgress = 0.0 // Rows have now been committed; do not translate them twice.
            phase = Phase.GAME_OVER
            return
        }
        if (round < Int.MAX_VALUE) round++
        spawnRow()
        advanceProgress = 0.0
        phase = Phase.AIMING
    }

    private fun spawnRow() {
        val columns = IntArray(COLUMNS) { it }
        for (i in columns.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val swap = columns[i]
            columns[i] = columns[j]
            columns[j] = swap
        }
        val count = 2 + random.nextInt(3)
        mutablePickups.add(Pickup(nextId++, columns[0], 0))
        for (i in 1..count) {
            var strength = if (round == 1) 1.0 else round * (0.7 + random.nextDouble() * 0.6)
            if (round >= 4 && random.nextInt(8) == 0) strength *= 2.0
            val hits = strength.coerceIn(1.0, Int.MAX_VALUE.toDouble()).roundToInt()
            mutableBlocks.add(Block(nextId++, columns[i], 0, hits))
        }
    }

    private fun ageImpacts() {
        for (index in mutableImpacts.lastIndex downTo 0) {
            val impact = mutableImpacts[index]
            impact.age += FIXED_STEP
            if (impact.age >= IMPACT_LIFETIME) mutableImpacts.removeAt(index)
        }
    }

    companion object {
        private const val FIXED_STEP = 1.0 / 120.0
        private const val ACCUMULATOR_EPS = 1e-12
        private const val SPEED = 430.0
        private const val RELEASE_INTERVAL = 0.045
        private const val ADVANCE_DURATION = 0.22
        private const val MAX_VOLLEY = 24.0
        private const val PICKUP_RADIUS = 9.0
        private const val PUSH_OUT = 1e-5
        private const val MAX_CONTACTS_PER_SLICE = 16
        private const val MAX_IMPACTS = 64
        private const val IMPACT_LIFETIME = 0.18
        private const val MAX_BALLS = 999
        private const val COLUMNS = 7
        private const val MIN_X = Board.LEFT + Board.BALL_RADIUS
        private const val MAX_X = Board.RIGHT - Board.BALL_RADIUS
        private const val MIN_Y = Board.TOP + Board.BALL_RADIUS
        private const val LANDING_Y = Board.FLOOR - Board.BALL_RADIUS

        /** The LAST scheduled ball gets the full travel budget, even at the ball cap. */
        private fun volleyDeadline(ballCount: Int): Double =
            (ballCount - 1) * RELEASE_INTERVAL + MAX_VOLLEY

        private fun validAngle(angle: Double): Boolean =
            angle.isFinite() && angle >= -PI + 0.12 && angle <= -0.12

        /** Rejects corrupt, nonfinite, overlapping, oversized, or inconsistent state. */
        fun restore(snapshot: GameSnapshot): GameEngine? {
            if (!validSnapshot(snapshot)) return null
            return GameEngine(snapshot.rngState, false).apply {
                phase = snapshot.phase
                round = snapshot.round
                ballCount = snapshot.ballCount
                launchX = snapshot.launchX
                mutableBlocks.addAll(snapshot.blocks.map { it.copy() })
                mutablePickups.addAll(snapshot.pickups.map { it.copy() })
                mutableBalls.addAll(snapshot.balls.map { it.copy() })
                mutableImpacts.addAll(snapshot.impacts.map { it.copy() })
                shotElapsed = snapshot.shotElapsed
                advanceProgress = snapshot.advanceProgress
                totalHits = snapshot.totalHits
                destroyedBlocks = snapshot.destroyedBlocks
                nextId = snapshot.nextId
                pendingLaunches = snapshot.pendingLaunches
                launchCountdown = snapshot.launchCountdown
                shotAngle = snapshot.shotAngle
                firstReturnX = snapshot.firstReturnX
                collectedBalls = snapshot.collectedBalls
                accumulator = snapshot.accumulator
            }
        }

        private fun validSnapshot(s: GameSnapshot): Boolean {
            if (s.version != 1 || s.round < 1 || s.ballCount !in 1..MAX_BALLS) return false
            if (!s.launchX.isFinite() || s.launchX !in MIN_X..MAX_X) return false
            if (!validAngle(s.shotAngle)) return false
            if (!s.accumulator.isFinite() || s.accumulator < 0.0 || s.accumulator >= FIXED_STEP) return false
            val deadline = volleyDeadline(s.ballCount)
            if (!s.shotElapsed.isFinite() || s.shotElapsed !in 0.0..deadline) return false
            if (!s.advanceProgress.isFinite() || s.advanceProgress < 0.0 || s.advanceProgress >= 1.0) return false
            if (s.phase != Phase.ADVANCING && s.advanceProgress != 0.0) return false
            if (s.totalHits < 0 || s.destroyedBlocks < 0 || s.destroyedBlocks.toLong() > s.totalHits) return false
            if (s.blocks.size > 70 || s.pickups.size > 70 || s.impacts.size > MAX_IMPACTS) return false
            if (s.balls.size > s.ballCount || s.pendingLaunches !in 0 until s.ballCount) return false
            if (s.balls.size + s.pendingLaunches > s.ballCount || s.collectedBalls !in 0..70) return false
            if (!s.launchCountdown.isFinite() || s.launchCountdown !in 0.0..RELEASE_INTERVAL) return false
            if (s.firstReturnX != null && (!s.firstReturnX.isFinite() || s.firstReturnX !in MIN_X..MAX_X)) return false
            if (s.phase == Phase.FIRING) {
                if ((s.balls.isEmpty() && s.pendingLaunches == 0) || s.shotElapsed >= deadline) return false
                if (s.pendingLaunches > 0 && s.launchCountdown <= 0.0) return false
                if (s.pendingLaunches == 0 && s.launchCountdown != 0.0) return false
                val hasReturnedBalls = s.balls.size + s.pendingLaunches < s.ballCount
                if (hasReturnedBalls != (s.firstReturnX != null)) return false
            } else if (s.balls.isNotEmpty() || s.pendingLaunches != 0 || s.launchCountdown != 0.0 ||
                s.collectedBalls != 0 || s.firstReturnX != null
            ) return false

            if (s.nextId < 1 || s.nextId > Long.MAX_VALUE - 8) return false
            val ids = HashSet<Long>()
            val occupied = HashSet<Int>()
            for (block in s.blocks) {
                val maxRow = if (s.phase == Phase.GAME_OVER) 9 else 8
                if (block.id < 1 || block.id >= s.nextId || !ids.add(block.id)) return false
                if (block.column !in 0 until COLUMNS || block.row !in 0..maxRow || block.hits < 1) return false
                if (!occupied.add(block.row * COLUMNS + block.column)) return false
            }
            for (pickup in s.pickups) {
                if (pickup.id < 1 || pickup.id >= s.nextId || !ids.add(pickup.id)) return false
                if (pickup.column !in 0 until COLUMNS || pickup.row !in 0..9) return false
                if (!occupied.add(pickup.row * COLUMNS + pickup.column)) return false
            }
            // A full row cannot arise from the generator and closes off every playable lane.
            if (s.blocks.groupingBy { it.row }.eachCount().values.any { it > 4 }) return false
            if (s.pickups.groupingBy { it.row }.eachCount().values.any { it > 1 }) return false
            if (occupied.groupingBy { it / COLUMNS }.eachCount().values.any { it > 5 }) return false
            if (s.phase == Phase.GAME_OVER && s.blocks.none { it.row == 9 }) return false
            for (ball in s.balls) {
                if (!ball.x.isFinite() || !ball.y.isFinite() || !ball.vx.isFinite() || !ball.vy.isFinite()) return false
                if (ball.x !in MIN_X..MAX_X || ball.y !in MIN_Y..LANDING_Y) return false
                val speed = hypot(ball.vx, ball.vy)
                if (speed < 1e-6 || speed > 10_000.0) return false
                if (s.blocks.any { Collision.overlapsBox(ball, it) }) return false
            }
            if (s.impacts.any {
                !it.x.isFinite() || !it.y.isFinite() || !it.age.isFinite() ||
                    it.x !in 0.0..Board.WIDTH || it.y !in 0.0..Board.HEIGHT ||
                    it.hitsBefore < 1 || it.age < 0.0 || it.age >= IMPACT_LIFETIME
            }) return false
            return true
        }
    }
}

/** Reused per-ball sweep and heap entry; never serialized or exposed to callers. */
private class BallFlight : Comparable<BallFlight> {
    lateinit var ball: Ball
    val blocks = ArrayList<Block>(4)
    var elapsed = 0.0
    var time = 0.0
    var nx = 0.0
    var ny = 0.0
    var wallNx = 0.0
    var wallNy = 0.0
    var floor = false
    var contactX = 0.0
    var contactY = 0.0
    var contacts = 0
    var landed = false
    var stopped = false

    override fun compareTo(other: BallFlight): Int {
        // A strict time order keeps the heap transitive (no epsilon comparator). Exact
        // ties use geometry/velocity, not array order; physically identical balls may tie.
        var result = time.compareTo(other.time)
        if (result == 0) result = contactX.compareTo(other.contactX)
        if (result == 0) result = contactY.compareTo(other.contactY)
        if (result == 0) result = ball.vx.compareTo(other.ball.vx)
        if (result == 0) result = ball.vy.compareTo(other.ball.vy)
        return result
    }
}

/** SplitMix64: one serializable state word, independent of Kotlin/platform RNG versions. */
private class SeededRandom(var state: Long) {
    private fun nextLong(): Long {
        state += -7046029254386353131L
        var value = state
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }

    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / 9007199254740992.0
}