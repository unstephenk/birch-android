package com.birch.podcast.downloads

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DownloadsCleanupScheduler {
  private const val UNIQUE_NAME = "downloads_cleanup"

  fun sync(context: Context) {
    val days = DownloadPrefs.autoDeleteDays(context, default = 0)
    val wm = WorkManager.getInstance(context)

    if (days <= 0) {
      wm.cancelUniqueWork(UNIQUE_NAME)
      return
    }

    val req = PeriodicWorkRequestBuilder<DownloadsCleanupWorker>(12, TimeUnit.HOURS)
      .addTag(UNIQUE_NAME)
      .build()

    wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
  }
}
