package com.birch.podcast.ui

import android.content.Context

object EpisodesPrefs {
  private const val PREFS = "episodes_prefs"
  private const val KEY_HIDE_PLAYED = "hide_played"
  private const val KEY_HIDE_PLAYED_PODCAST_PREFIX = "hide_played_podcast_"

  fun hidePlayed(context: Context, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_HIDE_PLAYED, default)
  }

  fun setHidePlayed(context: Context, hide: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_HIDE_PLAYED, hide).apply()
  }

  fun hidePlayedForPodcast(context: Context, podcastId: Long, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_HIDE_PLAYED_PODCAST_PREFIX + podcastId, default)
  }

  fun setHidePlayedForPodcast(context: Context, podcastId: Long, hide: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_HIDE_PLAYED_PODCAST_PREFIX + podcastId, hide).apply()
  }
}
