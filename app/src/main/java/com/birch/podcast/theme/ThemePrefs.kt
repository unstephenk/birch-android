package com.birch.podcast.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

private val KEY_DARK = booleanPreferencesKey("dark_theme")

class ThemePrefs(private val context: Context) {
  val darkTheme: Flow<Boolean?> = context.dataStore.data.map { prefs ->
    // null means "follow system" (reserved for later); for now we always write true/false.
    prefs[KEY_DARK]
  }

  suspend fun setDarkTheme(enabled: Boolean) {
    context.dataStore.edit { it[KEY_DARK] = enabled }
  }
}
