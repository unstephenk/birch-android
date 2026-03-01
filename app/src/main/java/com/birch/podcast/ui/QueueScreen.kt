package com.birch.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
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
import com.birch.podcast.data.db.QueueItemEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
  vm: QueueViewModel,
  nowPlayingGuid: String?,
  onBack: () -> Unit,
  onPlayNow: (QueueItemEntity) -> Unit,
) {
  val queue by vm.queue.collectAsState()
  var confirmClear by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Queue") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Clear upcoming (keep playing)") },
              enabled = queue.isNotEmpty(),
              onClick = {
                menuOpen = false
                confirmClear = true
              }
            )
          }
        }
      )
    }
  ) { padding ->
    if (confirmClear) {
      AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear upcoming?") },
        text = { Text("This clears upcoming items in the queue. Current playback will continue.") },
        confirmButton = {
          TextButton(
            onClick = {
              confirmClear = false
              vm.clear()
            }
          ) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
      )
    }

    if (queue.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("Queue is empty")
      }
    } else {
      val reorderState = rememberReorderableLazyListState(
        onMove = { from, to -> vm.moveToIndex(from.index, to.index) }
      )

      LazyColumn(
        state = reorderState.listState,
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .reorderable(reorderState)
          .detectReorderAfterLongPress(reorderState)
      ) {
        itemsIndexed(queue, key = { _, it -> it.id }) { idx, item ->
          ReorderableItem(reorderState, key = item.id) {
            val dismissState = rememberSwipeToDismissBoxState(
              confirmValueChange = { v ->
                if (v != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                  vm.remove(item.id)
                  true
                } else {
                  false
                }
              }
            )

            SwipeToDismissBox(
              state = dismissState,
              enableDismissFromStartToEnd = false,
              enableDismissFromEndToStart = true,
              backgroundContent = {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
                  contentAlignment = Alignment.CenterEnd,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                  )
                }
              },
            ) {
              Column {
                QueueRow(
                  item = item,
                  isNowPlaying = nowPlayingGuid == item.episodeGuid,
                  canMoveUp = idx > 0,
                  canMoveDown = idx != -1 && idx < queue.lastIndex,
                  dragHandleModifier = Modifier.detectReorderAfterLongPress(reorderState),
                  onMoveUp = { vm.moveUp(item.id) },
                  onMoveDown = { vm.moveDown(item.id) },
                  onMoveTop = { vm.moveToTop(item.id) },
                  onMoveBottom = { vm.moveToBottom(item.id) },
                  onPlayNow = { onPlayNow(item) },
                  onRemove = { vm.remove(item.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QueueRow(
  item: QueueItemEntity,
  isNowPlaying: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  dragHandleModifier: Modifier,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onMoveTop: () -> Unit,
  onMoveBottom: () -> Unit,
  onPlayNow: () -> Unit,
  onRemove: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onPlayNow)
      .padding(horizontal = 12.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
      Text(
        item.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        if (isNowPlaying) "Now playing" else "Up next",
        style = MaterialTheme.typography.labelSmall,
        color = if (isNowPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Filled.DragHandle,
        contentDescription = "Reorder",
        modifier = dragHandleModifier,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
      }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
          text = { Text("Move to top") },
          onClick = { menuOpen = false; onMoveTop() },
          enabled = canMoveUp,
        )
        DropdownMenuItem(
          text = { Text("Move to bottom") },
          onClick = { menuOpen = false; onMoveBottom() },
          enabled = canMoveDown,
        )
        DropdownMenuItem(
          text = { Text("Move up") },
          onClick = { menuOpen = false; onMoveUp() },
          enabled = canMoveUp,
        )
        DropdownMenuItem(
          text = { Text("Move down") },
          onClick = { menuOpen = false; onMoveDown() },
          enabled = canMoveDown,
        )
        DropdownMenuItem(
          text = { Text("Remove") },
          onClick = { menuOpen = false; onRemove() },
        )
      }

      FilledTonalIconButton(onClick = onPlayNow) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
      }
    }
  }
}
