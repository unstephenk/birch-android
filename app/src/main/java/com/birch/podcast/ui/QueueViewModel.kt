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
}
