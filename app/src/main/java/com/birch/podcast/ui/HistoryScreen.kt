package com.birch.podcast.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.birch.podcast.data.db.PlaybackHistoryEntity
import com.birch.podcast.data.repo.PodcastRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  repo: PodcastRepository,
  onBack: () -> Unit,
  onPlay: (episodeGuid: String) -> Unit,
) {
  val items by repo.observePlaybackHistory().collectAsState(initial = emptyList())

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("History") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (items.isEmpty()) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Nothing played yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        items(items, key = { it.id }) { h ->
          HistoryRow(h = h, onClick = { onPlay(h.episodeGuid) })
        }
      }
      item { Spacer(Modifier.padding(12.dp)) }
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
