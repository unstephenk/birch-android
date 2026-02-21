package com.birch.podcast.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.unit.dp
import com.birch.podcast.data.db.EpisodeEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EpisodesScreen(
  title: String,
  vm: EpisodesViewModel,
  podcastId: Long,
  onBack: () -> Unit,
  onPlay: (EpisodeEntity) -> Unit,
  onAddToQueue: (EpisodeEntity) -> Unit,
  onDownload: (EpisodeEntity) -> Unit,
  onRemoveDownload: (EpisodeEntity) -> Unit,
  onTogglePlayed: (EpisodeEntity) -> Unit,
  onPlayNext: (EpisodeEntity) -> Unit,
  onPlayLast: (EpisodeEntity) -> Unit,
  onDownloadAllUnplayed: (List<EpisodeEntity>) -> Unit,
  downloadProgress: (EpisodeEntity) -> DownloadUi?,
) {
  val episodes by vm.episodes.collectAsState()
  val context = LocalContext.current
  var query by remember { mutableStateOf("") }
  var hidePlayed by remember { mutableStateOf(EpisodesPrefs.hidePlayed(context, default = false)) }
  var hidePlayedThisPodcast by remember { mutableStateOf(EpisodesPrefs.hidePlayedForPodcast(context, podcastId, default = false)) }
  val effectiveHidePlayed = hidePlayed || hidePlayedThisPodcast
  var filter by remember { mutableStateOf(if (effectiveHidePlayed) "Unplayed" else "All") }

  var autoQueueNewest by remember { mutableStateOf(com.birch.podcast.queue.QueuePrefs.autoAddNewestUnplayed(context, podcastId, default = false)) }
  var menuOpen by remember { mutableStateOf(false) }
  var confirmClearPlayed by remember { mutableStateOf(false) }
  var confirmMarkAllPlayed by remember { mutableStateOf(false) }
  var confirmMarkAllUnplayed by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val refreshing by vm.refreshing.collectAsState()
  val lastError by vm.lastError.collectAsState()

  LaunchedEffect(lastError) {
    if (!lastError.isNullOrBlank()) {
      snackbarHostState.showSnackbar(lastError!!)
      vm.clearError()
    }
  }

  val filtered = remember(episodes, query, filter) {
    val q = query.trim()
    val searched = if (q.isBlank()) episodes
    else episodes.filter { ep ->
      ep.title.contains(q, ignoreCase = true) || (ep.summary?.contains(q, ignoreCase = true) ?: false)
    }

    val base0 = when (filter) {
      "Unplayed" -> searched.filter { it.completed == 0 }
      "Downloaded" -> searched.filter { !it.localFileUri.isNullOrBlank() }
      else -> searched
    }

    val base = if (effectiveHidePlayed) base0.filter { it.completed == 0 } else base0

    // Sort: saved first, then newest first.
    base.sortedWith(
      compareByDescending<EpisodeEntity> { !it.localFileUri.isNullOrBlank() }
        .thenByDescending { it.publishedAtMs }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(normalizePodcastTitle(title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
          IconButton(
            onClick = { vm.refresh(autoQueueNewest) },
            enabled = !refreshing,
          ) {
            if (refreshing) {
              CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
            } else {
              Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
          }

          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Mark all played") },
              onClick = {
                menuOpen = false
                confirmMarkAllPlayed = true
              }
            )

            DropdownMenuItem(
              text = { Text(if (autoQueueNewest) "Auto-queue newest: On" else "Auto-queue newest: Off") },
              onClick = {
                menuOpen = false
                autoQueueNewest = !autoQueueNewest
                com.birch.podcast.queue.QueuePrefs.setAutoAddNewestUnplayed(context, podcastId, autoQueueNewest)
              }
            )
            DropdownMenuItem(
              text = { Text("Mark all unplayed") },
              onClick = {
                menuOpen = false
                confirmMarkAllUnplayed = true
              }
            )
            DropdownMenuItem(
              text = { Text(if (hidePlayed) "Show played" else "Hide played") },
              onClick = {
                menuOpen = false
                hidePlayed = !hidePlayed
                EpisodesPrefs.setHidePlayed(context, hidePlayed)
                filter = if (hidePlayed || hidePlayedThisPodcast) "Unplayed" else filter
              }
            )

            DropdownMenuItem(
              text = { Text(if (hidePlayedThisPodcast) "Show played (this podcast)" else "Hide played (this podcast)") },
              onClick = {
                menuOpen = false
                hidePlayedThisPodcast = !hidePlayedThisPodcast
                EpisodesPrefs.setHidePlayedForPodcast(context, podcastId, hidePlayedThisPodcast)
                filter = if (hidePlayed || hidePlayedThisPodcast) "Unplayed" else filter
              }
            )

            DropdownMenuItem(
              text = { Text("Download all unplayed") },
              onClick = {
                menuOpen = false
                val targets = episodes.filter { it.completed == 0 && it.localFileUri.isNullOrBlank() && it.downloadId == 0L }
                if (targets.isNotEmpty()) onDownloadAllUnplayed(targets)
                scope.launch { snackbarHostState.showSnackbar("Started ${targets.size} downloads") }
              }
            )

            DropdownMenuItem(
              text = { Text("Clear played") },
              onClick = {
                menuOpen = false
                confirmClearPlayed = true
              }
            )
          }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    if (confirmMarkAllPlayed) {
      AlertDialog(
        onDismissRequest = { confirmMarkAllPlayed = false },
        title = { Text("Mark all played?") },
        text = { Text("This will mark every episode in this podcast as played.") },
        confirmButton = {
          TextButton(
            onClick = {
              confirmMarkAllPlayed = false
              vm.markAllPlayed()
              scope.launch { snackbarHostState.showSnackbar("Marked all played") }
            }
          ) { Text("Mark") }
        },
        dismissButton = { TextButton(onClick = { confirmMarkAllPlayed = false }) { Text("Cancel") } }
      )
    }

    if (confirmMarkAllUnplayed) {
      AlertDialog(
        onDismissRequest = { confirmMarkAllUnplayed = false },
        title = { Text("Mark all unplayed?") },
        text = { Text("This will mark every episode in this podcast as unplayed.") },
        confirmButton = {
          TextButton(
            onClick = {
              confirmMarkAllUnplayed = false
              vm.markAllUnplayed()
              scope.launch { snackbarHostState.showSnackbar("Marked all unplayed") }
            }
          ) { Text("Mark") }
        },
        dismissButton = { TextButton(onClick = { confirmMarkAllUnplayed = false }) { Text("Cancel") } }
      )
    }

    if (confirmClearPlayed) {
      AlertDialog(
        onDismissRequest = { confirmClearPlayed = false },
        title = { Text("Clear played episodes?") },
        text = { Text("This will delete all played episodes from this podcast.") },
        confirmButton = {
          TextButton(
            onClick = {
              confirmClearPlayed = false
              vm.clearPlayed()
              scope.launch { snackbarHostState.showSnackbar("Cleared played episodes") }
            }
          ) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = { confirmClearPlayed = false }) { Text("Cancel") } }
      )
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        label = { Text("Search episodes") },
        singleLine = true,
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf("All", "Unplayed", "Downloaded").forEach { label ->
          FilterChip(
            selected = filter == label,
            onClick = { filter = label },
            label = { Text(label) },
          )
        }
      }

      if (episodes.isEmpty()) {
        EmptyState(
          title = "No episodes yet",
          subtitle = "Pull to refresh (or tap refresh) to fetch the feed.",
          icon = Icons.Filled.Refresh,
          actionLabel = "Refresh",
          onAction = { vm.refresh(autoQueueNewest) },
        )
      } else if (filtered.isEmpty()) {
        EmptyState(
          title = "No matches",
          subtitle = "Try a different search or filter.",
        )
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(filtered, key = { it.id }) { ep ->
            Column {
              EpisodeListRow(
                ep = ep,
                onPlay = { onPlay(ep) },
                onAddToQueue = {
                  onAddToQueue(ep)
                  scope.launch {
                    snackbarHostState.showSnackbar("Added to queue")
                  }
                },
                onPlayNext = {
                  onPlayNext(ep)
                  scope.launch { snackbarHostState.showSnackbar("Added to play next") }
                },
                onPlayLast = {
                  onPlayLast(ep)
                  scope.launch { snackbarHostState.showSnackbar("Added to play last") }
                },
                onDownload = {
                  onDownload(ep)
                  scope.launch {
                    snackbarHostState.showSnackbar("Download started")
                  }
                },
                onRemoveDownload = {
                  onRemoveDownload(ep)
                  scope.launch {
                    snackbarHostState.showSnackbar("Download removed")
                  }
                },
                onTogglePlayed = {
                  onTogglePlayed(ep)
                  scope.launch {
                    snackbarHostState.showSnackbar(if (ep.completed == 1) "Marked unplayed" else "Marked played")
                  }
                },
                downloadProgress = { downloadProgress(ep) },
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            }
          }
        }
      }
    }
  }
}


