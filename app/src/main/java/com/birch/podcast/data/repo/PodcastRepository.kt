package com.birch.podcast.data.repo

import com.birch.podcast.data.db.AppDatabase
import com.birch.podcast.data.db.EpisodeEntity
import com.birch.podcast.data.db.PodcastEntity
import com.birch.podcast.rss.PodcastFeedParser
import okhttp3.OkHttpClient
import okhttp3.Request

class PodcastRepository(
  private val db: AppDatabase,
  private val http: OkHttpClient = OkHttpClient(),
) {
  fun observePodcasts() = db.podcasts().observeAll()
  fun observeEpisodes(podcastId: Long) = db.episodes().observeByPodcast(podcastId)

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

  private suspend fun fetchAndParse(feedUrl: String): PodcastFeedParser.ParsedFeed {
    val req = Request.Builder()
      .url(feedUrl)
      .header("User-Agent", "Birch/0.1")
      .build()

    http.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
      val body = resp.body ?: throw IllegalStateException("Empty body")
      val bytes = body.bytes()
      return PodcastFeedParser.parse(feedUrl, bytes)
    }
  }
}
