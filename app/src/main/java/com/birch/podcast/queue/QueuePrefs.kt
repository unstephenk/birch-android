package com.birch.podcast.queue

import android.content.Context

object QueuePrefs {
  private const val PREFS = "queue_prefs"
  private const val KEY_AUTO_ADD_PREFIX = "auto_add_newest_unplayed_"

  fun autoAddNewestUnplayed(context: Context, podcastId: Long, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_AUTO_ADD_PREFIX + podcastId, default)
  }

  fun setAutoAddNewestUnplayed(context: Context, podcastId: Long, enabled: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_AUTO_ADD_PREFIX + podcastId, enabled).apply()
  }
}
