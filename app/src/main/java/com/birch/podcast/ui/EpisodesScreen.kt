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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.birch.podcast.data.db.EpisodeEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesScreen(
  title: String,
  vm: EpisodesViewModel,
  onBack: () -> Unit,
  onPlay: (EpisodeEntity) -> Unit,
) {
  val episodes by vm.episodes.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        }
      )
    }
  ) { padding ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
      items(episodes, key = { it.id }) { ep ->
        EpisodeListRow(ep = ep, onPlay = { onPlay(ep) })
      }
    }
  }
}

@Composable
private fun EpisodeListRow(
  ep: EpisodeEntity,
  onPlay: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onPlay() }
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Text(ep.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (!ep.summary.isNullOrBlank()) {
      Spacer(Modifier.padding(2.dp))
      Text(ep.summary!!, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (ep.publishedAtMs != null) {
      Spacer(Modifier.padding(2.dp))
      Text("${ep.publishedAtMs}", style = MaterialTheme.typography.labelSmall)
    }
  }
}
