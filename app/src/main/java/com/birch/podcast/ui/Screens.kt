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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.birch.podcast.data.db.PodcastEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  vm: LibraryViewModel,
  onAdd: () -> Unit,
  onOpenPodcast: (Long) -> Unit,
) {
  val podcasts by vm.podcasts.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Library") },
        actions = {
          IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add feed")
          }
        }
      )
    }
  ) { padding ->
    if (podcasts.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("No podcasts yet")
        Spacer(Modifier.padding(6.dp))
        Button(onClick = onAdd) { Text("Add a feed") }
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        items(podcasts, key = { it.id }) { p ->
          PodcastRow(podcast = p, onOpen = { onOpenPodcast(p.id) }, onRefresh = { vm.refresh(p) })
        }
      }
    }
  }
}

@Composable
private fun PodcastRow(
  podcast: PodcastEntity,
  onOpen: () -> Unit,
  onRefresh: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .clickable { onOpen() }
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(podcast.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!podcast.description.isNullOrBlank()) {
          Text(podcast.description!!, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }

      IconButton(onClick = onRefresh) {
        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
  vm: AddFeedViewModel,
  onBack: () -> Unit,
  onAdded: (Long) -> Unit,
) {
  var url by remember { mutableStateOf("") }
  val busy by vm.busy.collectAsState()
  val err by vm.error.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Add feed") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        }
      )
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      Text("Paste an RSS feed URL")
      Spacer(Modifier.padding(6.dp))
      OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("https://example.com/feed.xml") }
      )
      if (err != null) {
        Spacer(Modifier.padding(6.dp))
        Text("Error: $err", color = MaterialTheme.colorScheme.error)
      }
      Spacer(Modifier.padding(12.dp))
      Button(
        onClick = { vm.add(url) { onAdded(it) } },
        enabled = !busy && url.isNotBlank()
      ) {
        if (busy) {
          CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
        }
        Text("Add")
      }

      Spacer(Modifier.padding(12.dp))
      Text("Try: https://rss.infowars.com/Alex.xml", style = MaterialTheme.typography.bodySmall)
    }
  }
}
