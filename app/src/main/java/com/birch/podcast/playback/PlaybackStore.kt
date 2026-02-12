package com.birch.podcast.playback

import android.content.Context

class PlaybackStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("birch_playback", Context.MODE_PRIVATE)

  fun getPositionMs(episodeId: String): Long = prefs.getLong("pos_$episodeId", 0L)

  fun setPositionMs(episodeId: String, posMs: Long) {
    prefs.edit().putLong("pos_$episodeId", posMs.coerceAtLeast(0L)).apply()
  }

  fun isCompleted(episodeId: String): Boolean = prefs.getBoolean("done_$episodeId", false)

  fun setCompleted(episodeId: String, completed: Boolean) {
    prefs.edit().putBoolean("done_$episodeId", completed).apply()
    if (completed) {
      // clear position when complete
      prefs.edit().remove("pos_$episodeId").apply()
    }
  }
}
