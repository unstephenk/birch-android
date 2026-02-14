package com.birch.podcast.ui

fun fmtBytes(b: Long): String {
  val kb = 1024.0
  val mb = kb * 1024.0
  val gb = mb * 1024.0
  val v = b.toDouble().coerceAtLeast(0.0)
  return when {
    v >= gb -> "%.1fGB".format(v / gb)
    v >= mb -> "%.1fMB".format(v / mb)
    v >= kb -> "%.0fKB".format(v / kb)
    else -> "${b}B"
  }
}

fun fmtEta(sec: Long): String {
  val s = sec.coerceAtLeast(0)
  val h = s / 3600
  val m = (s % 3600) / 60
  val r = s % 60
  return when {
    h > 0 -> "${h}h ${m}m"
    m > 0 -> "${m}m ${r}s"
    else -> "${r}s"
  }
}

fun fmtMaybeSize(bytes: Long?): String? {
  if (bytes == null || bytes <= 0L) return null
  return fmtBytes(bytes)
}

fun fmtRemainingMs(durationMs: Long, positionMs: Long): String? {
  if (durationMs <= 0L) return null
  val remSec = ((durationMs - positionMs).coerceAtLeast(0L)) / 1000L
  return fmtEta(remSec)
}
