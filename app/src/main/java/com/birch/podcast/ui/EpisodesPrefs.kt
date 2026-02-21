package com.birch.podcast.ui

import android.content.Context

object EpisodesPrefs {
  private const val PREFS = "episodes_prefs"
  private const val KEY_HIDE_PLAYED = "hide_played"
  private const val KEY_HIDE_PLAYED_PODCAST_PREFIX = "hide_played_podcast_"
  private const val KEY_FILTER_PODCAST_PREFIX = "filter_podcast_"
  private const val KEY_QUERY_PODCAST_PREFIX = "query_podcast_"

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

  /** 0=All, 1=Unplayed, 2=Downloaded */
  fun episodeFilterForPodcast(context: Context, podcastId: Long, default: Int = 0): Int {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getInt(KEY_FILTER_PODCAST_PREFIX + podcastId, default)
  }

  fun setEpisodeFilterForPodcast(context: Context, podcastId: Long, filter: Int) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putInt(KEY_FILTER_PODCAST_PREFIX + podcastId, filter).apply()
  }

  fun episodeQueryForPodcast(context: Context, podcastId: Long, default: String = ""): String {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getString(KEY_QUERY_PODCAST_PREFIX + podcastId, default) ?: default
  }

  fun setEpisodeQueryForPodcast(context: Context, podcastId: Long, query: String) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putString(KEY_QUERY_PODCAST_PREFIX + podcastId, query).apply()
  }
}
