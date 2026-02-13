package com.birch.podcast.ui

import android.content.Context

object EpisodesPrefs {
  private const val PREFS = "episodes_prefs"
  private const val KEY_HIDE_PLAYED = "hide_played"

  fun hidePlayed(context: Context, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_HIDE_PLAYED, default)
  }

  fun setHidePlayed(context: Context, hide: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_HIDE_PLAYED, hide).apply()
  }
}
