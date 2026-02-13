package com.birch.podcast.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
  @Query("SELECT * FROM podcasts ORDER BY id DESC")
  fun observeAll(): Flow<List<PodcastEntity>>

  @Query("SELECT * FROM podcasts WHERE id = :id")
  suspend fun getById(id: Long): PodcastEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(podcast: PodcastEntity): Long

  @Query("UPDATE podcasts SET lastRefreshAtMs = :ts WHERE id = :id")
  suspend fun setLastRefresh(id: Long, ts: Long)
}

@Dao
interface EpisodeDao {
  @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAtMs DESC, id DESC")
  fun observeByPodcast(podcastId: Long): Flow<List<EpisodeEntity>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(episodes: List<EpisodeEntity>)

  @Query("SELECT COUNT(*) FROM episodes WHERE podcastId = :podcastId")
  suspend fun countForPodcast(podcastId: Long): Int

  @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
  suspend fun deleteForPodcast(podcastId: Long)
}

@Dao
interface PlaybackDao {
  @Query("SELECT positionMs FROM playback_state WHERE episodeGuid = :guid LIMIT 1")
  suspend fun getPositionMs(guid: String): Long?
}
