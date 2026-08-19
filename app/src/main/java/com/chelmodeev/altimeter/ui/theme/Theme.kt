package com.chelmodeev.altimeter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B1220)
val Surface1 = Color(0xFF121B2D)
val Surface2 = Color(0xFF18253A)
val OnInk = Color(0xFFE9EFF9)
val Subtle = Color(0xFF8FA3C2)
val AccentBlue = Color(0xFF7FB4FF)

private val DarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color(0xFF0A1526),
    background = Ink,
    onBackground = OnInk,
    surface = Surface1,
    onSurface = OnInk,
    surfaceVariant = Surface2,
    onSurfaceVariant = Subtle,
    outline = Color(0x1FFFFFFF),
    secondaryContainer = Color(0xFF1E3252),
    onSecondaryContainer = OnInk,
)

/** Цвет акцента по высотной зоне: море → лес → предгорья → горы → снег. */
fun zoneAccent(altitude: Double?): Color = when {
    altitude == null -> AccentBlue
    altitude < 0 -> Color(0xFF5C9DFF)
    altitude < 500 -> Color(0xFF6FCF97)
    altitude < 1500 -> Color(0xFF4DD0C4)
    altitude < 3000 -> Color(0xFFF2B94B)
    else -> Color(0xFFBFD9FF)
}

@Composable
fun AltimeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
