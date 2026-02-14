package com.birch.podcast.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

// Legacy key (kept for migration)
private val KEY_DARK = booleanPreferencesKey("dark_theme")

private val KEY_THEME_MODE = intPreferencesKey("theme_mode")

enum class ThemeMode(val value: Int) {
  SYSTEM(0),
  LIGHT(1),
  DARK(2);

  companion object {
    fun fromValue(v: Int?): ThemeMode? = when (v) {
      0 -> SYSTEM
      1 -> LIGHT
      2 -> DARK
      else -> null
    }
  }
}

class ThemePrefs(private val context: Context) {
  /** Preferred theme mode. Defaults to SYSTEM if unset. */
  val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
    val mode = ThemeMode.fromValue(prefs[KEY_THEME_MODE])
    if (mode != null) return@map mode

    // Migrate from legacy boolean, if present.
    when (prefs[KEY_DARK]) {
      true -> ThemeMode.DARK
      false -> ThemeMode.LIGHT
      null -> ThemeMode.SYSTEM
    }
  }

  /** Convenience: null means follow system. */
  val darkTheme: Flow<Boolean?> = themeMode.map {
    when (it) {
      ThemeMode.SYSTEM -> null
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit {
      it[KEY_THEME_MODE] = mode.value
      it.remove(KEY_DARK) // clear legacy key to avoid ambiguity
    }
  }

  suspend fun setDarkTheme(enabled: Boolean) {
    setThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
  }
}
