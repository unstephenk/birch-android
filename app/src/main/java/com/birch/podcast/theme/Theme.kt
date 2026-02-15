package com.birch.podcast.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Pocket Casts-inspired: deep charcoal surfaces + warm coral accent.
private val DarkColors = darkColorScheme(
  primary = pcCoral,
  secondary = pcCoral2,
  tertiary = pcCoral2,
  background = pcBgDark,
  surface = pcSurfaceDark,
  surfaceVariant = pcSurfaceVariantDark,
  outline = pcOutlineDark,
  outlineVariant = pcOutlineVariantDark,
  error = pcErrorDark,
  onError = pcOnErrorDark,
  onPrimary = pcOnCoral,
  onSecondary = pcOnCoral,
  onBackground = pcOnDark,
  onSurface = pcOnDark,
  onSurfaceVariant = pcOnDarkMuted,
)

private val LightColors = lightColorScheme(
  primary = pcCoral,
  secondary = pcCoral2,
  tertiary = pcCoral2,
  background = pcBgLight,
  surface = pcSurfaceLight,
  surfaceVariant = pcSurfaceVariantLight,
  outline = pcOutlineLight,
  outlineVariant = pcOutlineVariantLight,
  error = pcErrorLight,
  onError = pcOnErrorLight,
  onPrimary = pcOnCoral,
  onSecondary = pcOnCoral,
  onBackground = pcOnLight,
  onSurface = pcOnLight,
  onSurfaceVariant = pcOnLightMuted,
)

@Composable
fun BirchTheme(
  darkTheme: Boolean? = null,
  content: @Composable () -> Unit,
) {
  val useDark = darkTheme ?: isSystemInDarkTheme()
  MaterialTheme(
    colorScheme = if (useDark) DarkColors else LightColors,
    typography = PocketTypography,
    shapes = PocketShapes,
    content = content
  )
}
