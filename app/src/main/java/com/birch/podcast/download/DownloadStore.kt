package com.birch.podcast.download

import android.content.Context

class DownloadStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("birch_downloads", Context.MODE_PRIVATE)

  fun getLocalPath(episodeId: String): String? = prefs.getString("path_$episodeId", null)

  fun setLocalPath(episodeId: String, path: String?) {
    val e = prefs.edit()
    if (path == null) e.remove("path_$episodeId") else e.putString("path_$episodeId", path)
    e.apply()
  }

  fun isDownloaded(episodeId: String): Boolean = getLocalPath(episodeId) != null
}
