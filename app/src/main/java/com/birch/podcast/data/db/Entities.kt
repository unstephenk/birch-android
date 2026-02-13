package com.birch.podcast.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "podcasts",
  indices = [Index(value = ["feedUrl"], unique = true)]
)
data class PodcastEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val feedUrl: String,
  val title: String,
  val description: String? = null,
  val imageUrl: String? = null,
  val lastRefreshAtMs: Long? = null,
)

@Entity(
  tableName = "episodes",
  indices = [
    Index(value = ["podcastId", "guid"], unique = true),
    Index(value = ["podcastId", "publishedAtMs"])
  ]
)
data class EpisodeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val podcastId: Long,
  val guid: String,
  val title: String,
  val summary: String? = null,
  val audioUrl: String,
  val publishedAtMs: Long? = null,
  @ColumnInfo(defaultValue = "0") val createdAtMs: Long = System.currentTimeMillis(),
)
