package com.birch.podcast.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "queue_items",
  indices = [Index(value = ["position"], unique = true)]
)
data class QueueItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val episodeGuid: String,
  val title: String,
  val audioUrl: String,
  val position: Long,
  val addedAtMs: Long = System.currentTimeMillis(),
)
