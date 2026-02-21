package com.birch.podcast.data.repo

import com.birch.podcast.data.db.AppDatabase
import com.birch.podcast.data.db.EpisodeEntity
import com.birch.podcast.data.db.PodcastEntity
import com.birch.podcast.data.db.QueueItemEntity
import com.birch.podcast.rss.PodcastFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PodcastRepository(
  private val db: AppDatabase,
  private val http: OkHttpClient = OkHttpClient(),
) {
  fun observePodcasts() = db.podcasts().observeAll()
  fun observeEpisodes(podcastId: Long) = db.episodes().observeByPodcast(podcastId)
  fun observeDownloadingEpisodes() = db.episodes().observeDownloading()
  fun observeDownloadedEpisodes() = db.episodes().observeDownloaded()
  fun observeFailedDownloads() = db.episodes().observeFailedDownloads()
  fun observeQueue() = db.queue().observe()
  fun observePlaybackHistory(limit: Int = 50) = db.history().observeRecent(limit)

  suspend fun clearPlaybackHistory() {
    db.history().clearAll()
  }

  suspend fun deleteHistoryItem(id: Long) {
    db.history().deleteById(id)
  }

  suspend fun addPodcast(feedUrl: String): Long {
    // Fetch and parse first so we can save the real title.
    val parsed = fetchAndParse(feedUrl)
    val podcastId = db.podcasts().insert(parsed.podcast)

    val episodes = parsed.episodes.map { pe ->
      EpisodeEntity(
        podcastId = podcastId,
        guid = pe.guid,
        title = pe.title,
        summary = pe.summary,
        audioUrl = pe.audioUrl,
        publishedAtMs = pe.publishedAtMs,
        enclosureLengthBytes = pe.audioLengthBytes,
      )
    }
    db.episodes().insertAll(episodes)
    db.podcasts().setLastRefresh(podcastId, System.currentTimeMillis())

    return podcastId
  }

  suspend fun getPodcastById(id: Long) = db.podcasts().getById(id)

  suspend fun refreshPodcastById(podcastId: Long, autoQueueNewestUnplayed: Boolean = false) {
    val podcast = db.podcasts().getById(podcastId) ?: return
    refreshPodcast(podcast, autoQueueNewestUnplayed = autoQueueNewestUnplayed)
  }

  suspend fun deletePlayedEpisodes(podcastId: Long) {
    db.episodes().deletePlayedForPodcast(podcastId)
  }

  suspend fun markAllPlayed(podcastId: Long) {
    db.episodes().markAllPlayed(podcastId, playedAtMs = System.currentTimeMillis())
  }

  suspend fun markAllUnplayed(podcastId: Long) {
    db.episodes().markAllUnplayed(podcastId)
  }

  suspend fun refreshPodcast(podcast: PodcastEntity, autoQueueNewestUnplayed: Boolean = false) {
    val parsed = fetchAndParse(podcast.feedUrl)

    val episodes = parsed.episodes.map { pe ->
      EpisodeEntity(
        podcastId = podcast.id,
        guid = pe.guid,
        title = pe.title,
        summary = pe.summary,
        audioUrl = pe.audioUrl,
        publishedAtMs = pe.publishedAtMs,
        enclosureLengthBytes = pe.audioLengthBytes,
      )
    }

    db.episodes().insertAll(episodes)

    // Update channel metadata in case it changed (especially artwork).
    db.podcasts().updateMetadata(
      id = podcast.id,
      title = parsed.podcast.title,
      description = parsed.podcast.description,
      imageUrl = parsed.podcast.imageUrl,
    )
    db.podcasts().setLastRefresh(podcast.id, System.currentTimeMillis())

    if (autoQueueNewestUnplayed) {
      val newest = db.episodes().newestUnplayed(podcast.id)
      if (newest != null) {
        val exists = db.queue().countByGuid(newest.guid) > 0
        if (!exists) {
          enqueueLast(newest.title, newest.guid, newest.audioUrl)
        }
      }
    }
  }

  suspend fun unsubscribe(podcast: PodcastEntity) {
    db.episodes().deleteForPodcast(podcast.id)
    db.podcasts().delete(podcast.id)
  }

  suspend fun countEpisodes(podcastId: Long): Int = db.episodes().countForPodcast(podcastId)

  suspend fun countUnplayedEpisodes(podcastId: Long): Int = db.episodes().countUnplayedForPodcast(podcastId)

  suspend fun enqueue(title: String, guid: String, audioUrl: String) {
    val pos = (db.queue().maxPosition() ?: 0L) + 1L
    db.queue().insert(
      QueueItemEntity(
        episodeGuid = guid,
        title = title,
        audioUrl = audioUrl,
        position = pos,
      )
    )
  }

  suspend fun enqueueNext(title: String, guid: String, audioUrl: String) {
    val pos = (db.queue().minPosition() ?: 0L) - 1L
    db.queue().insert(
      QueueItemEntity(
        episodeGuid = guid,
        title = title,
        audioUrl = audioUrl,
        position = pos,
      )
    )
  }

  suspend fun enqueueLast(title: String, guid: String, audioUrl: String) = enqueue(title, guid, audioUrl)

  suspend fun getEpisodeByGuid(guid: String) = db.episodes().getByGuid(guid)

  suspend fun updateEpisodePlayback(
    guid: String,
    positionMs: Long,
    durationMs: Long,
    completed: Boolean,
  ) {
    db.episodes().updatePlayback(
      guid = guid,
      positionMs = positionMs,
      durationMs = durationMs,
      completed = if (completed) 1 else 0,
      playedAtMs = System.currentTimeMillis(),
    )
  }

  suspend fun setEpisodeDownloadId(guid: String, downloadId: Long) {
    db.episodes().setDownloadId(guid, downloadId)
  }

  suspend fun getEpisodeByDownloadId(downloadId: Long) = db.episodes().getByDownloadId(downloadId)

  suspend fun setEpisodeLocalFileUri(guid: String, uri: String?) {
    db.episodes().setLocalFileUri(guid, uri)
  }

  suspend fun clearEpisodeDownload(guid: String) {
    db.episodes().setDownloadId(guid, 0L)
    db.episodes().setLocalFileUri(guid, null)
  }

  suspend fun setEpisodeDownloadStatus(guid: String, status: String?, error: String?) {
    db.episodes().setDownloadStatus(guid, status, error)
  }

  suspend fun listPlayedDownloadsOlderThan(olderThanMs: Long): List<EpisodeEntity> {
    return db.episodes().listPlayedDownloadsOlderThan(olderThanMs)
  }

  suspend fun addToHistory(podcastId: Long, guid: String, title: String) {
    db.history().insert(
      com.birch.podcast.data.db.PlaybackHistoryEntity(
        podcastId = podcastId,
        episodeGuid = guid,
        title = title,
        playedAtMs = System.currentTimeMillis(),
      )
    )
  }

  suspend fun setEpisodeCompleted(guid: String, completed: Boolean) {
    val ep = db.episodes().getByGuid(guid)
    val pos = if (completed) (ep?.durationMs ?: 0L) else 0L
    db.episodes().setCompleted(
      guid = guid,
      completed = if (completed) 1 else 0,
      positionMs = pos,
      playedAtMs = if (completed) System.currentTimeMillis() else 0L,
    )
  }

  suspend fun dequeueNext(): QueueItemEntity? {
    val next = db.queue().peek() ?: return null
    db.queue().delete(next.id)
    return next
  }

  suspend fun removeQueueItem(id: Long) {
    db.queue().delete(id)
  }

  suspend fun moveQueueItemToTop(id: Long) {
    val items = db.queue().list()
    val idx = items.indexOfFirst { it.id == id }
    if (idx <= 0) return

    val reordered = buildList {
      add(items[idx])
      items.forEachIndexed { i, it -> if (i != idx) add(it) }
    }

    // Avoid unique index collisions on position.
    reordered.forEachIndexed { i, it -> db.queue().updatePosition(it.id, -(i + 1).toLong()) }
    reordered.forEachIndexed { i, it -> db.queue().updatePosition(it.id, (i + 1).toLong()) }
  }

  suspend fun moveQueueItemToBottom(id: Long) {
    val items = db.queue().list()
    val idx = items.indexOfFirst { it.id == id }
    if (idx == -1 || idx == items.lastIndex) return

    val reordered = buildList {
      items.forEachIndexed { i, it -> if (i != idx) add(it) }
      add(items[idx])
    }

    reordered.forEachIndexed { i, it -> db.queue().updatePosition(it.id, -(i + 1).toLong()) }
    reordered.forEachIndexed { i, it -> db.queue().updatePosition(it.id, (i + 1).toLong()) }
  }

  suspend fun clearQueue() {
    db.queue().clear()
  }

  suspend fun moveQueueItem(id: Long, delta: Int) {
    if (delta == 0) return
    val items = db.queue().list()
    val idx = items.indexOfFirst { it.id == id }
    if (idx == -1) return
    val newIdx = (idx + delta).coerceIn(0, items.lastIndex)
    if (newIdx == idx) return

    val a = items[idx]
    val b = items[newIdx]

    // Swap positions without violating the unique index on position.
    db.queue().updatePosition(a.id, -1L)
    db.queue().updatePosition(b.id, a.position)
    db.queue().updatePosition(a.id, b.position)
  }

  suspend fun moveQueueItemToIndex(fromIndex: Int, toIndex: Int) {
    val items = db.queue().list()
    if (items.isEmpty()) return

    val from = fromIndex.coerceIn(0, items.lastIndex)
    val to = toIndex.coerceIn(0, items.lastIndex)
    if (from == to) return

    val reordered = items.toMutableList()
    val item = reordered.removeAt(from)
    reordered.add(to, item)

    // Avoid unique index collisions on position.
    reordered.forEachIndexed { i, it -> db.queue().updatePosition(it.id, -(i + 1).toLong()) }
    reordered.forEachIndexed { i, it -> db.queue().updatePosition(it.id, (i + 1).toLong()) }
  }

  private suspend fun fetchAndParse(feedUrl: String): PodcastFeedParser.ParsedFeed = withContext(Dispatchers.IO) {
    val req = Request.Builder()
      .url(feedUrl)
      .header("User-Agent", "Birch/0.1")
      .build()

    http.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
      val body = resp.body ?: throw IllegalStateException("Empty body")
      val bytes = body.bytes()

      // Some sites return HTML (Cloudflare/WAF/captcha) even for .xml URLs.
      val ct = (resp.header("content-type") ?: "").lowercase()
      val head = runCatching {
        val n = minOf(bytes.size, 512)
        String(bytes, 0, n)
      }.getOrNull()?.lowercase().orEmpty()

      val looksHtml = ct.contains("text/html") || head.contains("<!doctype") || head.contains("<html") || head.contains("<div")
      if (looksHtml) {
        throw IllegalStateException(
          "That URL did not return RSS XML (it returned HTML). " +
            "This is usually a Cloudflare/WAF block. Try a different feed URL."
        )
      }

      PodcastFeedParser.parse(feedUrl, bytes)
    }
  }
}
