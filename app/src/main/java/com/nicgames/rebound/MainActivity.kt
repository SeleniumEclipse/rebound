package com.nicgames.rebound

import android.media.AudioManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import com.nicgames.rebound.ui.ReboundApp

class MainActivity : ComponentActivity() {
    val model: AppModel by viewModels()
    private var audio: GameAudio? = null
    internal val audioReady: Boolean get() = audio?.ready == true
    internal val soundPlaybackCount: Int get() = audio?.playbackCount ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xff243036.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xff243036.toInt()),
        )
        volumeControlStream = AudioManager.STREAM_MUSIC
        audio = runCatching { GameAudio(this) }.getOrNull()
        setContent { ReboundApp(model, onHit = ::hit, onTestSound = ::testSound) }
    }

    private fun hit() {
        if (model.sound) audio?.hit()
    }

    private fun testSound() {
        if (!model.sound) return
        val manager = getSystemService(AudioManager::class.java)
        if (manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Toast.makeText(this, "Media volume is muted. Use the volume buttons.", Toast.LENGTH_SHORT).show()
        }
        if (audio?.hit(preview = true) != true) {
            Toast.makeText(this, "Sound is not ready. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        model.background()
        audio?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        audio?.release()
        audio = null
        super.onDestroy()
    }
}