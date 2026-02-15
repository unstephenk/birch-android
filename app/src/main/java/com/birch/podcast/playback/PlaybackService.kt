package com.birch.podcast.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
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
  private var loudnessEnhancer: LoudnessEnhancer? = null
  private var boostEnabled: Boolean = false

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

      addListener(object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
          if (audioSessionId == 0) return
          // Recreate when session id changes.
          loudnessEnhancer?.release()
          loudnessEnhancer = runCatching {
            LoudnessEnhancer(audioSessionId).apply {
              // 1200 mB ~= +12 dB. Keep it modest.
              setTargetGain(1200)
              enabled = boostEnabled
            }
          }.getOrNull()
        }
      })
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
            .buildUpon()
            .add(androidx.media3.session.SessionCommand(PlaybackCommands.ACTION_SET_BOOST, Bundle.EMPTY))
            .add(androidx.media3.session.SessionCommand(PlaybackCommands.ACTION_SET_SKIP_SILENCE, Bundle.EMPTY))
            .add(androidx.media3.session.SessionCommand(PlaybackCommands.ACTION_CYCLE_SPEED, Bundle.EMPTY))
            .add(androidx.media3.session.SessionCommand(PlaybackCommands.ACTION_TOGGLE_FAVORITE_EPISODE, Bundle.EMPTY))
            .build()
          val playerCommands = base.availablePlayerCommands
            .buildUpon()
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .build()
          return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onCustomCommand(
          session: MediaSession,
          controller: MediaSession.ControllerInfo,
          customCommand: androidx.media3.session.SessionCommand,
          args: Bundle,
        ): ListenableFuture<SessionResult> {
          when (customCommand.customAction) {
            PlaybackCommands.ACTION_SET_BOOST -> {
              boostEnabled = args.getBoolean(PlaybackCommands.EXTRA_ENABLED, false)
              loudnessEnhancer?.enabled = boostEnabled
              return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            PlaybackCommands.ACTION_SET_SKIP_SILENCE -> {
              val enabled = args.getBoolean(PlaybackCommands.EXTRA_ENABLED, false)
              player?.skipSilenceEnabled = enabled
              return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            PlaybackCommands.ACTION_CYCLE_SPEED -> {
              val p = player ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
              val cur = p.playbackParameters.speed
              val steps = listOf(1.0f, 1.2f, 1.5f, 2.0f)
              val idx = steps.indexOfFirst { kotlin.math.abs(it - cur) < 0.01f }
              val next = steps[(if (idx >= 0) idx + 1 else 0) % steps.size]
              p.setPlaybackSpeed(next)
              return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            PlaybackCommands.ACTION_TOGGLE_FAVORITE_EPISODE -> {
              // UI/layout first: wire this up to persistence next.
              return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
          }
          return super.onCustomCommand(session, controller, customCommand, args)
        }
      })
      .build().also { session ->
        // Notification / lockscreen buttons.
        val speed = CommandButton.Builder()
          .setDisplayName("1x")
          .setIconResId(android.R.drawable.ic_menu_manage) // placeholder icon; refine later
          .setSessionCommand(androidx.media3.session.SessionCommand(PlaybackCommands.ACTION_CYCLE_SPEED, Bundle.EMPTY))
          .build()
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
        val favorite = CommandButton.Builder()
          .setDisplayName("Favorite")
          .setIconResId(android.R.drawable.btn_star_big_off)
          .setSessionCommand(androidx.media3.session.SessionCommand(PlaybackCommands.ACTION_TOGGLE_FAVORITE_EPISODE, Bundle.EMPTY))
          .build()

        // Order: Speed • Rewind • Play/Pause (system) • Forward • Favorite
        session.setCustomLayout(listOf(speed, rewind, fwd, favorite))
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

    loudnessEnhancer?.release()
    loudnessEnhancer = null

    player?.release()
    player = null

    super.onDestroy()
  }
}
