package com.nicgames.rebound.ui

import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.nicgames.rebound.AppModel
import com.nicgames.rebound.BuildConfig
import com.nicgames.rebound.R
import com.nicgames.rebound.Screen
import com.nicgames.rebound.game.Board
import com.nicgames.rebound.game.Phase
import kotlin.math.min
import java.util.Locale

@Composable
fun ReboundApp(model: AppModel, onHit: () -> Unit = {}, onTestSound: () -> Unit = {}) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = Color.White, surface = Field, onSurface = Ink, background = Field)) {
        val screen = model.screen
        BackHandler(screen != Screen.HOME) { model.back() }
        Box(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
            Box(Modifier.fillMaxSize().background(Field)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(model)
                    Screen.PLAY -> PlayScreen(model, onHit)
                    Screen.PAUSE -> PauseScreen(model)
                    Screen.RESULTS -> ResultsScreen(model)
                    Screen.HELP -> HelpScreen(model)
                    Screen.SETTINGS -> SettingsScreen(model, onTestSound)
                    Screen.LICENSES -> LicensesScreen(model)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, color: Color = Ink, size: Int = 16, strong: Boolean = false) {
    Text(text, modifier, color = color, fontSize = size.sp, fontFamily = Archivo, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
}

@Composable
private fun Display(text: String, modifier: Modifier = Modifier, color: Color = Ink, size: Int = 42) {
    Text(text, modifier, fontFamily = ArchivoBlack, fontWeight = FontWeight.Black, fontSize = size.sp, lineHeight = (size * 1.02).sp, letterSpacing = (-1).sp, color = color)
}

@Composable
private fun SpectrumEdge(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(5.dp)) { Spectrum.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) } }
}

@Composable
private fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Color = Ink, textColor: Color = Color.White) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = textColor),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Text(text, fontFamily = ArchivoBlack, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Ink) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), shape = RectangleShape,
        border = BorderStroke(1.dp, color), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) {
        Text(text, fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Rebound colored blocks and bouncing ball" }) {
        val s = min(size.width / 250f, size.height / 160f)
        translate((size.width - 250 * s) / 2, (size.height - 160 * s) / 2) {
            scale(s, Offset.Zero) {
                listOf(Offset(9f, 12f), Offset(56f, 12f), Offset(103f, 12f), Offset(150f, 12f), Offset(197f, 12f), Offset(197f, 59f)).forEachIndexed { i, p ->
                    drawRect(Spectrum[i], p, Size(40f, 40f))
                }
                drawLine(Color.White, Offset(39f, 143f), Offset(147f, 64f), strokeWidth = 3f)
                drawLine(Color.White, Offset(147f, 64f), Offset(186f, 105f), strokeWidth = 3f)
                drawCircle(Color.White, 7f, Offset(39f, 143f))
            }
        }
    }
}

@Composable
private fun HomeScreen(model: AppModel) {
    var confirmNew by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 650.dp
        val titleSize = if (maxWidth < 350.dp) 38 else 44
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().weight(1f).background(Ink).padding(horizontal = 26.dp, vertical = if (compact) 18.dp else 30.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Display("REBOUND", color = Color.White, size = titleSize)
                BrandMark(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Label("Best round", color = Color.White, size = 15)
                    Display(model.best.toString(), color = Color.White, size = 38)
                }
            }
            SpectrumEdge()
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 14.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model.canContinue) {
                    Action("Continue", model::resume, fill = Teal, textColor = Ink)
                    TextAction("New game", { confirmNew = true }, Modifier.fillMaxWidth())
                } else Action("Play", model::newGame, fill = Coral, textColor = Ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextAction("How to play", model::help, Modifier.weight(1f))
                    TextAction("Settings", model::settings, Modifier.weight(1f))
                }
            }
        }
    }
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, shape = RectangleShape,
        title = { Label("Start a new game?", strong = true, size = 22) },
        text = { Label("Your current run will be replaced.") },
        confirmButton = { TextAction("New game", { confirmNew = false; model.newGame() }) },
        dismissButton = { TextAction("Keep playing", { confirmNew = false }) })
}

