package com.nicgames.rebound

import android.app.Application
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.nicgames.rebound.game.GameEngine
import com.nicgames.rebound.game.GameSnapshot
import com.nicgames.rebound.game.Phase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class Screen { HOME, PLAY, PAUSE, RESULTS, HELP, SETTINGS, LICENSES }

@Serializable
data class SavedGame(val version: Int = 1, val engine: GameSnapshot? = null, val best: Int = 0, val sound: Boolean = true, val haptics: Boolean = true, val motion: Boolean = true)

class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("rebound", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    fun load(): SavedGame? = runCatching {
        val raw = prefs.getString("game", null) ?: return null
        if (raw.length > 1_000_000) return null
        json.decodeFromString<SavedGame>(raw).takeIf { it.version == 1 && it.best >= 0 }
    }.getOrNull()
    @SuppressLint("ApplySharedPref") // Lifecycle checkpoint must reach disk before process exit.
    fun write(game: SavedGame, immediate: Boolean = false) {
        val edit = prefs.edit().putString("game", json.encodeToString(game))
        if (immediate) edit.commit() else edit.apply()
    }
}

class AppModel(application: Application) : AndroidViewModel(application) {
    private val store = GameStore(application)
    private val saved = store.load()
    var engine: GameEngine? = saved?.engine?.let(GameEngine::restore)
        private set
    var screen by mutableStateOf(Screen.HOME)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var best by mutableIntStateOf(maxOf(saved?.best ?: 0, engine?.round ?: 0))
        private set
    var sound by mutableStateOf(saved?.sound ?: true)
        private set
    var haptics by mutableStateOf(saved?.haptics ?: true)
        private set
    var motion by mutableStateOf(saved?.motion ?: true)
        private set
    var speed by mutableIntStateOf(1)
        private set
    var angle by mutableDoubleStateOf(-Math.PI / 2)
        private set
    var aiming by mutableStateOf(false)
        private set
    var helpSeen by mutableStateOf(application.getSharedPreferences("rebound", 0).getBoolean("helpSeen", false))
        private set
    private var saveAge = 0.0
    private var backFromHelp = Screen.HOME
    val canContinue: Boolean get() = engine?.phase?.let { it != Phase.GAME_OVER } ?: false

    fun newGame() {
        engine = GameEngine()
        angle = -Math.PI / 2
        aiming = false
        speed = 1
        screen = Screen.PLAY
        best = maxOf(best, 1)
        revision++
        save()
    }

    fun resume() {
        if (canContinue) { screen = Screen.PLAY; aiming = false; revision++ }
    }

    fun aim(value: Double) {
        if (screen == Screen.PLAY && engine?.phase == Phase.AIMING && value.isFinite()) {
            angle = value.coerceIn(-Math.PI + 0.13, -0.13)
            aiming = true
        }
    }

    fun cancelAim() { aiming = false }

    fun fire(): Boolean {
        if (screen != Screen.PLAY || !aiming) return false
        aiming = false
        val fired = engine?.fire(angle) == true
        if (fired) {
            helpSeen = true
            getApplication<Application>().getSharedPreferences("rebound", 0).edit().putBoolean("helpSeen", true).apply()
            revision++
            save()
        }
        return fired
    }

    fun tick(seconds: Double) {
        val game = engine ?: return
        if (screen != Screen.PLAY || game.phase == Phase.AIMING || !seconds.isFinite() || seconds <= 0) return
        val oldPhase = game.phase
        var remaining = seconds.coerceAtMost(0.05) * speed
        while (remaining > 0.000001) {
            val step = minOf(0.2, remaining)
            game.tick(step)
            remaining -= step
        }
        revision++
        best = maxOf(best, game.round)
        saveAge += seconds
        if (game.phase == Phase.GAME_OVER) screen = Screen.RESULTS
        if (game.phase != oldPhase || saveAge > 2.0) save()
    }

    fun pause() {
        if (screen == Screen.PLAY) { aiming = false; screen = Screen.PAUSE; save() }
    }

    fun home() { aiming = false; screen = Screen.HOME; save() }
    fun help() { backFromHelp = screen; screen = Screen.HELP; aiming = false }
    fun settings() { screen = Screen.SETTINGS }
    fun licenses() { screen = Screen.LICENSES }
    fun back() {
        when (screen) {
            Screen.PLAY -> pause()
            Screen.PAUSE -> resume()
            Screen.HELP -> screen = backFromHelp
            Screen.LICENSES -> screen = Screen.SETTINGS
            else -> home()
        }
    }
    fun toggleSound() { sound = !sound; save() }
    fun toggleHaptics() { haptics = !haptics; save() }
    fun toggleMotion() { motion = !motion; save() }
    fun changeSpeed() { speed = when (speed) { 1 -> 3; 3 -> 6; else -> 1 } }
    fun recall() {
        if (screen == Screen.PLAY && engine?.recall() == true) { revision++; save() }
    }
    fun background() { pause(); save(immediate = true) }
    fun save(immediate: Boolean = false) {
        store.write(SavedGame(engine = engine?.snapshot(), best = best, sound = sound, haptics = haptics, motion = motion), immediate)
        saveAge = 0.0
    }
}