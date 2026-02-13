package com.birch.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.birch.podcast.data.repo.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddFeedViewModel(
  private val repo: PodcastRepository,
) : ViewModel() {

  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error

  fun add(feedUrl: String, onSuccess: (Long) -> Unit) {
    _error.value = null
    _busy.value = true
    viewModelScope.launch {
      try {
        val id = repo.addPodcast(feedUrl.trim())
        onSuccess(id)
      } catch (t: Throwable) {
        _error.value = t.message ?: t.toString()
      } finally {
        _busy.value = false
      }
    }
  }
}
