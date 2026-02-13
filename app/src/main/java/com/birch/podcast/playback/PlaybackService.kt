package com.birch.podcast.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {

  private var player: ExoPlayer? = null
  private var mediaSession: MediaSession? = null

  override fun onCreate() {
    super.onCreate()

    val p = ExoPlayer.Builder(this)
      // Enables standard Bluetooth "seek forward/back" actions.
      // Many headsets/cars send these instead of custom commands.
      .setSeekBackIncrementMs(15_000)
      .setSeekForwardIncrementMs(30_000)
      .build().apply {
      setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(C.USAGE_MEDIA)
          .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
          .build(),
        /* handleAudioFocus= */ true
      )
      setHandleAudioBecomingNoisy(true)
      repeatMode = Player.REPEAT_MODE_OFF
    }
    player = p

    mediaSession = MediaSession.Builder(this, p)
      .setId("birch_session")
      .setCallback(object : MediaSession.Callback {
        override fun onConnect(
          session: MediaSession,
          controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
          // Allow notification controller to use seek back/forward.
          val base = super.onConnect(session, controller)
          val sessionCommands = base.availableSessionCommands
          val playerCommands = base.availablePlayerCommands
            .buildUpon()
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .build()
          return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }
      })
      .build().also { session ->
        // Notification / lockscreen buttons.
        val rewind = CommandButton.Builder()
          .setDisplayName("Rewind")
          .setIconResId(android.R.drawable.ic_media_rew)
          .setPlayerCommand(Player.COMMAND_SEEK_BACK)
          .build()
        val fwd = CommandButton.Builder()
          .setDisplayName("Forward")
          .setIconResId(android.R.drawable.ic_media_ff)
          .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
          .build()
        session.setCustomLayout(listOf(rewind, fwd))
      }

    // Media3 default notification (transport controls + BT/lockscreen)
    setMediaNotificationProvider(
      DefaultMediaNotificationProvider(this).apply {
        setSmallIcon(android.R.drawable.ic_media_play)
      }
    )
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onDestroy() {
    mediaSession?.release()
    mediaSession = null

    player?.release()
    player = null

    super.onDestroy()
  }
}
