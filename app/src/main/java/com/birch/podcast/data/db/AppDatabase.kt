package com.birch.podcast.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [PodcastEntity::class, EpisodeEntity::class, QueueItemEntity::class],
  version = 3,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun podcasts(): PodcastDao
  abstract fun episodes(): EpisodeDao
  abstract fun queue(): QueueDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase {
      val existing = INSTANCE
      if (existing != null) return existing

      return synchronized(this) {
        val again = INSTANCE
        if (again != null) return again

        val db = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "birch.db"
        )
          .fallbackToDestructiveMigration()
          .build()

        INSTANCE = db
        db
      }
    }
  }
}
