package com.birch.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.birch.podcast.data.db.PodcastEntity
import com.birch.podcast.data.repo.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
  private val repo: PodcastRepository,
) : ViewModel() {

  val podcasts: StateFlow<List<PodcastEntity>> = repo.observePodcasts()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _refreshingPodcastId = MutableStateFlow<Long?>(null)
  val refreshingPodcastId: StateFlow<Long?> = _refreshingPodcastId.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  fun clearError() {
    _lastError.value = null
  }

  fun refresh(podcast: PodcastEntity) {
    viewModelScope.launch {
      if (_refreshingPodcastId.value != null) return@launch
      _refreshingPodcastId.value = podcast.id
      _lastError.value = null
      try {
        repo.refreshPodcast(podcast)
      } catch (t: Throwable) {
        _lastError.value = t.message ?: "Refresh failed"
      } finally {
        _refreshingPodcastId.value = null
      }
    }
  }

  fun unsubscribe(podcast: PodcastEntity) {
    viewModelScope.launch {
      repo.unsubscribe(podcast)
    }
  }

  suspend fun countEpisodes(podcastId: Long): Int = repo.countEpisodes(podcastId)

  suspend fun countUnplayedEpisodes(podcastId: Long): Int = repo.countUnplayedEpisodes(podcastId)
}
