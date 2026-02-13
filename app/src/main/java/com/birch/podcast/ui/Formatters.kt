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
