package com.birch.podcast

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { BirchApp() }
  }
}

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

  // Playback controller
  var controller by remember { mutableStateOf<MediaController?>(null) }

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

  fun playEpisode(title: String, guid: String, audioUrl: String) {
    val c = controller ?: return

    val item = MediaItem.Builder()
      .setMediaId(guid)
      .setUri(audioUrl)
      .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
      .build()

    c.setMediaItem(item)
    c.prepare()
    c.play()
  }

  BirchTheme(darkTheme = dark) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "library") {
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
        // Title lookup (best-effort; if missing use id)
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
