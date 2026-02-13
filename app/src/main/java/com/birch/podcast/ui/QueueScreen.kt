package com.birch.podcast.ui

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
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
import com.birch.podcast.data.db.QueueItemEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
  vm: QueueViewModel,
  onBack: () -> Unit,
  onPlayNow: (QueueItemEntity) -> Unit,
) {
  val queue by vm.queue.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Queue") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
          IconButton(onClick = { vm.clear() }, enabled = queue.isNotEmpty()) {
            Icon(Icons.Filled.ClearAll, contentDescription = "Clear queue")
          }
        }
      )
    }
  ) { padding ->
    if (queue.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("Queue is empty")
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        items(queue, key = { it.id }) { item ->
          QueueRow(item = item, onPlayNow = { onPlayNow(item) }, onRemove = { vm.remove(item.id) })
        }
      }
    }
  }
}

@Composable
private fun QueueRow(
  item: QueueItemEntity,
  onPlayNow: () -> Unit,
  onRemove: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
      Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("Up next", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      IconButton(onClick = onPlayNow) {
        Text("Play", style = MaterialTheme.typography.labelLarge)
      }
      IconButton(onClick = onRemove) {
        Icon(Icons.Filled.Delete, contentDescription = "Remove")
      }
      Spacer(Modifier.padding(0.dp))
    }
  }
}