@Composable
private fun PlayScreen(model: AppModel, onHit: () -> Unit) {
    val game = model.engine ?: return
    val revision = model.revision
    val phase = game.phase
    val view = LocalView.current
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    LaunchedEffect(model, phase) {
        if (phase == Phase.FIRING || phase == Phase.ADVANCING) {
            var previous = 0L
            while (model.screen == Screen.PLAY && model.engine?.phase != Phase.AIMING && model.engine?.phase != Phase.GAME_OVER) {
                withFrameNanos { now ->
                    val hits = game.totalHits
                    if (previous != 0L) model.tick((now - previous) / 1_000_000_000.0)
                    previous = now
                    if (game.totalHits > hits) onHit()
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Display(game.round.toString(), Modifier.testTag("round"), Color.White, if (game.round < 1000) 36 else 27)
                Label("Round", color = Color.White, size = 12)
            }
            TextAction("Settings", model::settings, color = Color.White)
            TextAction("Pause", model::pause, color = Color.White)
        }
        SpectrumEdge()
        Box(Modifier.weight(1f).fillMaxWidth().background(Field), contentAlignment = Alignment.Center) {
            GameBoard(model, revision, Modifier.fillMaxSize())
            if (!model.helpSeen && phase == Phase.AIMING) Label("Pull back to aim. Release to shoot.", Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp), size = 13)
        }
        Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.size(8.dp)) { drawCircle(Color.White) }
                Label("${game.ballCount}", Modifier.testTag("ball-count"), Color.White, 21, strong = true)
                Label(if (game.ballCount == 1) "ball" else "balls", color = Color.White, size = 12)
            }
            if (phase == Phase.FIRING) {
                if (game.shotElapsed >= 4.0) TextAction("Collect", model::recall, color = Color.White)
                if (model.showSpeedUp) {
                    OutlinedButton(model::speedUp,
                        Modifier.heightIn(min = 48.dp).testTag("speed-up").semantics {
                            selected = model.speedUpActive
                            stateDescription = if (model.speedUpActive) "Maximum speed for this round" else "Use maximum speed until this round ends"
                        },
                        shape = RectangleShape, border = BorderStroke(1.dp, Teal),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (model.speedUpActive) Teal else Color.Transparent,
                            contentColor = if (model.speedUpActive) Ink else Teal)) {
                        Label("Speed up", color = if (model.speedUpActive) Ink else Teal, size = 14, strong = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameBoard(model: AppModel, revision: Int, modifier: Modifier) {
    val game = model.engine ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val numberPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(context, R.font.archivo_semibold); textAlign = Paint.Align.CENTER } }
    val phase = game.phase
    val aiming = model.aiming
    val angle = model.angle
    val rowShift = if (phase == Phase.ADVANCING && model.motion) game.advanceProgress * Board.ROW_STEP else 0.0
    val summary = remember(revision) { "Round ${game.round}. ${game.ballCount} balls. ${game.blocks.size} blocks. ${if (phase == Phase.AIMING) "Ready to aim." else "Balls moving."}" }
    Canvas(modifier.testTag("game-board").semantics {
        contentDescription = "Game board"
        stateDescription = summary
        customActions = listOf(
            CustomAccessibilityAction("Aim left") { model.aim(model.angle - .09); true },
            CustomAccessibilityAction("Aim right") { model.aim(model.angle + .09); true },
            CustomAccessibilityAction("Shoot") { model.aim(model.angle); model.fire() },
        )
    }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || phase != Phase.AIMING) false
        else when (event.key) {
            Key.DirectionLeft -> { model.aim(model.angle - .035); true }
            Key.DirectionRight -> { model.aim(model.angle + .035); true }
            Key.Enter, Key.Spacebar -> { model.aim(model.angle); model.fire(); true }
            else -> false
        }
    }.focusable().pointerInput(game, phase) {
        if (phase != Phase.AIMING) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val scale = min(size.width / Board.WIDTH, size.height / Board.HEIGHT)
            fun pointAim(p: Offset) {
                val angle = PullBackAim.angle(
                    (p.x - down.position.x) / scale,
                    (p.y - down.position.y) / scale,
                )
                if (angle == null) model.cancelAim() else model.aim(angle)
            }
            model.cancelAim()
            down.consume()
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    pointAim(change.position)
                    if (!change.pressed) { released = true; change.consume(); break }
                    change.consume()
                }
                if (released && model.fire() && model.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } finally {
                if (!released) model.cancelAim()
            }
        }
    }) {
        // Subscribe the drawing phase itself to mutable simulation updates. The
        // engine's identity stays constant; recomposition alone can reuse this lambda.
        model.revision
        val s = min(size.width / Board.WIDTH.toFloat(), size.height / Board.HEIGHT.toFloat())
        translate((size.width - Board.WIDTH.toFloat() * s) / 2, (size.height - Board.HEIGHT.toFloat() * s) / 2) {
            scale(s, Offset.Zero) {
                // All three visible boundaries match the actual physics surfaces.
                val wallColor = Color(0xFF879297)
                drawLine(wallColor, Offset(Board.LEFT.toFloat(), Board.TOP.toFloat()), Offset(Board.RIGHT.toFloat(), Board.TOP.toFloat()), 1.5f)
                drawLine(wallColor, Offset(Board.LEFT.toFloat(), Board.TOP.toFloat()), Offset(Board.LEFT.toFloat(), Board.FLOOR.toFloat()), 1.5f)
                drawLine(wallColor, Offset(Board.RIGHT.toFloat(), Board.TOP.toFloat()), Offset(Board.RIGHT.toFloat(), Board.FLOOR.toFloat()), 1.5f)
                game.blocks.forEach { block ->
                    val x = block.x.toFloat(); val y = (block.y + rowShift).toFloat()
                    drawRect(blockColor(block.hits), Offset(x, y), Size(42f, 42f))
                    numberPaint.color = if (block.hits >= 100) android.graphics.Color.WHITE else Ink.toArgb()
                    val text = block.hits.toString()
                    numberPaint.textSize = when (text.length) { 1, 2 -> 21f; 3 -> 17f; 4 -> 14f; else -> 12f }
                    val measured = numberPaint.measureText(text)
                    if (measured > 36f) numberPaint.textSize *= 36f / measured
                    val baseline = y + 21f - (numberPaint.ascent() + numberPaint.descent()) / 2
                    drawContext.canvas.nativeCanvas.drawText(text, x + 21f, baseline, numberPaint)
                }
                game.pickups.forEach { pickup ->
                    val center = Offset(pickup.x.toFloat(), (pickup.y + rowShift).toFloat())
                    drawCircle(Ink, 8f, center, style = Stroke(2f))
                    drawLine(Ink, center - Offset(4f, 0f), center + Offset(4f, 0f), 2f)
                    drawLine(Ink, center - Offset(0f, 4f), center + Offset(0f, 4f), 2f)
                }
                if (phase == Phase.AIMING) {
                    if (aiming) {
                        val path = game.aimPath(angle)
                        if (path.size >= 2) {
                            val from = Offset(path[0].x.toFloat(), path[0].y.toFloat())
                            val hintEnd = AimGuide.end(path[0], path[1])
                            val to = Offset(hintEnd.x.toFloat(), hintEnd.y.toFloat())
                            drawLine(Color(0xFF647177), from, to, strokeWidth = 2.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(.1f, 10f)))
                        }
                    }
                    drawCircle(Ink, 5f, Offset(game.launchX.toFloat(), Board.LAUNCH_Y.toFloat()))
                }
                game.balls.forEach { drawCircle(Ink, Board.BALL_RADIUS.toFloat(), Offset(it.x.toFloat(), it.y.toFloat())) }
                if (model.motion) game.impacts.forEach { impact ->
                    val alpha = (1.0 - impact.age / .18).coerceIn(0.0, 1.0).toFloat()
                    drawCircle(Ink.copy(alpha = alpha), (4.0 + impact.age * 25).toFloat(), Offset(impact.x.toFloat(), impact.y.toFloat()), style = Stroke(1.3f))
                }
                val danger = game.blocks.any { it.row >= 8 }
                drawLine(if (danger) Color(0xFFBB3026) else Color(0xFF879297), Offset(Board.LEFT.toFloat(), Board.FLOOR.toFloat()), Offset(Board.RIGHT.toFloat(), Board.FLOOR.toFloat()), if (danger) 2.5f else 1.2f)
                drawRect(Ink, Offset(game.nextLaunchX.toFloat() - 10, 490f), Size(20f, 3f))
            }
        }
    }
}

