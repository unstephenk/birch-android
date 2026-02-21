package com.birch.podcast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.birch.podcast.data.db.PlaybackHistoryEntity
import com.birch.podcast.data.repo.PodcastRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  repo: PodcastRepository,
  onBack: () -> Unit,
  onPlay: (episodeGuid: String) -> Unit,
) {
  val items by repo.observePlaybackHistory().collectAsState(initial = emptyList())

  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  var menuOpen by remember { mutableStateOf(false) }
  var confirmClear by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (items.isNotEmpty()) "History (${items.size})" else "History") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Clear history") },
              enabled = items.isNotEmpty(),
              leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
              onClick = {
                menuOpen = false
                confirmClear = true
              },
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    if (confirmClear) {
      AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear history?") },
        text = { Text("This removes all playback history.") },
        confirmButton = {
          TextButton(
            onClick = {
              confirmClear = false
              scope.launch {
                repo.clearPlaybackHistory()
                snackbarHostState.showSnackbar("History cleared")
              }
            }
          ) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
      )
    }
    if (items.isEmpty()) {
      EmptyState(
        title = "Nothing played yet",
        subtitle = "Start an episode and it’ll show up here.",
        icon = Icons.Filled.History,
        modifier = Modifier.padding(padding),
      )
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        items(items, key = { it.id }) { h ->
          HistoryRow(h = h, onClick = { onPlay(h.episodeGuid) })
        }
        item { Spacer(Modifier.padding(12.dp)) }
      }
    }
  }
}

@Composable
private fun HistoryRow(h: PlaybackHistoryEntity, onClick: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
    Text(
      "Played ${fmtRelative(h.playedAtMs)}",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun fmtRelative(tsMs: Long): String {
  val now = System.currentTimeMillis()
  val delta = (now - tsMs).coerceAtLeast(0)
  val min = delta / 60_000
  return when {
    min < 1 -> "just now"
    min < 60 -> "$min min ago"
    else -> {
      val h = min / 60
      if (h < 24) "$h h ago" else "${h / 24} d ago"
    }
  }
}
