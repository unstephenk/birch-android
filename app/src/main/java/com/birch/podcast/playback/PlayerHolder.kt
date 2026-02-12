package com.birch.podcast.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.birch.podcast.model.Episode

class PlayerHolder(context: Context) {
  private val appContext = context.applicationContext
  private val store = PlaybackStore(appContext)

  val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
    repeatMode = Player.REPEAT_MODE_OFF
  }

  var nowPlaying: Episode? = null
    private set

  fun playEpisode(ep: Episode, localPath: String? = null) {
    nowPlaying = ep

    val uri = localPath ?: ep.audioUrl
    val item = MediaItem.Builder()
      .setUri(uri)
      .setMediaId(ep.id)
      .setTag(ep)
      .build()

    player.setMediaItem(item)
    player.prepare()

    // restore last position (if any)
    val resumePos = store.getPositionMs(ep.id)
    if (resumePos > 0) {
      player.seekTo(resumePos)
    }

    player.playWhenReady = true
  }

  fun saveProgress() {
    val ep = nowPlaying ?: return
    val pos = player.currentPosition
    if (pos > 0) store.setPositionMs(ep.id, pos)
  }

  fun markCompletedIfFinished() {
    val ep = nowPlaying ?: return
    // Mark complete if playback ended, or if within last 15 seconds.
    val dur = player.duration
    val pos = player.currentPosition
    val ended = player.playbackState == Player.STATE_ENDED
    val nearEnd = dur > 0 && pos >= (dur - 15_000)
    if (ended || nearEnd) {
      store.setCompleted(ep.id, true)
    }
  }

  fun isCompleted(episodeId: String): Boolean = store.isCompleted(episodeId)

  fun release() {
    player.release()
  }
}
