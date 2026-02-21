package com.birch.podcast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.window.PopupProperties
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.birch.podcast.data.db.PodcastEntity
import com.birch.podcast.theme.Dimens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
  vm: LibraryViewModel,
  darkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onAdd: () -> Unit,
  onOpenDownloads: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenPodcast: (Long) -> Unit,
) {
  val podcasts by vm.podcasts.collectAsState()
  val refreshingPodcastId by vm.refreshingPodcastId.collectAsState()
  val lastError by vm.lastError.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(lastError) {
    if (!lastError.isNullOrBlank()) {
      snackbarHostState.showSnackbar(lastError!!)
      vm.clearError()
    }
  }

  var confirmUnsub by remember { mutableStateOf<PodcastEntity?>(null) }
  var query by remember { mutableStateOf("") }
  var sortMenuOpen by remember { mutableStateOf(false) }
  var sortMode by remember { mutableStateOf("Recent") }
  var gridMode by remember { mutableStateOf(false) }

  val filtered = remember(podcasts, query, sortMode) {
    val q = query.trim()
    val base = if (q.isBlank()) podcasts
    else podcasts.filter {
      it.title.contains(q, ignoreCase = true) || (it.description?.contains(q, ignoreCase = true) ?: false)
    }

    when (sortMode) {
      "A-Z" -> base.sortedBy { it.title.lowercase() }
      "Unplayed" -> base // we don’t have counts here without extra queries; keep stable for now
      else -> base.sortedWith(compareByDescending<PodcastEntity> { it.lastRefreshAtMs ?: 0L }.thenByDescending { it.id })
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
          IconButton(onClick = { sortMenuOpen = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
          }

          IconButton(onClick = { gridMode = !gridMode }) {
            Icon(
              if (gridMode) Icons.Filled.ViewList else Icons.Filled.GridView,
              contentDescription = if (gridMode) "List view" else "Grid view",
            )
          }
          DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Recent") },
              onClick = { sortMenuOpen = false; sortMode = "Recent" },
            )
            DropdownMenuItem(
              text = { Text("A-Z") },
              onClick = { sortMenuOpen = false; sortMode = "A-Z" },
            )
          }

          IconButton(onClick = onOpenDownloads) {
            Icon(Icons.Filled.Download, contentDescription = "Downloads")
          }
          IconButton(onClick = onOpenHistory) {
            Icon(Icons.Filled.History, contentDescription = "History")
          }
          IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
          }
          IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add feed")
          }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
      EmptyState(
        title = "No podcasts yet",
        subtitle = "Add an RSS feed to get started.",
        icon = Icons.Filled.Add,
        actionLabel = "Add a feed",
        onAction = onAdd,
        modifier = Modifier.padding(padding),
      )
    } else {
      Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenH, vertical = Dimens.s12),
          label = { Text("Search podcasts") },
          singleLine = true,
        )

        if (filtered.isEmpty()) {
          EmptyState(
            title = "No matches",
            subtitle = "Try a different search.",
          )
        } else {
          if (gridMode) {
            LazyVerticalGrid(
              columns = GridCells.Adaptive(minSize = 160.dp),
              modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.screenH),
              horizontalArrangement = Arrangement.spacedBy(Dimens.s12),
              verticalArrangement = Arrangement.spacedBy(Dimens.s12),
            ) {
              gridItems(filtered, key = { it.id }) { p ->
                PodcastGridCard(
                  podcast = p,
                  onOpen = { onOpenPodcast(p.id) },
                )
              }
            }
          } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(filtered, key = { it.id }) { p ->
                PodcastRow(
                  podcast = p,
                  vm = vm,
                  refreshing = refreshingPodcastId == p.id,
                  onOpen = { onOpenPodcast(p.id) },
                  onRefresh = {
                    vm.refresh(p)
                    scope.launch { snackbarHostState.showSnackbar("Refreshing…") }
                  },
                  onUnsubscribe = { confirmUnsub = p },
                )
              }
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
  refreshing: Boolean,
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
      .padding(horizontal = Dimens.screenH, vertical = Dimens.s8)
      .combinedClickable(
        onClick = onOpen,
        onLongClick = { menuOpen = true },
      )
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(Dimens.cardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Dimens.s12),
        ) {
          val ctx = LocalContext.current
          val url = podcast.imageUrl
          AsyncImage(
            model = ImageRequest.Builder(ctx).data(url).crossfade(true).build(),
            contentDescription = "Podcast artwork",
            modifier = Modifier.size(Dimens.artworkSm),
          )

          Column(modifier = Modifier.weight(1f)) {
            Text(
              normalizePodcastTitle(podcast.title),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )

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
              Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        IconButton(onClick = onRefresh, enabled = !refreshing) {
          if (refreshing) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
          } else {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
          }
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

@Composable
private fun PodcastGridCard(
  podcast: PodcastEntity,
  onOpen: () -> Unit,
) {
  val ctx = LocalContext.current
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onOpen() },
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(Dimens.cardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.s10),
    ) {
      AsyncImage(
        model = ImageRequest.Builder(ctx).data(podcast.imageUrl).crossfade(true).build(),
        contentDescription = "Podcast artwork",
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(Dimens.s16)),
      )

      Text(
        normalizePodcastTitle(podcast.title),
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
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
    val scroll = rememberScrollState()

    val suggestions = listOf(
      "Alex Jones (Infowars)" to "https://rss.infowars.com/Alex.xml",
      "BBC Global News Podcast" to "https://podcasts.files.bbci.co.uk/p02nq0lx.rss",
      "NPR: Up First" to "https://feeds.npr.org/510318/podcast.xml",
      "TED Talks Daily" to "https://feeds.feedburner.com/TEDTalks_audio",
      "The Economist: The Intelligence" to "https://rss.acast.com/theintelligence",
      "The Vergecast" to "https://feeds.megaphone.fm/vergecast",
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)
        .verticalScroll(scroll)
    ) {
      Text("Paste an RSS feed URL")
      Spacer(Modifier.padding(6.dp))
      OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("https://example.com/feed.xml") }
      )

      Spacer(Modifier.padding(12.dp))
      Text("Popular feeds (tap to fill)", style = MaterialTheme.typography.titleSmall)
      Spacer(Modifier.padding(6.dp))

      suggestions.forEach { (name, link) ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { url = link }
            .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(link, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Text("Use", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
      }

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

      Spacer(Modifier.padding(18.dp))
    }
  }
}
