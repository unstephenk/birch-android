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
  private const val KEY_SKIP_SILENCE = "skip_silence"
  private const val KEY_BOOST_VOLUME = "boost_volume"
  private const val KEY_SPEED_PODCAST_PREFIX = "speed_podcast_"
  private const val KEY_PITCH_PODCAST_PREFIX = "pitch_podcast_"
  private const val KEY_TRIM_INTRO_MS_PREFIX = "trim_intro_ms_"
  private const val KEY_TRIM_OUTRO_MS_PREFIX = "trim_outro_ms_"
  private const val KEY_SKIP_BACK_SEC = "skip_back_sec"
  private const val KEY_SKIP_FORWARD_SEC = "skip_forward_sec"

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

  fun getSkipSilence(context: Context, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_SKIP_SILENCE, default)
  }

  fun setSkipSilence(context: Context, enabled: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
  }

  fun getBoostVolume(context: Context, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_BOOST_VOLUME, default)
  }

  fun setBoostVolume(context: Context, enabled: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_BOOST_VOLUME, enabled).apply()
  }

  fun getSpeedForPodcast(context: Context, podcastId: Long, default: Float = getSpeed(context)): Float {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getFloat(KEY_SPEED_PODCAST_PREFIX + podcastId, default)
  }

  fun setSpeedForPodcast(context: Context, podcastId: Long, speed: Float) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putFloat(KEY_SPEED_PODCAST_PREFIX + podcastId, speed).apply()
  }

  fun getPitchForPodcast(context: Context, podcastId: Long, default: Float = getPitch(context)): Float {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getFloat(KEY_PITCH_PODCAST_PREFIX + podcastId, default)
  }

  fun setPitchForPodcast(context: Context, podcastId: Long, pitch: Float) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putFloat(KEY_PITCH_PODCAST_PREFIX + podcastId, pitch).apply()
  }

  fun getTrimIntroMs(context: Context, podcastId: Long, default: Long = 0L): Long {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getLong(KEY_TRIM_INTRO_MS_PREFIX + podcastId, default)
  }

  fun setTrimIntroMs(context: Context, podcastId: Long, ms: Long) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putLong(KEY_TRIM_INTRO_MS_PREFIX + podcastId, ms.coerceAtLeast(0L)).apply()
  }

  fun getTrimOutroMs(context: Context, podcastId: Long, default: Long = 0L): Long {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getLong(KEY_TRIM_OUTRO_MS_PREFIX + podcastId, default)
  }

  fun setTrimOutroMs(context: Context, podcastId: Long, ms: Long) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putLong(KEY_TRIM_OUTRO_MS_PREFIX + podcastId, ms.coerceAtLeast(0L)).apply()
  }

  fun getSkipBackSec(context: Context, default: Int = 15): Int {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getInt(KEY_SKIP_BACK_SEC, default).coerceIn(5, 300)
  }

  fun setSkipBackSec(context: Context, sec: Int) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putInt(KEY_SKIP_BACK_SEC, sec.coerceIn(5, 300)).apply()
  }

  fun getSkipForwardSec(context: Context, default: Int = 30): Int {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getInt(KEY_SKIP_FORWARD_SEC, default).coerceIn(5, 600)
  }

  fun setSkipForwardSec(context: Context, sec: Int) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putInt(KEY_SKIP_FORWARD_SEC, sec.coerceIn(5, 600)).apply()
  }
}
