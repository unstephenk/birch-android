package com.birch.podcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.birch.podcast.download.DownloadStore
import com.birch.podcast.download.Downloader
import com.birch.podcast.model.Episode
import com.birch.podcast.playback.PlaybackService
import com.birch.podcast.playback.PlaybackStore
import com.birch.podcast.rss.RssClient
import com.birch.podcast.theme.BirchTheme
import com.birch.podcast.theme.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FEED_URL = "https://www.infowars.com/rss.xml"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { BirchApp() }
  }
}

@Composable
private fun BirchApp() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs = remember { ThemePrefs(context) }

  // Default to dark if not set yet.
  val storedDark = prefs.darkTheme.collectAsState(initial = true).value
  val dark = storedDark ?: true

  BirchTheme(darkTheme = dark) {
    Surface(modifier = Modifier.fillMaxSize()) {
      PodcastScreen(
        darkTheme = dark,
        onToggleTheme = { next ->
          scope.launch { prefs.setDarkTheme(next) }
        }
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastScreen(
  darkTheme: Boolean,
  onToggleTheme: (Boolean) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val rss = remember { RssClient() }

  var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  val context = androidx.compose.ui.platform.LocalContext.current
  val downloadStore = remember { DownloadStore(context) }
  val downloader = remember { Downloader(context) }
  val playbackStore = remember { PlaybackStore(context) }

  var controller by remember { mutableStateOf<MediaController?>(null) }

  var nowPlaying by remember { mutableStateOf<Episode?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var positionMs by remember { mutableStateOf(0L) }
  var durationMs by remember { mutableStateOf(0L) }

  var downloadingId by remember { mutableStateOf<String?>(null) }
  var downloadError by remember { mutableStateOf<String?>(null) }
  val downloadProgress = remember { mutableStateMapOf<String, Float>() } // 0..1
  val downloadJobs = remember { mutableStateMapOf<String, kotlinx.coroutines.Job>() }

  // Persist playback position (throttled)
  LaunchedEffect(nowPlaying?.id, controller) {
    while (true) {
      kotlinx.coroutines.delay(5_000)
      val c = controller ?: continue
      val ep = nowPlaying ?: continue
      val pos = c.currentPosition
      if (pos > 0) playbackStore.setPositionMs(ep.id, pos)

      val dur = c.duration
      if (dur > 0 && pos >= dur - 15_000) {
        playbackStore.setCompleted(ep.id, true)
      }
    }
  }

  // UI ticker for playback position/duration
  LaunchedEffect(controller) {
    while (true) {
      val c = controller
      positionMs = c?.currentPosition ?: 0L
      durationMs = c?.duration ?: 0L
      kotlinx.coroutines.delay(500)
    }
  }

  // Notification permission (Android 13+; enforced on newer devices)
  val notifPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { /* no-op */ }
  )

  LaunchedEffect(Unit) {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  // Connect to the MediaSession-backed player
  LaunchedEffect(Unit) {
    // Ensure the service is started; required on some devices for stable playback.
    val intent = android.content.Intent(context, PlaybackService::class.java)
    try {
      ContextCompat.startForegroundService(context, intent)
    } catch (_: Throwable) {
      // If FGS start is blocked, we can still connect via session and let playback be best-effort.
    }

    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    future.addListener(
      {
        controller = future.get()
      },
      ContextCompat.getMainExecutor(context)
    )
  }

  // Listen to controller updates; release only when the screen is disposed.
  val playerListener = remember {
    object : Player.Listener {
      override fun onIsPlayingChanged(isPlayingNow: Boolean) {
        isPlaying = isPlayingNow
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        val c = controller
        isPlaying = c?.isPlaying ?: false
        if (playbackState == Player.STATE_ENDED) {
          nowPlaying?.let { playbackStore.setCompleted(it.id, true) }
        }
      }
    }
  }

  DisposableEffect(controller) {
    val c = controller
    c?.addListener(playerListener)
    onDispose {
      c?.removeListener(playerListener)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      controller?.release()
      controller = null
    }
  }

  fun refresh() {
    loading = true
    error = null
    scope.launch {
      try {
        val eps = withContext(Dispatchers.IO) { rss.fetchEpisodes(FEED_URL) }
        // newest-first: RSS is usually newest-first already; keep as-is
        episodes = eps
      } catch (t: Throwable) {
        error = t.message ?: t.toString()
      } finally {
        loading = false
      }
    }
  }

  LaunchedEffect(Unit) { refresh() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Birch") },
        actions = {
          IconButton(onClick = { onToggleTheme(!darkTheme) }) {
            Icon(
              if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
              contentDescription = "Toggle theme"
            )
          }
          IconButton(onClick = { refresh() }, enabled = !loading) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
          }
        }
      )
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
      NowPlayingBar(
        title = nowPlaying?.title,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        onSeekTo = { controller?.seekTo(it) },
        onPlayPause = {
          val c = controller ?: return@NowPlayingBar
          if (c.isPlaying) c.pause() else c.play()
        },
        onRewind15 = {
          val c = controller ?: return@NowPlayingBar
          val pos = (c.currentPosition - 15_000).coerceAtLeast(0)
          c.seekTo(pos)
        },
        onForward30 = {
          val c = controller ?: return@NowPlayingBar
          val pos = c.currentPosition + 30_000
          c.seekTo(pos)
        }
      )

      Spacer(Modifier.padding(6.dp))

      if (downloadError != null) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(downloadError!!, modifier = Modifier.weight(1f))
            IconButton(onClick = { downloadError = null }) {
              Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
          }
        }
        Spacer(Modifier.padding(6.dp))
      }

      when {
        loading -> {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
          }
        }
        error != null -> {
          Text("Error: $error", color = MaterialTheme.colorScheme.error)
        }
        else -> {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(episodes, key = { it.id }) { ep ->
              val localPath = downloadStore.getLocalPath(ep.id)
              val downloaded = localPath != null
              val downloading = downloadingId == ep.id
              val progress = downloadProgress[ep.id]

              EpisodeRow(
                ep = ep,
                completed = playbackStore.isCompleted(ep.id),
                downloaded = downloaded,
                downloading = downloading,
                progress = progress,
                onDownload = {
                  downloadError = null
                  downloadingId = ep.id
                  downloadProgress[ep.id] = 0f

                  val job = scope.launch {
                    try {
                      val file = withContext(Dispatchers.IO) {
                        downloader.download(ep) { done, total ->
                          if (total != null && total > 0) {
                            val p = (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
                            downloadProgress[ep.id] = p.toFloat()
                          }
                        }
                      }
                      downloadStore.setLocalPath(ep.id, file.absolutePath)
                      downloadProgress.remove(ep.id)
                    } catch (t: Throwable) {
                      downloadError = t.message ?: t.toString()
                    } finally {
                      downloadJobs.remove(ep.id)
                      downloadingId = null
                    }
                  }

                  downloadJobs[ep.id] = job
                },
                onCancelDownload = {
                  downloadJobs[ep.id]?.cancel()
                },
                onDelete = {
                  val ok = downloader.delete(ep.id)
                  downloadStore.setLocalPath(ep.id, null)
                  downloadProgress.remove(ep.id)
                  downloadError = if (ok) "Deleted: ${ep.title}" else "Delete failed: ${ep.title}"
                },
                onPlay = {
                  val c = controller ?: return@EpisodeRow
                  val lp = downloadStore.getLocalPath(ep.id)

                  val uri = if (lp != null) {
                    Uri.fromFile(java.io.File(lp))
                  } else {
                    Uri.parse(ep.audioUrl)
                  }

                  val item = MediaItem.Builder()
                    .setMediaId(ep.id)
                    .setUri(uri)
                    .setMediaMetadata(
                      MediaMetadata.Builder()
                        .setTitle(ep.title)
                        .build()
                    )
                    .build()

                  nowPlaying = ep
                  c.setMediaItem(item)
                  c.prepare()

                  val resume = playbackStore.getPositionMs(ep.id)
                  if (resume > 0) c.seekTo(resume)

                  c.play()
                }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NowPlayingBar(
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
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
    Text(
      text = if (title != null) "Now playing: $title" else "Now playing: —",
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.bodyMedium
    )

    // Seek bar
    if (title != null && durationMs > 0) {
      var sliderValue by remember(title, durationMs) { mutableStateOf(0f) }
      // Keep slider in sync with playback
      LaunchedEffect(title, positionMs, durationMs) {
        sliderValue = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
      }

      fun fmt(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
      }

      val elapsed = positionMs.coerceAtLeast(0L)
      val remaining = (durationMs - positionMs).coerceAtLeast(0L)

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmt(elapsed), style = MaterialTheme.typography.bodySmall)
        Text("-${fmt(remaining)}", style = MaterialTheme.typography.bodySmall)
        Text(fmt(durationMs), style = MaterialTheme.typography.bodySmall)
      }

      Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = {
          val seek = (sliderValue * durationMs.toFloat()).toLong()
          onSeekTo(seek)
        },
        modifier = Modifier.fillMaxWidth()
      )
    }

    Spacer(Modifier.padding(2.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      IconButton(onClick = onRewind15, enabled = title != null) {
        Icon(Icons.Filled.FastRewind, contentDescription = "Rewind 15 seconds")
      }
      IconButton(onClick = onPlayPause, enabled = title != null) {
        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
      }
      IconButton(onClick = onForward30, enabled = title != null) {
        Icon(Icons.Filled.FastForward, contentDescription = "Forward 30 seconds")
      }
    }
    }
  }
}

@Composable
private fun EpisodeRow(
  ep: Episode,
  completed: Boolean,
  downloaded: Boolean,
  downloading: Boolean,
  progress: Float?,
  onDownload: () -> Unit,
  onCancelDownload: () -> Unit,
  onDelete: () -> Unit,
  onPlay: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onPlay() }
      .padding(vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(ep.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)

        if (!ep.pubDateRaw.isNullOrBlank()) {
          Text(ep.pubDateRaw!!, style = MaterialTheme.typography.bodySmall)
        }

        if (!ep.summary.isNullOrBlank()) {
          Text(
            ep.summary!!,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
          )
        }

        if (downloading && progress != null) {
          Text("Downloading: ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          if (completed) Text("Done", style = MaterialTheme.typography.labelMedium)
          if (downloaded) Text("Offline", style = MaterialTheme.typography.labelMedium)
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(onClick = onDownload, enabled = !downloaded && !downloading) {
            Icon(Icons.Filled.Download, contentDescription = "Download")
          }
          IconButton(onClick = onCancelDownload, enabled = downloading) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel")
          }
          IconButton(onClick = onDelete, enabled = downloaded && !downloading) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
          }
        }
      }
    }
  }
}
