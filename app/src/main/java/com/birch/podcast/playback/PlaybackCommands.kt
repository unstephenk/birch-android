package com.birch.podcast.playback

/**
 * Custom session commands for PlaybackService.
 */
object PlaybackCommands {
  const val ACTION_SET_BOOST = "birch.SET_BOOST"
  const val ACTION_SET_SKIP_SILENCE = "birch.SET_SKIP_SILENCE"

  // Notification actions
  const val ACTION_CYCLE_SPEED = "birch.CYCLE_SPEED"
  const val ACTION_TOGGLE_FAVORITE_EPISODE = "birch.TOGGLE_FAVORITE_EPISODE"

  const val EXTRA_ENABLED = "enabled"
}
