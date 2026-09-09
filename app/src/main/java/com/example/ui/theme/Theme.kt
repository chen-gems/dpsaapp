package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF60A5FA),
        onPrimary = Color(0xFF0F172A),
        primaryContainer = Color(0xFF1E3A8A),
        onPrimaryContainer = Color(0xFFDBEAFE),
        secondary = Color(0xFF2DD4BF),
        onSecondary = Color(0xFF0F172A),
        tertiary = Color(0xFFFBBF24),
        background = SlateBackgroundDark,
        surface = SlateSurfaceDark,
        surfaceVariant = SlateSurfaceVariantDark,
        outline = SlateBorderDark
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PrimaryBlue,
        onPrimary = Color.White,
        primaryContainer = PrimaryBlueContainer,
        onPrimaryContainer = OnPrimaryBlueContainer,
        secondary = SecondaryTeal,
        onSecondary = Color.White,
        secondaryContainer = SecondaryTealContainer,
        onSecondaryContainer = OnSecondaryTealContainer,
        tertiary = AccentAmber,
        background = SlateBackgroundLight,
        surface = SlateSurfaceLight,
        surfaceVariant = SlateSurfaceVariantLight,
        outline = SlateBorderLight
    )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
