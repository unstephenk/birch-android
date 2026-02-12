package com.birch.podcast.rss

import android.util.Xml
import com.birch.podcast.model.Episode
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.security.MessageDigest

/**
 * Despite the URL name, https://rss.infowars.com/Alex-mobile.html is HTML, not RSS.
 * We support:
 * - HTML index page (primary)
 * - RSS/Atom (fallback) for future compatibility
 */
class RssClient(
  private val http: OkHttpClient = OkHttpClient()
) {
  suspend fun fetchEpisodes(feedUrl: String): List<Episode> {
    val req = Request.Builder()
      .url(feedUrl)
      .header("User-Agent", "Birch/0.1")
      .build()

    http.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
      val body = resp.body ?: throw IllegalStateException("Empty body")
      val ct = resp.header("content-type")?.lowercase().orEmpty()

      // Read as text once (works for both HTML and XML).
      val text = body.string()

      return when {
        ct.contains("text/html") || text.trimStart().startsWith("<!doctype", true) || text.contains("<html", true) -> {
          parseHtmlIndex(text)
        }
        else -> {
          // fallback parse as XML
          parseXml(text.byteInputStream())
        }
      }
    }
  }

  private fun parseHtmlIndex(html: String): List<Episode> {
    val doc = Jsoup.parse(html)
    val links = doc.select("a[href]")

    val episodes = mutableListOf<Episode>()

    for (a in links) {
      val href = a.attr("href")
      val title = a.text().trim()
      if (href.isNullOrBlank() || title.isBlank()) continue

      // Only keep the mp3 episode links
      if (!href.lowercase().endsWith(".mp3")) continue
      if (!href.contains("_alex", ignoreCase = true)) continue

      val audioUrl = normalizeAudioUrl(href)
      val stable = audioUrl
      episodes += Episode(
        id = sha1(stable),
        title = title,
        pubDateRaw = null,
        audioUrl = audioUrl
      )
    }

    return episodes
  }

  private fun normalizeAudioUrl(url: String): String {
    // Android 11 blocks cleartext by default; the feed uses http:// links.
    // Prefer https if it's the same host.
    return when {
      url.startsWith("http://rss.infowars.com/", ignoreCase = true) ->
        "https://rss.infowars.com/" + url.removePrefix("http://rss.infowars.com/")
      url.startsWith("http://", ignoreCase = true) -> url.replaceFirst("http://", "https://")
      else -> url
    }
  }

  private fun parseXml(input: java.io.InputStream): List<Episode> {
    val parser = Xml.newPullParser().apply {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
      setInput(input, null)
    }

    val episodes = mutableListOf<Episode>()
    var event = parser.eventType
    var inItem = false

    var title: String? = null
    var pubDate: String? = null
    var enclosureUrl: String? = null
    var guid: String? = null

    fun flushItem() {
      val t = title?.trim().orEmpty()
      val url = enclosureUrl?.trim().orEmpty()
      if (t.isNotBlank() && url.isNotBlank()) {
        val stable = (guid?.trim()?.takeIf { it.isNotBlank() } ?: url)
        episodes += Episode(
          id = sha1(stable),
          title = t,
          pubDateRaw = pubDate?.trim(),
          audioUrl = url
        )
      }
      title = null
      pubDate = null
      enclosureUrl = null
      guid = null
    }

    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          val name = parser.name ?: ""
          if (name.equals("item", ignoreCase = true)) {
            inItem = true
          } else if (inItem) {
            when {
              name.equals("title", ignoreCase = true) -> title = readText(parser)
              name.equals("pubDate", ignoreCase = true) -> pubDate = readText(parser)
              name.equals("guid", ignoreCase = true) -> guid = readText(parser)
              name.equals("enclosure", ignoreCase = true) -> {
                enclosureUrl = parser.getAttributeValue(null, "url")
              }
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

    return episodes
  }

  private fun readText(parser: XmlPullParser): String? {
    var result: String? = null
    if (parser.next() == XmlPullParser.TEXT) {
      result = parser.text
      parser.nextTag()
    }
    return result
  }

  private fun sha1(s: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
