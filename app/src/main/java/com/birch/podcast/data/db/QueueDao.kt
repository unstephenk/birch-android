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

  @Query("SELECT * FROM queue_items ORDER BY position ASC LIMIT 1")
  suspend fun peek(): QueueItemEntity?

  @Query("SELECT COALESCE(MAX(position), 0) FROM queue_items")
  suspend fun maxPosition(): Long?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(item: QueueItemEntity)

  @Query("DELETE FROM queue_items WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM queue_items")
  suspend fun clear()
}
