package com.birch.podcast.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.birch.podcast.data.db.EpisodeEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EpisodesScreen(
  title: String,
  vm: EpisodesViewModel,
  onBack: () -> Unit,
  onPlay: (EpisodeEntity) -> Unit,
  onAddToQueue: (EpisodeEntity) -> Unit,
  onDownload: (EpisodeEntity) -> Unit,
  onRemoveDownload: (EpisodeEntity) -> Unit,
  onTogglePlayed: (EpisodeEntity) -> Unit,
) {
  val episodes by vm.episodes.collectAsState()
  var query by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf("All") }
  var menuOpen by remember { mutableStateOf(false) }
  var confirmClearPlayed by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val filtered = remember(episodes, query, filter) {
    val q = query.trim()
    val searched = if (q.isBlank()) episodes
    else episodes.filter { ep ->
      ep.title.contains(q, ignoreCase = true) || (ep.summary?.contains(q, ignoreCase = true) ?: false)
    }

    val base = when (filter) {
      "Unplayed" -> searched.filter { it.completed == 0 }
      "Downloaded" -> searched.filter { !it.localFileUri.isNullOrBlank() }
      else -> searched
    }

    // Sort: saved first, then newest first.
    base.sortedWith(
      compareByDescending<EpisodeEntity> { !it.localFileUri.isNullOrBlank() }
        .thenByDescending { it.publishedAtMs }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
          IconButton(onClick = { vm.refresh() }) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
          }

          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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

      LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filtered, key = { it.id }) { ep ->
          EpisodeListRow(
            ep = ep,
            onPlay = { onPlay(ep) },
            onAddToQueue = {
              onAddToQueue(ep)
              scope.launch {
                snackbarHostState.showSnackbar("Added to queue")
              }
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
          )
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
  onDownload: () -> Unit,
  onRemoveDownload: () -> Unit,
  onTogglePlayed: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = onPlay,
        onLongClick = onAddToQueue,
      )
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        ep.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(end = 8.dp)
      )

      val downloaded = !ep.localFileUri.isNullOrBlank()
      val downloading = !downloaded && ep.downloadId != 0L

      // Played toggle
      IconButton(onClick = onTogglePlayed) {
        Icon(Icons.Filled.Check, contentDescription = "Toggle played")
      }

      if (downloaded) {
        Text("Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = onRemoveDownload) {
          Text("Remove", style = MaterialTheme.typography.labelSmall)
        }
      } else if (downloading) {
        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 12.dp), strokeWidth = 2.dp)
        IconButton(onClick = onRemoveDownload) {
          Text("Cancel", style = MaterialTheme.typography.labelSmall)
        }
      } else {
        IconButton(onClick = onDownload) {
          Icon(Icons.Filled.Download, contentDescription = "Download")
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
    if (!date.isNullOrBlank()) {
      Spacer(Modifier.padding(2.dp))
      Text(date, style = MaterialTheme.typography.labelSmall)
    }

    if (ep.completed == 1) {
      Spacer(Modifier.padding(2.dp))
      Text("Played", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

