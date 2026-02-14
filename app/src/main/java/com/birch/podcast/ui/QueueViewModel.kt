package com.birch.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.birch.podcast.data.db.QueueItemEntity
import com.birch.podcast.data.repo.PodcastRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(
  private val repo: PodcastRepository,
) : ViewModel() {

  val queue: StateFlow<List<QueueItemEntity>> = repo.observeQueue()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun remove(id: Long) {
    viewModelScope.launch { repo.removeQueueItem(id) }
  }

  fun clear() {
    viewModelScope.launch { repo.clearQueue() }
  }

  fun moveUp(id: Long) {
    viewModelScope.launch { repo.moveQueueItem(id, -1) }
  }

  fun moveDown(id: Long) {
    viewModelScope.launch { repo.moveQueueItem(id, +1) }
  }

  fun moveToTop(id: Long) {
    viewModelScope.launch { repo.moveQueueItemToTop(id) }
  }

  fun moveToBottom(id: Long) {
    viewModelScope.launch { repo.moveQueueItemToBottom(id) }
  }

  fun moveToIndex(fromIndex: Int, toIndex: Int) {
    viewModelScope.launch { repo.moveQueueItemToIndex(fromIndex, toIndex) }
  }
}
