package com.birch.podcast

import android.content.ComponentName
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
import com.birch.podcast.playback.PlaybackService
import com.birch.podcast.theme.BirchTheme
import com.birch.podcast.theme.ThemePrefs
import com.birch.podcast.ui.AddFeedScreen
import com.birch.podcast.ui.AddFeedViewModel
import com.birch.podcast.ui.EpisodesScreen
import com.birch.podcast.ui.EpisodesViewModel
import com.birch.podcast.ui.LibraryScreen
import com.birch.podcast.ui.LibraryViewModel
import kotlinx.coroutines.delay

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

  // Playback controller + UI state
  var controller by remember { mutableStateOf<MediaController?>(null) }
  var nowTitle by remember { mutableStateOf<String?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var positionMs by remember { mutableStateOf(0L) }
  var durationMs by remember { mutableStateOf(0L) }

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
      }
    }
  }

  DisposableEffect(controller) {
    val c = controller
    c?.addListener(listener)
    onDispose { c?.removeListener(listener) }
  }

  // Position/duration ticker
  LaunchedEffect(controller) {
    while (true) {
      val c = controller
      positionMs = c?.currentPosition ?: 0L
      durationMs = c?.duration ?: 0L
      delay(500)
    }
  }

  fun playEpisode(title: String, guid: String, audioUrl: String) {
    val c = controller ?: return

    val item = MediaItem.Builder()
      .setMediaId(guid)
      .setUri(audioUrl)
      .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
      .build()

    nowTitle = title
    c.setMediaItem(item)
    c.prepare()
    c.play()
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
  onSeekTo: (Long) -> Unit,
  onPlayPause: () -> Unit,
  onRewind15: () -> Unit,
  onForward30: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
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
