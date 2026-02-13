package com.birch.podcast.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "playback_history",
  indices = [Index(value = ["playedAtMs"]), Index(value = ["episodeGuid"])],
)
data class PlaybackHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val podcastId: Long,
  val episodeGuid: String,
  val title: String,
  @ColumnInfo(defaultValue = "0") val playedAtMs: Long = System.currentTimeMillis(),
)
