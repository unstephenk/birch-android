package com.birch.podcast.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a")

fun formatEpochMsShort(epochMs: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String? {
  if (epochMs == null) return null
  return dateFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zoneId))
}

fun formatEpochMsWithTime(epochMs: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String? {
  if (epochMs == null) return null
  return dateTimeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zoneId))
}

fun normalizePodcastTitle(title: String): String {
  val t = title.trim()
  // Common feed pattern: "Show Name - Publisher". Keep the show name.
  val beforeDash = t.substringBefore(" - ", missingDelimiterValue = t).trim()
  return if (beforeDash.isNotBlank()) beforeDash else t
}

fun fmtDurationMs(ms: Long): String {
  val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
