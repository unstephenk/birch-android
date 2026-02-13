package com.birch.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.birch.podcast.data.db.PodcastEntity
import com.birch.podcast.data.repo.PodcastRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
  private val repo: PodcastRepository,
) : ViewModel() {

  val podcasts: StateFlow<List<PodcastEntity>> = repo.observePodcasts()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun refresh(podcast: PodcastEntity) {
    viewModelScope.launch {
      repo.refreshPodcast(podcast)
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
