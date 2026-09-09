package com.nicgames.rebound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock

/** Predecoded short effects. System media volume remains under the player's control. */
class GameAudio(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    @Volatile var ready = false
        private set
    var playbackCount = 0
        private set
    private var lastPlayback = -1000L
    private var released = false
    private val streams = IntArray(3)
    private var streamIndex = 0
    private val hitId: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status -> if (!released) ready = status == 0 }
        hitId = pool.load(context, R.raw.block_hit, 1)
    }

    fun hit(preview: Boolean = false): Boolean {
        if (!ready || released) return false
        val now = SystemClock.uptimeMillis()
        if (!preview && now - lastPlayback < 55L) return false
        val stream = pool.play(hitId, .9f, .9f, 1, 0, 1f)
        if (stream == 0) return false
        streams[streamIndex] = stream
        streamIndex = (streamIndex + 1) % streams.size
        lastPlayback = now
        playbackCount++
        return true
    }

    fun stop() { if (!released) streams.forEach { if (it != 0) pool.stop(it) } }
    fun release() {
        if (!released) { stop(); released = true; ready = false; pool.release() }
    }
}