package com.nicgames.rebound.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.nicgames.rebound.R

val Ink = Color(0xFF243036)
val Field = Color(0xFFF2F3F4)
val Coral = Color(0xFFF27C7C)
val Orange = Color(0xFFF5AD65)
val Yellow = Color(0xFFE9D363)
val Green = Color(0xFF9ACC8B)
val Teal = Color(0xFF62C4B9)
val Violet = Color(0xFFAB97D8)
val DeepViolet = Color(0xFF7354A2)
val Spectrum = listOf(Coral, Orange, Yellow, Green, Teal, Violet)
val Archivo = FontFamily(Font(R.font.archivo_regular), Font(R.font.archivo_semibold, FontWeight.SemiBold))
val ArchivoBlack = FontFamily(Font(R.font.archivo_black, FontWeight.Black))

fun blockColor(hits: Int): Color = when {
    hits < 10 -> Coral
    hits < 20 -> Orange
    hits < 30 -> Yellow
    hits < 40 -> Green
    hits < 60 -> Teal
    hits < 100 -> Violet
    else -> DeepViolet
}