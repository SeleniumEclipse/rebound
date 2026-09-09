package com.nicgames.rebound

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nicgames.rebound.game.Block
import com.nicgames.rebound.game.Board
import com.nicgames.rebound.game.GameEngine
import com.nicgames.rebound.game.GameSnapshot
import com.nicgames.rebound.game.Phase
import com.nicgames.rebound.game.Pickup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.math.min

/** Each method declares its own seed; there is deliberately no test-method ordering. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LaunchWith(val value: ReboundFixture, val helpSeen: Boolean = true)

enum class ReboundFixture { EMPTY, ROUND_42, MID_FLIGHT, BEFORE_LOSS }

internal object ReboundFixtures {
    fun round42(): GameSnapshot = checked(GameEngine(4242L).snapshot().copy(
        round = 42,
        ballCount = 42,
        blocks = listOf(
            Block(1, 0, 0, 9), Block(2, 2, 0, 19),
            Block(3, 4, 0, 29), Block(4, 6, 0, 39),
            Block(5, 1, 1, 59), Block(6, 3, 1, 99), Block(7, 5, 1, 140),
        ),
        pickups = listOf(Pickup(8, 3, 0), Pickup(9, 6, 2)),
        nextId = 1000L,
    ))

    fun midFlight(): GameSnapshot {
        val game = requireNotNull(GameEngine.restore(round42()))
        check(game.fire(-1.25))
        game.tick(0.123) // Keeps airborne balls, a release queue and a partial physics step.
        return checked(game.snapshot())
    }

    fun beforeLoss(): GameSnapshot {
        val game = requireNotNull(GameEngine.restore(round42().copy(
            ballCount = 1,
            blocks = listOf(Block(1, 3, 8, 5)),
            pickups = emptyList(),
        )))
        check(game.fire(-1.0))
        check(game.recall())
        game.tick(0.2)
        check(game.phase == Phase.ADVANCING)
        // A legal row-eight block, not an invalid GAME_OVER flag on a live board.
        return checked(game.snapshot())
    }

    fun saved(fixture: ReboundFixture): SavedGame? {
        val snapshot = when (fixture) {
            ReboundFixture.EMPTY -> return null
            ReboundFixture.ROUND_42 -> round42()
            ReboundFixture.MID_FLIGHT -> midFlight()
            ReboundFixture.BEFORE_LOSS -> beforeLoss()
        }
        // Only app-local preferences are changed. No emulator-wide audio/animation settings.
        return SavedGame(engine = snapshot, best = 73, sound = false, haptics = false)
    }

    private fun checked(snapshot: GameSnapshot): GameSnapshot {
        assertNotNull("Instrumentation fixture must pass production snapshot validation", GameEngine.restore(snapshot))
        return snapshot
    }
}

private class FreshReboundPreferences : ExternalResource() {
    private var fixture = ReboundFixture.EMPTY
    private var helpSeen = false
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    override fun apply(base: Statement, description: Description): Statement {
        val launch = description.getAnnotation(LaunchWith::class.java)
        fixture = launch?.value ?: ReboundFixture.EMPTY
        helpSeen = launch?.helpSeen ?: false
        return super.apply(base, description)
    }

    override fun before() {
        clear()
        ReboundFixtures.saved(fixture)?.let { saved ->
            GameStore(context).write(saved, immediate = true)
            assertEquals("Fixture must be stored before MainActivity constructs its model", saved, GameStore(context).load())
            assertTrue(context.getSharedPreferences("rebound", Context.MODE_PRIVATE)
                .edit().putBoolean("helpSeen", helpSeen).commit())
        }
    }

    override fun after() {
        // The inner Compose/activity rule has already closed MainActivity and saved onPause.
        clear()
    }

    private fun clear() {
        assertTrue("Could not reset this app's preferences",
            context.getSharedPreferences("rebound", Context.MODE_PRIVATE).edit().clear().commit())
    }
}

/** Real MainActivity, real GameStore and a manually advanced UI frame clock. */
abstract class ReboundUiTest {
    protected val compose = createAndroidComposeRule<MainActivity>()

    // @Before alone would be too late: the activity rule launches before @Before methods.
    @get:Rule
    val rules: TestRule = RuleChain.outerRule(FreshReboundPreferences()).around(compose)

    protected val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun controlFrameClock() {
        // Every launch starts on HOME, so no simulation runs before this is set.
        compose.mainClock.autoAdvance = false
        frame()
    }

    protected fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    protected fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }

    protected fun <T> modelValue(read: (AppModel) -> T): T {
        val activity = compose.activity
        return compose.runOnIdle { read(activity.model) }
    }

    protected fun snapshot(): GameSnapshot = modelValue { requireNotNull(it.engine).snapshot() }

    protected fun click(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed().performClick()
        frame()
    }

    protected fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        frame()
    }

    protected fun headerBack() {
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        frame()
    }

    protected fun assertScreen(expected: Screen) {
        assertEquals(expected, modelValue { it.screen })
    }

    protected fun dialogAction(text: String): SemanticsNodeInteraction =
        compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    protected fun assertSetting(title: String, enabled: Boolean) {
        compose.onNodeWithText(title).assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (enabled) "On" else "Off"),
        )
    }

    protected fun assertTouchTarget(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed().assertIsEnabled().assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    /** Coordinates are local to the Canvas, not screen pixels or a stretched 360x504 board. */
    protected fun boardPoint(x: Double, y: Double): Offset {
        val bounds = compose.onNodeWithTag("game-board").fetchSemanticsNode().boundsInRoot
        val scale = min(bounds.width / Board.WIDTH, bounds.height / Board.HEIGHT)
        val left = (bounds.width - Board.WIDTH * scale) / 2.0
        val top = (bounds.height - Board.HEIGHT * scale) / 2.0
        return Offset((left + x * scale).toFloat(), (top + y * scale).toFloat())
    }

    protected fun recreateWithFreshModel() {
        val previous = modelValue { it }
        compose.activityRule.scenario.onActivity { activity ->
            activity.model.save(immediate = true)
            // recreate() normally retains ViewModels and would NOT test reading GameStore.
            // Force a new activity-scoped model; this is not a claim to simulate process death.
            activity.viewModelStore.clear()
        }
        compose.activityRule.scenario.recreate()
        frame()
        assertNotSame("Restoration must construct a new AppModel", previous, modelValue { it })
        assertScreen(Screen.HOME)
    }

    protected fun captureScreen(name: String) {
        compose.waitForIdle()
        // Evidence is optional; screenshot service failure must not hide a gameplay assertion.
        runCatching {
            val output = File(targetContext.filesDir, "screenshot-$name.png")
            // Do not leave evidence from an older run if this capture fails.
            if (output.exists()) check(output.delete())
            val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                output.outputStream().use { stream ->
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                Log.i("ReboundScreenshots", "Saved ${output.absolutePath}")
            } finally {
                image.recycle()
            }
        }.onFailure { Log.w("ReboundScreenshots", "Screenshot unavailable: $name", it) }
    }
}