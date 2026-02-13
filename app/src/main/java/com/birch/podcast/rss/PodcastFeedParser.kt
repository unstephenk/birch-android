package com.birch.podcast.rss

import android.util.Xml
import com.birch.podcast.data.db.EpisodeEntity
import com.birch.podcast.data.db.PodcastEntity
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal podcast RSS parser.
 *
 * Supports RSS2 + iTunes-ish fields:
 * - channel title/description
 * - item title/pubDate/guid/enclosure url
 * - item description / content:encoded / summary (cleaned to text)
 */
object PodcastFeedParser {

  data class ParsedFeed(
    val podcast: PodcastEntity,
    val episodes: List<ParsedEpisode>,
  )

  data class ParsedEpisode(
    val guid: String,
    val title: String,
    val audioUrl: String,
    val audioLengthBytes: Long?,
    val publishedAtMs: Long?,
    val summary: String?,
  )

  fun parse(feedUrl: String, xmlBytes: ByteArray): ParsedFeed {
    val parser = Xml.newPullParser().apply {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
      setInput(xmlBytes.inputStream(), null)
    }

    var event = parser.eventType
    var inItem = false

    var channelTitle: String? = null
    var channelDesc: String? = null

    var itemTitle: String? = null
    var itemPubDate: String? = null
    var itemEnclosureUrl: String? = null
    var itemEnclosureLength: Long? = null
    var itemGuid: String? = null
    var itemDescription: String? = null
    var itemContentEncoded: String? = null
    var itemSummary: String? = null

    val out = mutableListOf<ParsedEpisode>()

    fun readText(): String? {
      var result: String? = null
      if (parser.next() == XmlPullParser.TEXT) {
        result = parser.text
        parser.nextTag()
      }
      return result
    }

    fun parseDateMs(raw: String?): Long? {
      if (raw.isNullOrBlank()) return null
      return try {
        val zdt = ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
        zdt.toInstant().toEpochMilli()
      } catch (_: Throwable) {
        null
      }
    }

    fun cleanSummary(raw: String?): String? {
      if (raw.isNullOrBlank()) return null
      val txt = Jsoup.parse(raw).text().trim()
      return txt.takeIf { it.isNotBlank() }
    }

    fun flushItem() {
      val t = itemTitle?.trim().orEmpty()
      val enclosure = itemEnclosureUrl?.trim().orEmpty()
      if (t.isBlank() || enclosure.isBlank()) {
        // Not a playable podcast episode.
        itemTitle = null; itemPubDate = null; itemEnclosureUrl = null; itemGuid = null
        itemDescription = null; itemContentEncoded = null; itemSummary = null
        return
      }

      val guid = (itemGuid?.trim()?.takeIf { it.isNotBlank() } ?: enclosure)
      val sum = listOf(itemContentEncoded, itemDescription, itemSummary)
        .firstOrNull { !it.isNullOrBlank() }
        ?.let { cleanSummary(it) }

      out += ParsedEpisode(
        guid = guid,
        title = t,
        audioUrl = normalizeAudioUrl(enclosure),
        audioLengthBytes = itemEnclosureLength,
        publishedAtMs = parseDateMs(itemPubDate),
        summary = sum,
      )

      itemTitle = null; itemPubDate = null; itemEnclosureUrl = null; itemEnclosureLength = null; itemGuid = null
      itemDescription = null; itemContentEncoded = null; itemSummary = null
    }

    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          val name = parser.name ?: ""
          if (name.equals("item", ignoreCase = true)) {
            inItem = true
          } else if (inItem) {
            when {
              name.equals("title", ignoreCase = true) -> itemTitle = readText()
              name.equals("pubDate", ignoreCase = true) -> itemPubDate = readText()
              name.equals("guid", ignoreCase = true) -> itemGuid = readText()
              name.equals("description", ignoreCase = true) -> itemDescription = readText()
              name.equals("summary", ignoreCase = true) -> itemSummary = readText()
              name.equals("encoded", ignoreCase = true) || name.equals("content:encoded", ignoreCase = true) -> itemContentEncoded = readText()
              name.equals("enclosure", ignoreCase = true) -> {
                itemEnclosureUrl = parser.getAttributeValue(null, "url")
                itemEnclosureLength = parser.getAttributeValue(null, "length")?.toLongOrNull()
              }
            }
          } else {
            // channel-level
            when {
              name.equals("title", ignoreCase = true) -> if (channelTitle == null) channelTitle = readText()
              name.equals("description", ignoreCase = true) -> if (channelDesc == null) channelDesc = readText()
            }
          }
        }
        XmlPullParser.END_TAG -> {
          val name = parser.name ?: ""
          if (name.equals("item", ignoreCase = true)) {
            flushItem()
            inItem = false
          }
        }
      }
      event = parser.next()
    }

    val podcast = PodcastEntity(
      feedUrl = feedUrl,
      title = channelTitle?.trim().takeIf { !it.isNullOrBlank() } ?: feedUrl,
      description = channelDesc?.trim(),
    )

    return ParsedFeed(podcast = podcast, episodes = out)
  }

  private fun normalizeAudioUrl(url: String): String {
    // Prefer https if it's the same host.
    return when {
      url.startsWith("http://rss.infowars.com/", ignoreCase = true) ->
        "https://rss.infowars.com/" + url.removePrefix("http://rss.infowars.com/")
      url.startsWith("http://", ignoreCase = true) -> url.replaceFirst("http://", "https://")
      else -> url
    }
  }
}
