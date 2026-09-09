package com.nicgames.rebound

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import com.nicgames.rebound.ui.ReboundApp

class MainActivity : ComponentActivity() {
    val model: AppModel by viewModels()
    private var tone: ToneGenerator? = null
    private var lastTone = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xff243036.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xff243036.toInt()),
        )
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 22) }.getOrNull()
        setContent { ReboundApp(model, onHit = ::hit) }
    }

    private fun hit() {
        val now = android.os.SystemClock.uptimeMillis()
        if (model.sound && now - lastTone > 75) {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 24)
            lastTone = now
        }
    }

    override fun onPause() {
        model.background()
        tone?.stopTone()
        super.onPause()
    }

    override fun onDestroy() {
        tone?.release()
        tone = null
        super.onDestroy()
    }
}