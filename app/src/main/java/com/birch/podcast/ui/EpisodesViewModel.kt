package com.birch.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.birch.podcast.data.db.EpisodeEntity
import com.birch.podcast.data.repo.PodcastRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EpisodesViewModel(
  private val repo: PodcastRepository,
  private val podcastId: Long,
) : ViewModel() {
  val episodes: StateFlow<List<EpisodeEntity>> = repo.observeEpisodes(podcastId)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun refresh() {
    viewModelScope.launch { repo.refreshPodcastById(podcastId) }
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
