package com.birch.podcast.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.birch.podcast.theme.BirchTheme

@Preview(name = "Settings (dark)")
@Composable
private fun PreviewSettingsDark() {
  BirchTheme(darkTheme = true) {
    SettingsScreen(onBack = {})
  }
}

@Preview(name = "Settings (light)")
@Composable
private fun PreviewSettingsLight() {
  BirchTheme(darkTheme = false) {
    SettingsScreen(onBack = {})
  }
}
