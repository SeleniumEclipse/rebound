package com.nicgames.rebound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Predecoded recorded pop. Each hit-bearing frame plays; there is no time-based
 * discard gate. Simultaneous collisions share a pop instead of building an audio backlog. */
class GameAudio(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    @Volatile var ready = false
        private set
    var playbackCount = 0
        private set
    private var released = false
    private val streams = IntArray(12)
    private var streamIndex = 0
    private val hitId: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status -> if (!released) ready = status == 0 }
        hitId = pool.load(context, R.raw.block_pop, 1)
    }

    fun hit(preview: Boolean = false): Boolean {
        if (!ready || released) return false
        val gain = if (preview) .9f else .8f
        val stream = pool.play(hitId, gain, gain, 1, 0, 1f)
        if (stream == 0) return false
        streams[streamIndex] = stream
        streamIndex = (streamIndex + 1) % streams.size
        playbackCount++
        return true
    }

    fun stop() { if (!released) streams.forEach { if (it != 0) pool.stop(it) } }
    fun release() {
        if (!released) { stop(); released = true; ready = false; pool.release() }
    }
}