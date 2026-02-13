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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
) {
  val episodes by vm.episodes.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
      items(episodes, key = { it.id }) { ep ->
        EpisodeListRow(
          ep = ep,
          onPlay = { onPlay(ep) },
          onAddToQueue = {
            onAddToQueue(ep)
            scope.launch {
              snackbarHostState.showSnackbar("Added to queue")
            }
          },
        )
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
    Text(ep.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (!ep.summary.isNullOrBlank()) {
      Spacer(Modifier.padding(2.dp))
      Text(ep.summary!!, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val date = formatEpochMsShort(ep.publishedAtMs)
    if (!date.isNullOrBlank()) {
      Spacer(Modifier.padding(2.dp))
      Text(date, style = MaterialTheme.typography.labelSmall)
    }
  }
}
