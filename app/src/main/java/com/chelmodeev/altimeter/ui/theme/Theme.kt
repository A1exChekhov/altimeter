package com.chelmodeev.altimeter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD1D6DC),
    onPrimary = Color(0xFF15181C),
    background = Color(0xFF0E1115),
    onBackground = Color(0xFFF0F2F4),
    surface = Color(0xFF171B20),
    onSurface = Color(0xFFF0F2F4),
    surfaceVariant = Color(0xFF22282E),
    onSurfaceVariant = Color(0xFFA8B0B8),
    outline = Color(0x33FFFFFF),
    secondaryContainer = Color(0xFF292F36),
    onSecondaryContainer = Color(0xFFF0F2F4),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4E5964),
    onPrimary = Color.White,
    background = Color(0xFFF4F4F1),
    onBackground = Color(0xFF17191C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17191C),
    surfaceVariant = Color(0xFFE8E9E7),
    onSurfaceVariant = Color(0xFF646B72),
    outline = Color(0x22000000),
    secondaryContainer = Color(0xFFE1E3E4),
    onSecondaryContainer = Color(0xFF17191C),
)

private val ThinTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Light),
    displayMedium = TextStyle(fontWeight = FontWeight.Light),
    displaySmall = TextStyle(fontWeight = FontWeight.Light),
    headlineLarge = TextStyle(fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontWeight = FontWeight.Normal),
    titleSmall = TextStyle(fontWeight = FontWeight.Normal),
    bodyLarge = TextStyle(fontWeight = FontWeight.Light),
    bodyMedium = TextStyle(fontWeight = FontWeight.Light),
    bodySmall = TextStyle(fontWeight = FontWeight.Light),
    labelLarge = TextStyle(fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontWeight = FontWeight.Normal),
)

/** Единый спокойный акцент; высота больше не раскрашивает интерфейс по зонам. */
fun zoneAccent(altitude: Double?): Color = Color(0xFFB8C0C8)

@Composable
fun AltimeterTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ThinTypography,
        content = content,
    )
}
