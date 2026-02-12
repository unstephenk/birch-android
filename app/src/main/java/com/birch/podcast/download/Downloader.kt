package com.birch.podcast.download

import android.content.Context
import com.birch.podcast.model.Episode
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

class Downloader(
  private val context: Context,
  private val http: OkHttpClient = OkHttpClient(),
) {
  private val appContext = context.applicationContext

  fun episodeFile(episodeId: String): File {
    val dir = File(appContext.filesDir, "podcasts")
    dir.mkdirs()
    return File(dir, "$episodeId.mp3")
  }

  suspend fun download(
    episode: Episode,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
  ): File {
    val out = episodeFile(episode.id)
    val tmp = File(out.absolutePath + ".partial")

    val req = Request.Builder()
      .url(episode.audioUrl)
      .header("User-Agent", "Birch/0.1")
      .build()

    http.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
      val body = resp.body ?: throw IllegalStateException("Empty body")
      val total = body.contentLength().takeIf { it > 0 }

      body.byteStream().use { input ->
        tmp.outputStream().use { output ->
          val buf = ByteArray(DEFAULT_BUFFER_SIZE)
          var read: Int
          var downloaded = 0L
          while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            read = input.read(buf)
            if (read < 0) break
            output.write(buf, 0, read)
            downloaded += read
            onProgress(downloaded, total)
          }
        }
      }
    }

    if (out.exists()) out.delete()
    if (!tmp.renameTo(out)) {
      throw IllegalStateException("Failed to finalize download")
    }
    return out
  }

  fun delete(episodeId: String): Boolean {
    val f = episodeFile(episodeId)
    val partial = File(f.absolutePath + ".partial")
    var ok = true
    if (partial.exists()) ok = ok && partial.delete()
    if (f.exists()) ok = ok && f.delete()
    return ok
  }
}
