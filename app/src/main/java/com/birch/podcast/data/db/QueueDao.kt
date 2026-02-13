package com.birch.podcast.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
  @Query("SELECT * FROM queue_items ORDER BY position ASC")
  fun observe(): Flow<List<QueueItemEntity>>

  @Query("SELECT * FROM queue_items ORDER BY position ASC")
  suspend fun list(): List<QueueItemEntity>

  @Query("SELECT * FROM queue_items ORDER BY position ASC LIMIT 1")
  suspend fun peek(): QueueItemEntity?

  @Query("SELECT COALESCE(MAX(position), 0) FROM queue_items")
  suspend fun maxPosition(): Long?

  @Query("SELECT COALESCE(MIN(position), 0) FROM queue_items")
  suspend fun minPosition(): Long?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(item: QueueItemEntity)

  @Query("UPDATE queue_items SET position = :position WHERE id = :id")
  suspend fun updatePosition(id: Long, position: Long)

  @Query("DELETE FROM queue_items WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM queue_items")
  suspend fun clear()

  @Query("SELECT COUNT(*) FROM queue_items WHERE episodeGuid = :guid")
  suspend fun countByGuid(guid: String): Int
}
