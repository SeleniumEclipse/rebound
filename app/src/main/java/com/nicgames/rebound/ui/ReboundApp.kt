package com.nicgames.rebound.ui

import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
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
import com.nicgames.rebound.R
import com.nicgames.rebound.Screen
import com.nicgames.rebound.game.Board
import com.nicgames.rebound.game.Phase
import kotlin.math.atan2
import kotlin.math.min

@Composable
fun ReboundApp(model: AppModel, onHit: () -> Unit = {}) {
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
                    Screen.SETTINGS -> SettingsScreen(model)
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
    TextButton(onClick, modifier.heightIn(min = 48.dp), shape = RectangleShape, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
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
                    TextAction("New game", { confirmNew = true }, Modifier.align(Alignment.CenterHorizontally))
                } else Action("Play", model::newGame, fill = Coral, textColor = Ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextAction("How to play", model::help)
                    TextAction("Settings", model::settings)
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
        Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Display(game.round.toString(), Modifier.testTag("round"), Color.White, if (game.round < 1000) 42 else 32)
                Label("Round", Modifier.padding(bottom = 5.dp), Color.White, 14)
            }
            OutlinedButton(model::pause, Modifier.heightIn(min = 48.dp), shape = RectangleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF657378)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Canvas(Modifier.size(14.dp)) {
                    drawLine(Color.White, Offset(size.width * .28f, 1f), Offset(size.width * .28f, size.height - 1f), 2.dp.toPx())
                    drawLine(Color.White, Offset(size.width * .72f, 1f), Offset(size.width * .72f, size.height - 1f), 2.dp.toPx())
                }
                Spacer(Modifier.width(5.dp))
                Label("Pause", color = Color.White, size = 14, strong = true)
            }
        }
        SpectrumEdge()
        Box(Modifier.weight(1f).fillMaxWidth().background(Field), contentAlignment = Alignment.Center) {
            GameBoard(model, revision, Modifier.fillMaxSize())
            if (!model.helpSeen && phase == Phase.AIMING) Label("Drag to aim. Release to shoot.", Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp), size = 13)
        }
        Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 6.dp).heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size(8.dp)) { drawCircle(Color.White) }
                Label("${game.ballCount}", Modifier.testTag("ball-count"), Color.White, 23, strong = true)
                Label(if (game.ballCount == 1) "ball" else "balls", color = Color.White, size = 14)
            }
            if (phase == Phase.FIRING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (game.shotElapsed >= 4.0) TextAction("Collect", model::recall, color = Color.White)
                    TextAction("Speed ${model.speed}×", model::changeSpeed, color = Teal)
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
            val left = (size.width - Board.WIDTH * scale) / 2.0
            val top = (size.height - Board.HEIGHT * scale) / 2.0
            fun pointAim(p: Offset) {
                val x = (p.x - left) / scale
                val y = (p.y - top) / scale
                model.aim(atan2(minOf(-12.0, y - Board.LAUNCH_Y), x - game.launchX))
            }
            pointAim(down.position)
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
                // A subtle side boundary makes the actual wall locations honest.
                drawLine(Color(0xFFD3D8DA), Offset(12f, 8f), Offset(12f, 482f), 1f)
                drawLine(Color(0xFFD3D8DA), Offset(348f, 8f), Offset(348f, 482f), 1f)
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
                            val to = Offset(path[1].x.toFloat(), path[1].y.toFloat())
                            drawLine(Color(0xFF647177), from, to, strokeWidth = 2.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(.1f, 10f)))
                            drawCircle(Ink, 4.5f, to, style = Stroke(1f))
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
                drawLine(if (danger) Color(0xFFBB3026) else Color(0xFF879297), Offset(12f, 482f), Offset(348f, 482f), if (danger) 2.5f else 1.2f)
                drawRect(Ink, Offset(game.launchX.toFloat() - 10, 490f), Size(20f, 3f))
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextAction("How to play", model::help)
                TextAction("Home", model::home)
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
            TextAction("Home", model::home, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Sharp.ArrowBack, "Back", tint = Color.White) }
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
            HelpRule("Aim & release", "Drag anywhere on the board to aim. Lift your finger to send the balls.")
            HelpRule("Break the blocks", "Each hit takes one off a block. At zero, it disappears. Colors change with the number.")
            HelpRule("Add more balls", "Hit a circle marked + to add a ball to your next shot.")
            HelpRule("Stay above the line", "The blocks move down after every shot. A block reaching the bottom ends the run.")
            HelpRule("Skip the wait", "Speed makes a volley faster. Collect ends it early, keeping the hits and extra balls already earned.")
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { Action("Got it", model::back) }
    }
}

@Composable
private fun HelpRule(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Label(title, size = 22, strong = true); Text(body, fontFamily = Archivo, fontSize = 16.sp, lineHeight = 24.sp, color = Ink) }
}

@Composable
private fun SettingsScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", model::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            SettingRow("Sound", model.sound, model::toggleSound)
            SettingRow("Vibration", model.haptics, model::toggleHaptics)
            SettingRow("Animations", model.motion, model::toggleMotion)
            Spacer(Modifier.height(22.dp))
            TextAction("About & licenses", model::licenses)
        }
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClickLabel = "Toggle $title", onClick = toggle).semantics(mergeDescendants = true) { stateDescription = if (checked) "On" else "Off" }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Label(title, size = 21, strong = true)
        Box(Modifier.width(64.dp).height(38.dp).background(if (checked) Teal else Color(0xFFDDE1E3)).border(1.dp, Ink), contentAlignment = Alignment.Center) { Label(if (checked) "On" else "Off", strong = true) }
    }
}

@Composable
private fun LicensesScreen(model: AppModel) {
    val context = LocalContext.current
    val licenses = remember { context.resources.openRawResource(R.raw.font_licenses).bufferedReader().use { it.readText() } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("About", model::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Display("REBOUND", size = 32)
            Label("Version 1.0.0")
            Label("An offline ball-and-block game. No ads, accounts, tracking, or network access. Progress and settings stay on this device.")
            Label("Original game code and graphics. Not affiliated with Ballz or Ketchapp.")
            Label("Fonts: Archivo and Archivo Black, by the Archivo Project Authors. SIL Open Font License.", strong = true)
            Label("AndroidX and Material icons: Apache License 2.0. Kotlin: Apache License 2.0.")
            Text(licenses, fontFamily = Archivo, fontSize = 12.sp, lineHeight = 18.sp, color = Ink)
        }
    }
}