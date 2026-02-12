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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.birch.podcast.download.DownloadStore
import com.birch.podcast.download.Downloader
import com.birch.podcast.model.Episode
import com.birch.podcast.playback.PlayerHolder
import com.birch.podcast.rss.RssClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FEED_URL = "https://rss.infowars.com/Alex-mobile.html"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { BirchApp() }
  }
}

@Composable
private fun BirchApp() {
  MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      PodcastScreen()
    }
  }
}

@Composable
private fun PodcastScreen() {
  val scope = rememberCoroutineScope()
  val rss = remember { RssClient() }

  var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  val context = androidx.compose.ui.platform.LocalContext.current
  val holder = remember { PlayerHolder(context) }
  val downloadStore = remember { DownloadStore(context) }
  val downloader = remember { Downloader(context) }

  var nowPlaying by remember { mutableStateOf<Episode?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var positionMs by remember { mutableStateOf(0L) }
  var durationMs by remember { mutableStateOf(0L) }

  var downloadingId by remember { mutableStateOf<String?>(null) }
  var downloadError by remember { mutableStateOf<String?>(null) }
  val downloadProgress = remember { mutableStateMapOf<String, Float>() } // 0..1
  val downloadJobs = remember { mutableStateMapOf<String, kotlinx.coroutines.Job>() }

  // Persist playback position (throttled)
  LaunchedEffect(nowPlaying?.id) {
    while (true) {
      kotlinx.coroutines.delay(5_000)
      holder.saveProgress()
    }
  }

  // UI ticker for playback position/duration
  LaunchedEffect(nowPlaying?.id) {
    while (true) {
      positionMs = holder.player.currentPosition
      durationMs = holder.player.duration
      kotlinx.coroutines.delay(500)
    }
  }

  DisposableEffect(holder) {
    val listener = object : androidx.media3.common.Player.Listener {
      override fun onIsPlayingChanged(isPlayingNow: Boolean) {
        isPlaying = isPlayingNow
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        isPlaying = holder.player.isPlaying
        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
          holder.markCompletedIfFinished()
        }
      }
    }
    holder.player.addListener(listener)
    onDispose {
      holder.player.removeListener(listener)
      holder.saveProgress()
      holder.release()
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

  Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Birch", style = MaterialTheme.typography.headlineSmall)
      Button(onClick = { refresh() }, enabled = !loading) { Text("Refresh") }
    }

    Spacer(Modifier.padding(4.dp))

    NowPlayingBar(
      title = nowPlaying?.title,
      isPlaying = isPlaying,
      positionMs = positionMs,
      durationMs = durationMs,
      onSeekTo = { holder.player.seekTo(it) },
      onPlayPause = {
        if (holder.player.isPlaying) holder.player.pause() else holder.player.play()
      },
      onRewind15 = {
        val pos = (holder.player.currentPosition - 15_000).coerceAtLeast(0)
        holder.player.seekTo(pos)
      },
      onForward30 = {
        val pos = holder.player.currentPosition + 30_000
        holder.player.seekTo(pos)
      }
    )

    Spacer(Modifier.padding(6.dp))

    if (downloadError != null) {
      Text(downloadError!!)
      Spacer(Modifier.padding(4.dp))
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
              completed = holder.isCompleted(ep.id),
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
                // recompute localPath right before play
                val lp = downloadStore.getLocalPath(ep.id)
                nowPlaying = ep
                holder.playEpisode(ep, localPath = lp) // tap = play immediately
              }
            )
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
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp)
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
      Button(onClick = onRewind15, enabled = title != null) { Text("-15s") }
      Button(onClick = onPlayPause, enabled = title != null) { Text(if (isPlaying) "Pause" else "Play") }
      Button(onClick = onForward30, enabled = title != null) { Text("+30s") }
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
        Text(ep.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!ep.pubDateRaw.isNullOrBlank()) {
          Text(ep.pubDateRaw!!, style = MaterialTheme.typography.bodySmall)
        }
        if (downloading && progress != null) {
          Text("Downloading: ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        if (completed) Text("Done", style = MaterialTheme.typography.bodySmall)
        if (downloaded) Text("Offline", style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onDownload, enabled = !downloaded && !downloading) {
            Text("Download")
          }
          Button(onClick = onCancelDownload, enabled = downloading) {
            Text("Cancel")
          }
          Button(onClick = onDelete, enabled = downloaded && !downloading) {
            Text("Delete")
          }
        }
      }
    }
  }
}
