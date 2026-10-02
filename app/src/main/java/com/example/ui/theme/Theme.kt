package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = TealAccent,
    onPrimary = DarkTeal,
    primaryContainer = DarkTeal,
    onPrimaryContainer = TealAccent,
    secondary = TealSecondary,
    onSecondary = Color.White,
    tertiary = TealAccent,
    background = TealDarkBg,
    surface = TealDarkCard,
    surfaceVariant = DarkTeal,
    onBackground = Color.White,
    onSurface = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealSurfaceVariant,
    onPrimaryContainer = DarkTeal,
    secondary = TealSecondary,
    onSecondary = Color.White,
    tertiary = TealAccent,
    background = TealLightBg,
    surface = TealCardBg,
    surfaceVariant = TealSurfaceVariant,
    onBackground = DarkTeal,
    onSurface = DarkTeal
  )

@Composable
fun CleanTrackTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  CleanTrackTheme(darkTheme = darkTheme, content = content)
}

