package com.birch.podcast.downloads

import android.content.Context

/**
 * Simple SharedPreferences-backed settings for downloads.
 */
object DownloadPrefs {
  private const val PREFS = "download_prefs"
  private const val KEY_WIFI_ONLY = "wifi_only"
  private const val KEY_SHOW_NOTIFICATION = "show_system_notification"
  private const val KEY_AUTO_DELETE_ON_PLAYED = "auto_delete_on_played"
  private const val KEY_CHARGING_ONLY = "charging_only"
  private const val KEY_AUTO_DELETE_DAYS = "auto_delete_days"
  private const val KEY_SHOW_NETWORK_WARNINGS = "show_network_warnings"

  fun wifiOnly(context: Context, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_WIFI_ONLY, default)
  }

  fun setWifiOnly(context: Context, enabled: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
  }

  fun showSystemNotification(context: Context, default: Boolean = true): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_SHOW_NOTIFICATION, default)
  }

  fun setShowSystemNotification(context: Context, enabled: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_SHOW_NOTIFICATION, enabled).apply()
  }

  fun autoDeleteOnPlayed(context: Context, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_AUTO_DELETE_ON_PLAYED, default)
  }

  fun setAutoDeleteOnPlayed(context: Context, enabled: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_AUTO_DELETE_ON_PLAYED, enabled).apply()
  }

  fun chargingOnly(context: Context, default: Boolean = false): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_CHARGING_ONLY, default)
  }

  fun setChargingOnly(context: Context, enabled: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_CHARGING_ONLY, enabled).apply()
  }

  /**
   * If >0, delete played downloads where lastPlayedAtMs is older than N days.
   */
  fun autoDeleteDays(context: Context, default: Int = 0): Int {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getInt(KEY_AUTO_DELETE_DAYS, default)
  }

  fun setAutoDeleteDays(context: Context, days: Int) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putInt(KEY_AUTO_DELETE_DAYS, days.coerceAtLeast(0)).apply()
  }

  fun showNetworkWarnings(context: Context, default: Boolean = true): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_SHOW_NETWORK_WARNINGS, default)
  }

  fun setShowNetworkWarnings(context: Context, enabled: Boolean) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_SHOW_NETWORK_WARNINGS, enabled).apply()
  }
}
