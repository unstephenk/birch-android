package com.birch.podcast.playback

import android.content.Context

/**
 * Tiny SharedPreferences wrapper for playback-related user prefs.
 * (Keeping it simple for now; can migrate to DataStore later.)
 */
object PlaybackPrefs {
  private const val PREFS = "playback_prefs"
  private const val KEY_SPEED = "playback_speed"
  private const val KEY_PITCH = "playback_pitch"

  fun getSpeed(context: Context, default: Float = 1.0f): Float {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getFloat(KEY_SPEED, default)
  }

  fun setSpeed(context: Context, speed: Float) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putFloat(KEY_SPEED, speed).apply()
  }

  fun getPitch(context: Context, default: Float = 1.0f): Float {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getFloat(KEY_PITCH, default)
  }

  fun setPitch(context: Context, pitch: Float) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putFloat(KEY_PITCH, pitch).apply()
  }
}
