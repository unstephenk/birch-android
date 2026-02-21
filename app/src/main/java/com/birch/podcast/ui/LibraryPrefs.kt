package com.birch.podcast.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "settings")

private val KEY_LIBRARY_GRID = booleanPreferencesKey("library_grid")
private val KEY_LIBRARY_SORT = intPreferencesKey("library_sort")
private val KEY_LIBRARY_QUERY = stringPreferencesKey("library_query")

/** Keep the surface tiny: just enough to persist library UI choices. */
object LibraryPrefs {
  enum class SortMode(val value: Int) {
    RECENT(0),
    AZ(1);

    companion object {
      fun fromValue(v: Int?): SortMode = when (v) {
        1 -> AZ
        else -> RECENT
      }
    }
  }

  suspend fun isGrid(context: Context, default: Boolean = false): Boolean {
    val prefs = context.dataStore.data.first()
    return prefs[KEY_LIBRARY_GRID] ?: default
  }

  suspend fun setGrid(context: Context, enabled: Boolean) {
    context.dataStore.edit { it[KEY_LIBRARY_GRID] = enabled }
  }

  suspend fun sortMode(context: Context, default: SortMode = SortMode.RECENT): SortMode {
    val prefs = context.dataStore.data.first()
    return SortMode.fromValue(prefs[KEY_LIBRARY_SORT])
  }

  suspend fun setSortMode(context: Context, mode: SortMode) {
    context.dataStore.edit { it[KEY_LIBRARY_SORT] = mode.value }
  }

  suspend fun query(context: Context, default: String = ""): String {
    val prefs = context.dataStore.data.first()
    return prefs[KEY_LIBRARY_QUERY] ?: default
  }

  suspend fun setQuery(context: Context, query: String) {
    context.dataStore.edit { it[KEY_LIBRARY_QUERY] = query }
  }
}
