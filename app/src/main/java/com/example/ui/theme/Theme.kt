package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
  primary = FigmaTeal,
  onPrimary = FigmaWhite,
  primaryContainer = FigmaSoftTeal,
  onPrimaryContainer = FigmaNavy,
  secondary = FigmaNavy,
  onSecondary = FigmaWhite,
  secondaryContainer = FigmaSoftTeal,
  onSecondaryContainer = FigmaNavy,
  tertiary = FigmaAmber,
  onTertiary = FigmaWhite,
  background = FigmaBackground,
  onBackground = FigmaInk,
  surface = FigmaWhite,
  onSurface = FigmaInk,
  surfaceVariant = FigmaBackground,
  onSurfaceVariant = FigmaMuted,
  outline = FigmaLine,
  outlineVariant = FigmaLine,
  error = FigmaRed,
  onError = FigmaWhite
)

private val DarkColorScheme = lightColorScheme( // Consistent light-dominant branding per Figma PDF
  primary = FigmaTeal,
  onPrimary = FigmaWhite,
  primaryContainer = FigmaSoftTeal,
  onPrimaryContainer = FigmaNavy,
  secondary = FigmaNavy,
  onSecondary = FigmaWhite,
  background = FigmaBackground,
  onBackground = FigmaInk,
  surface = FigmaWhite,
  onSurface = FigmaInk,
  surfaceVariant = FigmaBackground,
  onSurfaceVariant = FigmaMuted,
  outline = FigmaLine,
  error = FigmaRed,
  onError = FigmaWhite
)

@Composable
fun IceTrackTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Strictly preserve Figma brand palette
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        window.statusBarColor = colorScheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

// Backwards-compatible alias
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) = IceTrackTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
