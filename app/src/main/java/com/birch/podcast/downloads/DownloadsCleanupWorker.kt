package com.birch.podcast.downloads

import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.birch.podcast.data.db.AppDatabase
import com.birch.podcast.data.repo.PodcastRepository

class DownloadsCleanupWorker(
  appContext: android.content.Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val days = DownloadPrefs.autoDeleteDays(applicationContext, default = 0)
    if (days <= 0) return Result.success()

    val olderThan = System.currentTimeMillis() - (days.toLong() * 24L * 60L * 60L * 1000L)

    val db = AppDatabase.get(applicationContext)
    val repo = PodcastRepository(db)

    return try {
      val candidates = repo.listPlayedDownloadsOlderThan(olderThan)
      candidates.forEach { ep ->
        val local = ep.localFileUri
        if (!local.isNullOrBlank()) {
          runCatching {
            val uri = Uri.parse(local)
            applicationContext.contentResolver.delete(uri, null, null)
          }
        }
        repo.clearEpisodeDownload(ep.guid)
        repo.setEpisodeDownloadStatus(ep.guid, null, null)
      }
      Result.success()
    } catch (_: Throwable) {
      Result.retry()
    }
  }
}
