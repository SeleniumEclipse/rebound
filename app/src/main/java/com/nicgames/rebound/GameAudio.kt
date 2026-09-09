package com.nicgames.rebound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.nicgames.rebound.audio.PopMixer
import com.nicgames.rebound.audio.PopSample

/** Persistent PCM stream: no load race, cooldown, or recycled SoundPool voices.
 * System media volume remains under the player's control. */
class GameAudio(context: Context) {
    private val lock = Object()
    private val mixer = PopMixer(PopSample.decode(context.resources.openRawResource(R.raw.ui_pop).use { it.readBytes() }))
    private fun createTrack(): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(maxOf(4096, AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()
    @Volatile private var track = createTrack()
    @Volatile private var released = false
    @Volatile private var failed = false
    private var flushRequested = false
    private var generation = 0
    private var tailFrames = 0
    @Volatile var flushCount = 0
        private set
    @Volatile var recoveryCount = 0
        private set
    @Volatile var idle = true
        private set
    @Volatile var playbackCount = 0
        private set
    @Volatile var writtenFrames = 0L
        private set
    @Volatile var audibleFrames = 0L
        private set
    @Volatile var writeFailures = 0
        private set
    val ready: Boolean get() = !released && !failed && track.state == AudioTrack.STATE_INITIALIZED
    val playedFrames: Long get() = if (released || failed) 0L else runCatching { track.playbackHeadPosition.toLong() and 0xffffffffL }.getOrDefault(0L)
    private val worker = Thread(::writeLoop, "ReboundAudio").apply { isDaemon = true; start() }

    fun hit(preview: Boolean = false, count: Int = 1): Boolean = synchronized(lock) {
        if (!ready || count <= 0) return@synchronized false
        mixer.add(count, preview)
        idle = false
        // A short isolated pop must still fill the device-specific start threshold.
        // Trailing silence guarantees it starts even on a high-latency output route.
        tailFrames = track.bufferCapacityInFrames
        playbackCount += count
        lock.notifyAll()
        true
    }

    private fun writeLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        val output = ShortArray(256)
        try {
            if (!released) track.play()
            while (true) {
                val work = synchronized(lock) {
                    while (!released && !flushRequested && !mixer.active && tailFrames == 0) {
                        idle = true
                        lock.wait()
                    }
                    if (released) return
                    val flush = flushRequested
                    flushRequested = false
                    val count = if (mixer.active) mixer.render(output) else {
                        output.fill(0)
                        minOf(output.size, tailFrames).also { tailFrames -= it }
                    }
                    Triple(generation, flush, count)
                }
                if (work.second) { track.pause(); track.flush(); track.play(); flushCount++ }
                var offset = 0
                while (offset < work.third && !released) {
                    if (synchronized(lock) { work.first != generation }) break
                    // Never hold the producer lock during device I/O. Hit delivery
                    // and lifecycle callbacks must not wait for an audio driver.
                    val written = track.write(output, offset, work.third - offset, AudioTrack.WRITE_NON_BLOCKING)
                    if (written == 0) {
                        // Interruptible backpressure, not a driver call that can strand
                        // stop/release forever. Lifecycle and new hits wake this wait.
                        synchronized(lock) { if (!released && work.first == generation) lock.wait(4L) }
                        continue
                    }
                    if (written == AudioTrack.ERROR_DEAD_OBJECT && recoveryCount < 3) {
                        track.release()
                        track = createTrack()
                        track.play()
                        recoveryCount++
                        synchronized(lock) { tailFrames = track.bufferCapacityInFrames }
                        continue
                    }
                    if (written < 0) { writeFailures++; failed = true; return }
                    writtenFrames += written
                    for (i in offset until offset + written) if (kotlin.math.abs(output[i].toInt()) > 100) audibleFrames++
                    offset += written
                }
            }
        } catch (_: Exception) {
            if (!released) { writeFailures++; failed = true }
        } finally { track.release() }
    }

    fun stop() = synchronized(lock) {
        // Lifecycle cancellation must work even while the output is being recovered.
        if (released) return@synchronized
        mixer.clear()
        tailFrames = 0
        generation++
        flushRequested = true
        lock.notifyAll()
    }

    fun release() = synchronized(lock) {
        if (!released) { mixer.clear(); tailFrames = 0; released = true; lock.notifyAll() }
    }
}