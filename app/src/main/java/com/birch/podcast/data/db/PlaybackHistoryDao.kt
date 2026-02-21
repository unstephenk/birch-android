package com.birch.podcast.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
  @Query("SELECT * FROM playback_history ORDER BY playedAtMs DESC, id DESC LIMIT :limit")
  fun observeRecent(limit: Int = 50): Flow<List<PlaybackHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(item: PlaybackHistoryEntity)

  @Query("DELETE FROM playback_history")
  suspend fun clearAll()

  @Query("DELETE FROM playback_history WHERE id = :id")
  suspend fun deleteById(id: Long)
}