@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun EpisodeListRow(
  ep: EpisodeEntity,
  onPlay: () -> Unit,
  onAddToQueue: () -> Unit,
  onPlayNext: () -> Unit,
  onPlayLast: () -> Unit,
  onDownload: () -> Unit,
  onRemoveDownload: () -> Unit,
  onTogglePlayed: () -> Unit,
  downloadProgress: () -> DownloadUi?,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = onPlay,
        onLongClick = onPlayNext,
      )
      .padding(horizontal = 12.dp, vertical = 12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      var rowMenuOpen by remember { mutableStateOf(false) }
      Row(modifier = Modifier.weight(1f).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ep.completed == 1) {
          Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Played",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
          )
        }
        Text(
          ep.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      val downloaded = !ep.localFileUri.isNullOrBlank()
      val downloading = !downloaded && ep.downloadId != 0L

      // Row menu (anchor to the 3-dots button)
      androidx.compose.foundation.layout.Box(
        modifier = Modifier.wrapContentSize(Alignment.TopEnd)
      ) {
        IconButton(onClick = { rowMenuOpen = true }) {
          Icon(Icons.Filled.MoreVert, contentDescription = "Episode menu")
        }
        DropdownMenu(
          expanded = rowMenuOpen,
          onDismissRequest = { rowMenuOpen = false },
        ) {
          DropdownMenuItem(
            text = { Text("Play next") },
            onClick = { rowMenuOpen = false; onPlayNext() },
          )
          DropdownMenuItem(
            text = { Text("Play last") },
            onClick = { rowMenuOpen = false; onPlayLast() },
          )
          DropdownMenuItem(
            text = { Text("Add to queue") },
            onClick = { rowMenuOpen = false; onAddToQueue() },
          )
          DropdownMenuItem(
            text = { Text(if (ep.completed == 1) "Mark unplayed" else "Mark played") },
            onClick = { rowMenuOpen = false; onTogglePlayed() },
          )
        }
      }

      // Download area (fixed width so it doesn't push other buttons around)
      androidx.compose.foundation.layout.Box(modifier = Modifier.width(72.dp)) {
        when {
          downloaded -> {
            var confirmRemove by remember { mutableStateOf(false) }
            if (confirmRemove) {
              AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("Remove download?") },
                text = { Text("This will delete the downloaded file for this episode.") },
                confirmButton = {
                  TextButton(
                    onClick = {
                      confirmRemove = false
                      onRemoveDownload()
                    }
                  ) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
              )
            }

            IconButton(onClick = { confirmRemove = true }) {
              Icon(
                Icons.Filled.Download,
                contentDescription = "Downloaded (tap to remove)",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          downloading -> {
            val ui = downloadProgress()
            val p = ui?.progress
            IconButton(onClick = onRemoveDownload) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (p != null) {
                  CircularProgressIndicator(progress = { p }, strokeWidth = 2.dp)
                  Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                } else {
                  CircularProgressIndicator(strokeWidth = 2.dp)
                  Text("…", style = MaterialTheme.typography.labelSmall)
                }

                val soFar = ui?.soFarBytes
                val total = ui?.totalBytes
                val eta = ui?.etaSec
                if (soFar != null && total != null && total > 0) {
                  Text("${fmtBytes(soFar)}/${fmtBytes(total)}", style = MaterialTheme.typography.labelSmall)
                } else if (soFar != null) {
                  Text(fmtBytes(soFar), style = MaterialTheme.typography.labelSmall)
                }
                if (eta != null && eta > 0) {
                  Text("ETA ${fmtEta(eta)}", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }

          else -> {
            IconButton(onClick = onDownload) {
              Icon(Icons.Filled.Download, contentDescription = "Download")
            }
          }
        }
      }
    }

    // Progress
    val showProgress = ep.durationMs > 0 && ep.lastPositionMs > 0 && ep.completed == 0
    if (showProgress) {
      Spacer(Modifier.padding(2.dp))
      val v = (ep.lastPositionMs.toFloat() / ep.durationMs.toFloat()).coerceIn(0f, 1f)
      LinearProgressIndicator(progress = { v }, modifier = Modifier.fillMaxWidth())
    }

    ep.summary?.takeIf { it.isNotBlank() }?.let { summary ->
      Spacer(Modifier.padding(2.dp))
      Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val date = formatEpochMsShort(ep.publishedAtMs)
    val size = fmtMaybeSize(ep.enclosureLengthBytes)
    val dur = ep.durationMs.takeIf { it > 0 }?.let { fmtDurationMs(it) }
    val remaining = if (ep.durationMs > 0 && ep.lastPositionMs > 0 && ep.completed == 0) {
      fmtDurationMs((ep.durationMs - ep.lastPositionMs).coerceAtLeast(0L))
    } else null

    if (!date.isNullOrBlank() || !dur.isNullOrBlank() || !remaining.isNullOrBlank()) {
      Spacer(Modifier.padding(2.dp))
      val bits = buildList {
        if (!date.isNullOrBlank()) add(date)
        if (!size.isNullOrBlank()) add(size)
        if (!dur.isNullOrBlank()) add(dur)
        if (!remaining.isNullOrBlank()) add("-$remaining")
      }
      Text(bits.joinToString(" • "), style = MaterialTheme.typography.labelSmall)
    }

    if (ep.completed == 1) {
      Spacer(Modifier.padding(2.dp))
      Text("Played", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

