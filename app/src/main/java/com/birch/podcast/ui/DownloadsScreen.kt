package com.birch.podcast.ui

import android.app.DownloadManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.birch.podcast.data.db.EpisodeEntity
import com.birch.podcast.data.repo.PodcastRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
  repo: PodcastRepository,
  onBack: () -> Unit,
  onPlay: (EpisodeEntity) -> Unit,
  onRetry: (EpisodeEntity) -> Unit,
  onCancelAllActive: (List<EpisodeEntity>) -> Unit,
  onClearAllFailed: (List<EpisodeEntity>) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val downloading by repo.observeDownloadingEpisodes().collectAsState(initial = emptyList())
  val saved by repo.observeDownloadedEpisodes().collectAsState(initial = emptyList())
  val failed by repo.observeFailedDownloads().collectAsState(initial = emptyList())

  var menuOpen by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Downloads") },
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
              text = { Text("Cancel all active") },
              enabled = downloading.isNotEmpty(),
              onClick = {
                menuOpen = false
                onCancelAllActive(downloading)
                scope.launch { snackbarHostState.showSnackbar("Canceled ${downloading.size} download(s)") }
              },
            )
            DropdownMenuItem(
              text = { Text("Retry all failed") },
              enabled = failed.isNotEmpty(),
              onClick = {
                menuOpen = false
                failed.forEach { onRetry(it) }
                scope.launch { snackbarHostState.showSnackbar("Retrying ${failed.size} download(s)") }
              },
            )
            DropdownMenuItem(
              text = { Text("Clear all failed") },
              enabled = failed.isNotEmpty(),
              onClick = {
                menuOpen = false
                onClearAllFailed(failed)
                scope.launch { snackbarHostState.showSnackbar("Cleared ${failed.size} failed item(s)") }
              },
            )
          }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    val isEmptyAll = downloading.isEmpty() && saved.isEmpty() && failed.isEmpty()
    if (isEmptyAll) {
      EmptyState(
        title = "No downloads",
        subtitle = "Download an episode to listen offline.",
        icon = Icons.Filled.Download,
        modifier = Modifier.padding(padding),
      )
      return@Scaffold
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
      item {
        SectionHeader("Downloading", downloading.size)
      }
      if (downloading.isEmpty()) {
        item { EmptyRow("No active downloads") }
      } else {
        items(downloading, key = { it.id }) { ep ->
          val size = fmtMaybeSize(ep.enclosureLengthBytes)
          val rem = fmtRemainingMs(ep.durationMs, ep.lastPositionMs)
          val detail = listOfNotNull(
            size?.let { "Size: $it" },
            rem?.let { "Remaining: $it" },
          ).joinToString(" • ").ifBlank { null }

          DownloadRow(
            ep = ep,
            status = "Downloading",
            detail = detail,
            onClick = { onPlay(ep) },
            onRemove = {
              scope.launch {
                val dm = context.getSystemService(DownloadManager::class.java)
                if (ep.downloadId != 0L) dm?.remove(ep.downloadId)
                repo.clearEpisodeDownload(ep.guid)
                snackbarHostState.showSnackbar("Canceled")
              }
            },
          )
        }
      }

      item {
        SectionHeader("Saved", saved.size)
      }
      if (saved.isEmpty()) {
        item { EmptyRow("No saved episodes") }
      } else {
        items(saved, key = { it.id }) { ep ->
          val size = fmtMaybeSize(ep.enclosureLengthBytes)
          val rem = fmtRemainingMs(ep.durationMs, ep.lastPositionMs)
          val detail = listOfNotNull(
            size?.let { "Size: $it" },
            rem?.let { "Remaining: $it" },
          ).joinToString(" • ").ifBlank { null }

          var confirmRemove by remember(ep.id) { mutableStateOf(false) }
          if (confirmRemove) {
            AlertDialog(
              onDismissRequest = { confirmRemove = false },
              title = { Text("Remove download?") },
              text = { Text("This deletes the saved file for this episode.") },
              confirmButton = {
                TextButton(
                  onClick = {
                    confirmRemove = false
                    val local = ep.localFileUri
                    if (!local.isNullOrBlank()) {
                      runCatching {
                        val uri = Uri.parse(local)
                        context.contentResolver.delete(uri, null, null)
                      }
                    }
                    scope.launch {
                      repo.clearEpisodeDownload(ep.guid)
                      repo.setEpisodeDownloadStatus(ep.guid, null, null)
                      snackbarHostState.showSnackbar("Removed")
                    }
                  }
                ) { Text("Remove") }
              },
              dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
              },
            )
          }

          DownloadRow(
            ep = ep,
            status = "Saved",
            detail = detail,
            onClick = { onPlay(ep) },
            onRemove = {
              confirmRemove = true
            },
          )
        }
      }

      item {
        SectionHeader("Failed", failed.size)
      }
      if (failed.isEmpty()) {
        item { EmptyRow("No failed downloads") }
      } else {
        items(failed, key = { it.id }) { ep ->
          val size = fmtMaybeSize(ep.enclosureLengthBytes)
          val rem = fmtRemainingMs(ep.durationMs, ep.lastPositionMs)
          val d1 = listOfNotNull(
            size?.let { "Size: $it" },
            rem?.let { "Remaining: $it" },
          ).joinToString(" • ").ifBlank { null }
          val d2 = ep.downloadError?.let { "Reason: $it" }
          val detail = listOfNotNull(d1, d2).joinToString("\n").ifBlank { null }

          DownloadRow(
            ep = ep,
            status = "Failed",
            detail = detail,
            onClick = {
              onRetry(ep)
              scope.launch { snackbarHostState.showSnackbar("Retrying…") }
            },
            onRemove = {
              scope.launch {
                repo.setEpisodeDownloadStatus(ep.guid, null, null)
                repo.clearEpisodeDownload(ep.guid)
                snackbarHostState.showSnackbar("Cleared")
              }
            },
          )
        }
      }

      item { Spacer(Modifier.padding(12.dp)) }
    }
  }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
  val label = if (count > 0) "$title ($count)" else title
  Text(
    label,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
  )
}

@Composable
private fun EmptyRow(text: String) {
  Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DownloadRow(
  ep: EpisodeEntity,
  status: String,
  detail: String? = null,
  onClick: () -> Unit,
  onRemove: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.padding(4.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(ep.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!detail.isNullOrBlank()) {
          Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    IconButton(onClick = onRemove) {
      Icon(Icons.Filled.Delete, contentDescription = "Remove")
    }
  }
}
