package com.birch.podcast

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.birch.podcast.data.db.AppDatabase
import com.birch.podcast.data.repo.PodcastRepository
import com.birch.podcast.playback.PlaybackPrefs
import com.birch.podcast.playback.PlaybackService
import com.birch.podcast.theme.BirchTheme
import com.birch.podcast.theme.ThemePrefs
import com.birch.podcast.ui.AddFeedScreen
import com.birch.podcast.ui.AddFeedViewModel
import com.birch.podcast.ui.EpisodesScreen
import com.birch.podcast.ui.EpisodesViewModel
import com.birch.podcast.ui.LibraryScreen
import com.birch.podcast.ui.LibraryViewModel
import com.birch.podcast.ui.NowPlayingScreen
import com.birch.podcast.ui.QueueScreen
import com.birch.podcast.ui.QueueViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { BirchApp() }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirchApp() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // Theme
  val themePrefs = remember { ThemePrefs(context) }
  val storedDark = themePrefs.darkTheme.collectAsState(initial = true).value
  val dark = storedDark ?: true

  // DB + repo
  val db = remember { AppDatabase.get(context) }
  val repo = remember { PodcastRepository(db) }

  fun downloadEpisode(title: String, guid: String, audioUrl: String) {
    val dm = context.getSystemService(DownloadManager::class.java) ?: return
    val req = DownloadManager.Request(Uri.parse(audioUrl))
      .setTitle(title)
      .setAllowedOverRoaming(true)
      .setAllowedOverMetered(true)
      .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
      .setDestinationInExternalFilesDir(context, "downloads", "$guid.mp3")

    val id = dm.enqueue(req)
    scope.launch { repo.setEpisodeDownloadId(guid, id) }
  }

  // Listen for DownloadManager completion and attach local file uri to the episode.
  DisposableEffect(Unit) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0) return

        val dm = context.getSystemService(DownloadManager::class.java) ?: return
        val q = DownloadManager.Query().setFilterById(id)
        val cur: Cursor = dm.query(q) ?: return
        cur.use {
          if (!it.moveToFirst()) return
          val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
          if (status != DownloadManager.STATUS_SUCCESSFUL) return
          val local = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: return

          // Find matching episode by downloadId.
          scope.launch {
            val ep = repo.getEpisodeByDownloadId(id) ?: return@launch
            repo.setEpisodeLocalFileUri(ep.guid, local)
          }
        }
      }
    }

    ContextCompat.registerReceiver(
      context,
      receiver,
      IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
      RECEIVER_NOT_EXPORTED,
    )
    onDispose {
      try {
        context.unregisterReceiver(receiver)
      } catch (_: IllegalArgumentException) {
        // If registration failed or was already unregistered, ignore.
      }
    }
  }

  // Playback controller + UI state
  var controller by remember { mutableStateOf<MediaController?>(null) }
  var nowTitle by remember { mutableStateOf<String?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var positionMs by remember { mutableStateOf(0L) }
  var durationMs by remember { mutableStateOf(0L) }
  var playbackSpeed by remember { mutableStateOf(PlaybackPrefs.getSpeed(context, 1.0f)) }

  LaunchedEffect(Unit) {
    // Ensure the service is started; required on some devices for stable playback.
    val intent = android.content.Intent(context, PlaybackService::class.java)
    try {
      ContextCompat.startForegroundService(context, intent)
    } catch (_: Throwable) {
      // best-effort
    }

    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    future.addListener(
      { controller = future.get() },
      ContextCompat.getMainExecutor(context)
    )
  }

  // Keep UI in sync with controller.
  val listener = remember {
    object : Player.Listener {
      override fun onIsPlayingChanged(isPlayingNow: Boolean) {
        isPlaying = isPlayingNow
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        nowTitle = mediaItem?.mediaMetadata?.title?.toString()
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        val c = controller
        isPlaying = c?.isPlaying ?: false
        playbackSpeed = c?.playbackParameters?.speed ?: playbackSpeed
      }
    }
  }

  DisposableEffect(controller) {
    val c = controller
    c?.addListener(listener)
    onDispose { c?.removeListener(listener) }
  }

  // Position/duration ticker + persist playback
  LaunchedEffect(controller) {
    // Apply saved playback speed whenever we get a controller.
    controller?.setPlaybackSpeed(playbackSpeed)

    var lastPersistAt = 0L
    while (true) {
      val c = controller
      positionMs = c?.currentPosition ?: 0L
      durationMs = c?.duration ?: 0L

      val guid = c?.currentMediaItem?.mediaId
      val now = System.currentTimeMillis()
      if (guid != null && durationMs > 0 && now - lastPersistAt > 3_000) {
        lastPersistAt = now
        // best-effort persistence
        scope.launch {
          repo.updateEpisodePlayback(
            guid = guid,
            positionMs = positionMs,
            durationMs = durationMs,
            completed = false,
          )
        }
      }

      delay(500)
    }
  }

  fun playEpisode(title: String, guid: String, audioUrl: String) {
    val c = controller ?: return

    nowTitle = title

    scope.launch {
      val saved = repo.getEpisodeByGuid(guid)
      val uri = saved?.localFileUri ?: audioUrl

      val item = MediaItem.Builder()
        .setMediaId(guid)
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()

      c.setMediaItem(item)

      // Resume (best-effort)
      val resumeMs = (saved?.lastPositionMs ?: 0L)
      val wasCompleted = (saved?.completed ?: 0) == 1
      if (!wasCompleted && resumeMs > 5_000) {
        c.seekTo(resumeMs)
      }
      c.prepare()
      c.play()
    }
  }

  // Auto-play next in queue when the current item ends.
  LaunchedEffect(controller) {
    val c = controller ?: return@LaunchedEffect

    val l = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          // Mark completed
          val guid = c.currentMediaItem?.mediaId
          val dur = c.duration
          if (guid != null && dur > 0) {
            scope.launch {
              repo.updateEpisodePlayback(
                guid = guid,
                positionMs = dur,
                durationMs = dur,
                completed = true,
              )
            }
          }

          // Pick the next queued item.
          scope.launch {
            val next = repo.dequeueNext() ?: return@launch
            playEpisode(next.title, next.episodeGuid, next.audioUrl)
          }
        }
      }
    }

    c.addListener(l)
    try {
      // Keep installed until controller changes
      while (true) delay(60_000)
    } finally {
      c.removeListener(l)
    }
  }

  BirchTheme(darkTheme = dark) {
    val nav = rememberNavController()

    Scaffold(
      bottomBar = {
        if (!nowTitle.isNullOrBlank()) {
          MiniPlayerBar(
            title = nowTitle,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            onOpen = { nav.navigate("nowplaying") },
            onSeekTo = { ms -> controller?.seekTo(ms) },
            onPlayPause = {
              val c = controller ?: return@MiniPlayerBar
              if (c.isPlaying) c.pause() else c.play()
            },
            onRewind15 = {
              val c = controller ?: return@MiniPlayerBar
              c.seekTo((c.currentPosition - 15_000).coerceAtLeast(0))
            },
            onForward30 = {
              val c = controller ?: return@MiniPlayerBar
              c.seekTo(c.currentPosition + 30_000)
            }
          )
        }
      }
    ) { padding ->
      NavHost(
        navController = nav,
        startDestination = "library",
        modifier = Modifier.padding(padding)
      ) {
        composable("library") {
          val vm = remember { LibraryViewModel(repo) }
          LibraryScreen(
            vm = vm,
            onAdd = { nav.navigate("add") },
            onOpenPodcast = { id -> nav.navigate("podcast/$id") },
          )
        }

        composable("add") {
          val vm = remember { AddFeedViewModel(repo) }
          AddFeedScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onAdded = { id ->
              nav.popBackStack() // back to library
              nav.navigate("podcast/$id")
            }
          )
        }

        composable(
          route = "podcast/{podcastId}",
          arguments = listOf(navArgument("podcastId") { type = NavType.LongType })
        ) { entry ->
          val podcastId = entry.arguments?.getLong("podcastId") ?: return@composable

          // Title lookup (best-effort)
          var title by remember { mutableStateOf("Podcast") }
          LaunchedEffect(podcastId) {
            title = db.podcasts().getById(podcastId)?.title ?: "Podcast"
          }

          val vm = remember { EpisodesViewModel(repo, podcastId) }
          EpisodesScreen(
            title = title,
            vm = vm,
            onBack = { nav.popBackStack() },
            onPlay = { ep ->
              playEpisode(ep.title, ep.guid, ep.audioUrl)
            },
            onAddToQueue = { ep ->
              scope.launch { repo.enqueue(ep.title, ep.guid, ep.audioUrl) }
            },
            onDownload = { ep ->
              downloadEpisode(ep.title, ep.guid, ep.audioUrl)
            },
            onRemoveDownload = { ep ->
              scope.launch {
                // Best-effort: we keep the file for now; just detach it from the episode.
                repo.clearEpisodeDownload(ep.guid)
              }
            }
          )
        }

        composable("nowplaying") {
          NowPlayingScreen(
            title = nowTitle,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            onBack = { nav.popBackStack() },
            onOpenQueue = { nav.navigate("queue") },
            onSeekTo = { ms -> controller?.seekTo(ms) },
            onPlayPause = {
              val c = controller ?: return@NowPlayingScreen
              if (c.isPlaying) c.pause() else c.play()
            },
            onRewind15 = {
              val c = controller ?: return@NowPlayingScreen
              c.seekTo((c.currentPosition - 15_000).coerceAtLeast(0))
            },
            onForward30 = {
              val c = controller ?: return@NowPlayingScreen
              c.seekTo(c.currentPosition + 30_000)
            },
            onSetSpeed = { speed ->
              val c = controller ?: return@NowPlayingScreen
              c.setPlaybackSpeed(speed)
              playbackSpeed = speed
              PlaybackPrefs.setSpeed(context, speed)
            }
          )
        }

        composable("queue") {
          val vm = remember { QueueViewModel(repo) }
          QueueScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onPlayNow = { item ->
              playEpisode(item.title, item.episodeGuid, item.audioUrl)
            }
          )
        }
      }
    }
  }
}

@Composable
private fun MiniPlayerBar(
  title: String?,
  isPlaying: Boolean,
  positionMs: Long,
  durationMs: Long,
  onOpen: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onPlayPause: () -> Unit,
  onRewind15: () -> Unit,
  onForward30: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text(
        text = title ?: "Now playing",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium
      )

      if (durationMs > 0) {
        val value = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        Slider(
          value = value,
          onValueChange = { v -> onSeekTo((v * durationMs.toFloat()).toLong()) },
          modifier = Modifier.fillMaxWidth()
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onRewind15) {
          Icon(Icons.Filled.FastRewind, contentDescription = "Rewind 15")
        }
        IconButton(onClick = onPlayPause) {
          Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
        }
        IconButton(onClick = onForward30) {
          Icon(Icons.Filled.FastForward, contentDescription = "Forward 30")
        }
        Spacer(Modifier.weight(1f))
      }
    }
  }
}
