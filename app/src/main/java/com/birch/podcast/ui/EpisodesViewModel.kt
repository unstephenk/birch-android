package com.birch.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.birch.podcast.data.db.EpisodeEntity
import com.birch.podcast.data.repo.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EpisodesViewModel(
  private val repo: PodcastRepository,
  private val podcastId: Long,
) : ViewModel() {
  val episodes: StateFlow<List<EpisodeEntity>> = repo.observeEpisodes(podcastId)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _refreshing = MutableStateFlow(false)
  val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  fun clearError() {
    _lastError.value = null
  }

  fun refresh(autoQueueNewestUnplayed: Boolean = false) {
    viewModelScope.launch {
      if (_refreshing.value) return@launch
      _refreshing.value = true
      _lastError.value = null
      try {
        repo.refreshPodcastById(podcastId, autoQueueNewestUnplayed)
      } catch (t: Throwable) {
        _lastError.value = t.message ?: "Refresh failed"
      } finally {
        _refreshing.value = false
      }
    }
  }

  fun clearPlayed() {
    viewModelScope.launch { repo.deletePlayedEpisodes(podcastId) }
  }

  fun markAllPlayed() {
    viewModelScope.launch { repo.markAllPlayed(podcastId) }
  }

  fun markAllUnplayed() {
    viewModelScope.launch { repo.markAllUnplayed(podcastId) }
  }
}