@Composable
private fun PauseScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().weight(1f).background(Ink).padding(26.dp), verticalArrangement = Arrangement.Center) {
            Display("PAUSED", color = Color.White, size = 40)
            Spacer(Modifier.height(28.dp))
            Label("Round", color = Color.White)
            Display((model.engine?.round ?: 1).toString(), color = Teal, size = 88)
        }
        SpectrumEdge()
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Resume", model::resume, fill = Teal, textColor = Ink)
            TextAction("Settings", model::settings, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextAction("How to play", model::help, Modifier.weight(1f))
                TextAction("Home", model::home, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ResultsScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().weight(1f).background(Ink).padding(26.dp), verticalArrangement = Arrangement.Center) {
            Display("GAME\nOVER", color = Color.White, size = 48)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Label("Round", color = Color.White); Display((model.engine?.round ?: 1).toString(), color = Coral, size = 84) }
                Column(horizontalAlignment = Alignment.End) { Label("Best", color = Color.White); Display(model.best.toString(), color = Color.White, size = 40) }
            }
        }
        SpectrumEdge()
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Play again", model::newGame, fill = Coral, textColor = Ink)
            TextAction("Home", model::home, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction("Back", onBack, color = Color.White)
            Spacer(Modifier.width(14.dp))
            Display(title, color = Color.White, size = 28)
        }
        SpectrumEdge()
    }
}

