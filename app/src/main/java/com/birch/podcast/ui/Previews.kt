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

@Preview(name = "Now Playing (dark)")
@Composable
private fun PreviewNowPlayingDark() {
  BirchTheme(darkTheme = true) {
    NowPlayingScreen(
      title = "Episode Title That Might Be Kinda Long",
      podcastTitle = "Podcast Name",
      episodeDate = "Feb 21",
      isPlaying = true,
      darkTheme = true,
      onToggleTheme = {},
      positionMs = 10 * 60_000L,
      durationMs = 42 * 60_000L,
      playbackSpeed = 1.2f,
      playbackPitch = 1.0f,
      sleepTimerLabel = "15m",
      artworkUrl = null,
      chapters = listOf(
        ChapterUi("Intro", 0L),
        ChapterUi("Main", 120_000L),
      ),
      onBack = {},
      onOpenQueue = {},
      onSetSleepTimerOff = {},
      onSetSleepTimerMinutes = {},
      onSetSleepTimerEndOfEpisode = {},
      onSeekTo = {},
      onPlayPause = {},
      onRewind15 = {},
      onForward30 = {},
      onPlayFromBeginning = {},
      onMarkPlayed = {},
      onPlayNext = {},
      onPlayLast = {},
      onSetSpeed = {},
      onSetPitch = {},
      onSeekToChapter = {},
    )
  }
}

@Preview(name = "Now Playing (light)")
@Composable
private fun PreviewNowPlayingLight() {
  BirchTheme(darkTheme = false) {
    NowPlayingScreen(
      title = "Episode Title",
      podcastTitle = "Podcast Name",
      episodeDate = "Feb 21",
      isPlaying = false,
      darkTheme = false,
      onToggleTheme = {},
      positionMs = 0L,
      durationMs = 0L,
      playbackSpeed = 1.0f,
      playbackPitch = 1.0f,
      sleepTimerLabel = null,
      artworkUrl = null,
      chapters = emptyList(),
      onBack = {},
      onOpenQueue = {},
      onSetSleepTimerOff = {},
      onSetSleepTimerMinutes = {},
      onSetSleepTimerEndOfEpisode = {},
      onSeekTo = {},
      onPlayPause = {},
      onRewind15 = {},
      onForward30 = {},
      onPlayFromBeginning = {},
      onMarkPlayed = {},
      onPlayNext = {},
      onPlayLast = {},
      onSetSpeed = {},
      onSetPitch = {},
      onSeekToChapter = {},
    )
  }
}
