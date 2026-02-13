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
  fun observeQueue() = db.queue().observe()

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
      )
    }
    db.episodes().insertAll(episodes)
    db.podcasts().setLastRefresh(podcastId, System.currentTimeMillis())

    return podcastId
  }

  suspend fun refreshPodcast(podcast: PodcastEntity) {
    val parsed = fetchAndParse(podcast.feedUrl)

    val episodes = parsed.episodes.map { pe ->
      EpisodeEntity(
        podcastId = podcast.id,
        guid = pe.guid,
        title = pe.title,
        summary = pe.summary,
        audioUrl = pe.audioUrl,
        publishedAtMs = pe.publishedAtMs,
      )
    }

    db.episodes().insertAll(episodes)
    db.podcasts().setLastRefresh(podcast.id, System.currentTimeMillis())
  }

  suspend fun unsubscribe(podcast: PodcastEntity) {
    db.episodes().deleteForPodcast(podcast.id)
    db.podcasts().delete(podcast.id)
  }

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

  suspend fun dequeueNext(): QueueItemEntity? {
    val next = db.queue().peek() ?: return null
    db.queue().delete(next.id)
    return next
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
