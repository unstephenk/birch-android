package com.birch.podcast.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
  vm: LibraryViewModel,
  darkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onAdd: () -> Unit,
  onOpenDownloads: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenPodcast: (Long) -> Unit,
) {
  val podcasts by vm.podcasts.collectAsState()
  var confirmUnsub by remember { mutableStateOf<PodcastEntity?>(null) }
  var query by remember { mutableStateOf("") }

  val filtered = remember(podcasts, query) {
    val q = query.trim()
    if (q.isBlank()) podcasts
    else podcasts.filter {
      it.title.contains(q, ignoreCase = true) || (it.description?.contains(q, ignoreCase = true) ?: false)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Library") },
        actions = {
          IconButton(onClick = onToggleTheme) {
            Icon(
              if (darkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
              contentDescription = "Toggle theme",
            )
          }
          IconButton(onClick = onOpenDownloads) {
            Icon(Icons.Filled.Download, contentDescription = "Downloads")
          }
          IconButton(onClick = onOpenHistory) {
            Icon(Icons.Filled.History, contentDescription = "History")
          }
          IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add feed")
          }
        }
      )
    }
  ) { padding ->
    confirmUnsub?.let { p ->
      AlertDialog(
        onDismissRequest = { confirmUnsub = null },
        title = { Text("Unsubscribe?") },
        text = { Text("Remove ‘${p.title}’ and delete its episodes?") },
        confirmButton = {
          TextButton(
            onClick = {
              confirmUnsub = null
              vm.unsubscribe(p)
            }
          ) { Text("Unsubscribe") }
        },
        dismissButton = { TextButton(onClick = { confirmUnsub = null }) { Text("Cancel") } }
      )
    }
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
      Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          label = { Text("Search podcasts") },
          singleLine = true,
        )

        if (filtered.isEmpty()) {
          Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text("No matches")
          }
        } else {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { p ->
              PodcastRow(
                podcast = p,
                vm = vm,
                onOpen = { onOpenPodcast(p.id) },
                onRefresh = { vm.refresh(p) },
                onUnsubscribe = { confirmUnsub = p },
              )
            }
          }
        }
      }
    }
  }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun PodcastRow(
  podcast: PodcastEntity,
  vm: LibraryViewModel,
  onOpen: () -> Unit,
  onRefresh: () -> Unit,
  onUnsubscribe: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  var totalCount by remember { mutableStateOf<Int?>(null) }
  var unplayedCount by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(podcast.id) {
    // best-effort counts
    totalCount = runCatching { vm.countEpisodes(podcast.id) }.getOrNull()
    unplayedCount = runCatching { vm.countUnplayedEpisodes(podcast.id) }.getOrNull()
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .combinedClickable(
        onClick = onOpen,
        onLongClick = { menuOpen = true },
      )
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(normalizePodcastTitle(podcast.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

          val refreshed = formatEpochMsWithTime(podcast.lastRefreshAtMs)
          if (!refreshed.isNullOrBlank()) {
            Text("Updated: $refreshed", style = MaterialTheme.typography.labelSmall)
          }

          if (totalCount != null && unplayedCount != null) {
            Text(
              "Episodes: ${totalCount} • Unplayed: ${unplayedCount}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          podcast.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
        }

        IconButton(onClick = onRefresh) {
          Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
        }
      }

      // Ensure menu is properly anchored/positioned.
      DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
        properties = PopupProperties(focusable = true)
      ) {
        DropdownMenuItem(
          text = { Text("Refresh") },
          onClick = {
            menuOpen = false
            onRefresh()
          }
        )
        DropdownMenuItem(
          text = { Text("Unsubscribe") },
          onClick = {
            menuOpen = false
            onUnsubscribe()
          }
        )
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
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
