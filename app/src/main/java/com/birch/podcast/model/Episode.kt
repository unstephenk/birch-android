package com.birch.podcast.model

data class Episode(
  val id: String,
  val title: String,
  val pubDateRaw: String?,
  val audioUrl: String,
  val summary: String?,
)
