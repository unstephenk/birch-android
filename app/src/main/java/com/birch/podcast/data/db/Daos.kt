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

  @Query("DELETE FROM podcasts WHERE id = :id")
  suspend fun delete(id: Long)
}

@Dao
interface EpisodeDao {
  @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAtMs DESC, id DESC")
  fun observeByPodcast(podcastId: Long): Flow<List<EpisodeEntity>>

  @Query("SELECT * FROM episodes WHERE guid = :guid LIMIT 1")
  suspend fun getByGuid(guid: String): EpisodeEntity?

  @Query("SELECT * FROM episodes WHERE downloadId != 0 AND (localFileUri IS NULL OR localFileUri = '')")
  fun observeDownloading(): Flow<List<EpisodeEntity>>

  @Query("SELECT * FROM episodes WHERE localFileUri IS NOT NULL AND localFileUri != '' ORDER BY lastPlayedAtMs DESC, publishedAtMs DESC")
  fun observeDownloaded(): Flow<List<EpisodeEntity>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(episodes: List<EpisodeEntity>)

  @Query("SELECT COUNT(*) FROM episodes WHERE podcastId = :podcastId")
  suspend fun countForPodcast(podcastId: Long): Int

  @Query("SELECT COUNT(*) FROM episodes WHERE podcastId = :podcastId AND completed = 0")
  suspend fun countUnplayedForPodcast(podcastId: Long): Int

  @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
  suspend fun deleteForPodcast(podcastId: Long)

  @Query("DELETE FROM episodes WHERE podcastId = :podcastId AND completed = 1")
  suspend fun deletePlayedForPodcast(podcastId: Long)

  @Query(
    "UPDATE episodes SET lastPositionMs = :positionMs, durationMs = :durationMs, completed = :completed, lastPlayedAtMs = :playedAtMs WHERE guid = :guid"
  )
  suspend fun updatePlayback(
    guid: String,
    positionMs: Long,
    durationMs: Long,
    completed: Int,
    playedAtMs: Long,
  )

  @Query("UPDATE episodes SET downloadId = :downloadId WHERE guid = :guid")
  suspend fun setDownloadId(guid: String, downloadId: Long)

  @Query("SELECT * FROM episodes WHERE downloadId = :downloadId LIMIT 1")
  suspend fun getByDownloadId(downloadId: Long): EpisodeEntity?

  @Query("UPDATE episodes SET localFileUri = :uri WHERE guid = :guid")
  suspend fun setLocalFileUri(guid: String, uri: String?)

  @Query("UPDATE episodes SET completed = :completed, lastPositionMs = :positionMs, lastPlayedAtMs = :playedAtMs WHERE guid = :guid")
  suspend fun setCompleted(
    guid: String,
    completed: Int,
    positionMs: Long,
    playedAtMs: Long,
  )

  @Query("UPDATE episodes SET completed = 1, lastPlayedAtMs = :playedAtMs WHERE podcastId = :podcastId")
  suspend fun markAllPlayed(podcastId: Long, playedAtMs: Long)

  @Query("UPDATE episodes SET completed = 0, lastPositionMs = 0, lastPlayedAtMs = 0 WHERE podcastId = :podcastId")
  suspend fun markAllUnplayed(podcastId: Long)
}