@Composable
private fun HelpScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("How to play", model::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            HelpRule("Pull back & release", "Touch anywhere on the board, then pull down to aim upward. Pull left to shoot right, or right to shoot left. Release to shoot. Tap or return to your starting point to cancel.")
            HelpRule("Break the blocks", "Each hit takes one off a block. At zero, it disappears. Colors change with the number.")
            HelpRule("Add more balls", "Hit a circle marked + to add a ball to your next shot.")
            HelpRule("Stay above the line", "The blocks move down after every shot. A block reaching the bottom ends the run.")
            HelpRule("Set your pace", "Choose your normal speed in Settings. You can open Settings during play; the game waits while you change it.")
            HelpRule("Skip the wait", "Speed up uses the maximum speed for this shot only. Collect ends it early, keeping the hits and extra balls already earned.")
            HelpRule("Watch your next shot", "The marker below the board moves as soon as the first ball lands. That is where your next shot starts.")
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { Action("Got it", model::back) }
    }
}

@Composable
private fun HelpRule(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Label(title, size = 22, strong = true); Text(body, fontFamily = Archivo, fontSize = 16.sp, lineHeight = 24.sp, color = Ink) }
}

@Composable
private fun SettingsScreen(model: AppModel, onTestSound: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", model::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label("Game speed", size = 21, strong = true)
                    Label(String.format(Locale.ROOT, "%.1f×", model.preferredSpeed), Modifier.testTag("speed-value"), size = 21, strong = true)
                }
                Slider(value = model.preferredSpeed.toFloat(), onValueChange = { model.updatePreferredSpeed(it.toDouble()) },
                    onValueChangeFinished = model::finishSpeedChange,
                    valueRange = AppModel.MIN_SPEED.toFloat()..AppModel.MAX_SPEED.toFloat(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("speed-slider").semantics { contentDescription = "Game speed" },
                    colors = SliderDefaults.colors(thumbColor = Ink, activeTrackColor = Ink, inactiveTrackColor = Color(0xFFC5CDD0)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label("1×", size = 13)
                    Label("6×", size = 13)
                }
            }
            SettingRow("Sound", model.sound, model::toggleSound)
            OutlinedButton(onTestSound, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = model.sound,
                shape = RectangleShape, border = BorderStroke(1.dp, if (model.sound) Ink else Color(0xFF879297)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) {
                Text("Test sound", fontFamily = Archivo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            SettingRow("Vibration", model.haptics, model::toggleHaptics)
            SettingRow("Animations", model.motion, model::toggleMotion)
            Spacer(Modifier.height(22.dp))
            TextAction("About & licenses", model::licenses, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).border(1.dp, Ink).clickable(onClickLabel = "Toggle $title", onClick = toggle).padding(horizontal = 12.dp).semantics(mergeDescendants = true) { stateDescription = if (checked) "On" else "Off" }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Label(title, size = 21, strong = true)
        Box(Modifier.width(64.dp).height(38.dp).background(if (checked) Teal else Color(0xFFDDE1E3)).border(1.dp, Ink), contentAlignment = Alignment.Center) { Label(if (checked) "On" else "Off", strong = true) }
    }
}

@Composable
private fun LicensesScreen(model: AppModel) {
    val context = LocalContext.current
    val licenses = remember { context.resources.openRawResource(R.raw.font_licenses).bufferedReader().use { it.readText() } }
    val audioCredit = remember { context.resources.openRawResource(R.raw.audio_credit).bufferedReader().use { it.readText() } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("About", model::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Display("REBOUND", size = 32)
            Label("Version ${BuildConfig.VERSION_NAME}")
            Label("An offline ball-and-block game. No ads, accounts, tracking, or network access. Progress and settings stay on this device.")
            Label("Original game code and graphics. Not affiliated with Ballz or Ketchapp.")
            Label(audioCredit)
            Label("Fonts: Archivo and Archivo Black, by the Archivo Project Authors. SIL Open Font License.", strong = true)
            Label("AndroidX and Material icons: Apache License 2.0. Kotlin: Apache License 2.0.")
            Text(licenses, fontFamily = Archivo, fontSize = 12.sp, lineHeight = 18.sp, color = Ink)
        }
    }
}