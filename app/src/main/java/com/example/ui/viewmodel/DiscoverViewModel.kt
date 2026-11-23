package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.HashtagItem
import com.example.data.model.SoundItem
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val query: String = "",
    val selectedCategory: String = "All",
    val activeHashtag: String? = null,
    val activeSoundId: String? = null,
    val videosState: UiState<List<VideoItem>> = UiState.Loading,
    val creators: List<UserProfile> = emptyList(),
    val hashtags: List<HashtagItem> = emptyList(),
    val sounds: List<SoundItem> = emptyList(),
    val categories: List<String> = listOf("All", "Comedy", "Music", "Dance", "Cricket", "Tech", "Food", "Travel", "Gaming"),
    val feedbackMessage: String? = null,
    val requireAuthPrompt: Boolean = false
)

class DiscoverViewModel(private val repository: BladeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun onQueryChanged(q: String) {
        _uiState.update { it.copy(query = q, activeHashtag = null, activeSoundId = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(280)
            refresh()
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category, activeHashtag = null, activeSoundId = null) }
        refresh()
    }

    fun filterByHashtag(tag: String) {
        val clean = tag.removePrefix("#")
        _uiState.update { it.copy(activeHashtag = clean, query = "#$clean", activeSoundId = null) }
        refresh()
    }

    fun filterBySound(soundId: String) {
        _uiState.update { it.copy(activeSoundId = soundId, activeHashtag = null) }
        refresh()
    }

    fun clearFilters() {
        _uiState.update { it.copy(query = "", selectedCategory = "All", activeHashtag = null, activeSoundId = null) }
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(videosState = UiState.Loading) }
            val result = repository.searchDiscover(
                query = state.query,
                category = state.selectedCategory,
                hashtag = state.activeHashtag,
                soundId = state.activeSoundId
            )
            val sounds = repository.getSounds()
            result.fold(
                onSuccess = { (videos, users, hashtags) ->
                    _uiState.update {
                        it.copy(
                            videosState = UiState.Success(videos),
                            creators = users,
                            hashtags = hashtags,
                            sounds = sounds
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(videosState = UiState.Error(err.message ?: "Failed to search"), sounds = sounds)
                    }
                }
            )
        }
    }

    fun onVideoViewed(video: VideoItem) {
        viewModelScope.launch { repository.recordVideoView(video) }
    }

    fun onWatchSignal(video: VideoItem, weight: Int) {
        viewModelScope.launch { repository.recordWatchSignal(video, weight = weight) }
    }

    fun toggleVideoLike(video: VideoItem, forceLike: Boolean = false) {
        if (forceLike && video.isLiked) return
        viewModelScope.launch {
            val optimisticLiked = !video.isLiked
            val delta = if (optimisticLiked) 1 else -1
            repository.recordWatchSignal(video, weight = if (optimisticLiked) 6 else -4)
            updateVideoInResults(video.id) {
                it.copy(isLiked = optimisticLiked, likesCount = (it.likesCount + delta).coerceAtLeast(0))
            }
            val res = repository.toggleLike(video.id)
            if (res.isFailure) {
                updateVideoInResults(video.id) {
                    it.copy(isLiked = video.isLiked, likesCount = video.likesCount)
                }
                val msg = res.exceptionOrNull()?.message ?: "Sign in required"
                _uiState.update {
                    it.copy(
                        feedbackMessage = msg,
                        requireAuthPrompt = msg.contains("Sign in", ignoreCase = true)
                    )
                }
            }
        }
    }

    fun toggleVideoSave(video: VideoItem) {
        viewModelScope.launch {
            val optimisticSaved = !video.isSaved
            val delta = if (optimisticSaved) 1 else -1
            repository.recordWatchSignal(video, weight = if (optimisticSaved) 6 else -4)
            updateVideoInResults(video.id) {
                it.copy(isSaved = optimisticSaved, savesCount = (it.savesCount + delta).coerceAtLeast(0))
            }
            val res = repository.toggleSave(video.id, video.isSaved)
            if (res.isFailure) {
                updateVideoInResults(video.id) {
                    it.copy(isSaved = video.isSaved, savesCount = video.savesCount)
                }
                val msg = res.exceptionOrNull()?.message ?: "Sign in required"
                _uiState.update {
                    it.copy(
                        feedbackMessage = msg,
                        requireAuthPrompt = msg.contains("Sign in", ignoreCase = true)
                    )
                }
            }
        }
    }

    fun toggleFollowCreator(creatorId: String) {
        viewModelScope.launch {
            val res = repository.toggleFollow(creatorId)
            res.fold(
                onSuccess = { nowFollowing ->
                    val current = (_uiState.value.videosState as? UiState.Success)?.data ?: return@fold
                    _uiState.update {
                        it.copy(
                            videosState = UiState.Success(
                                current.map { v ->
                                    if (v.userId == creatorId) v.copy(isFollowingCreator = nowFollowing) else v
                                }
                            )
                        )
                    }
                },
                onFailure = { err ->
                    val msg = err.message ?: "Sign in required"
                    _uiState.update {
                        it.copy(
                            feedbackMessage = msg,
                            requireAuthPrompt = msg.contains("Sign in", ignoreCase = true)
                        )
                    }
                }
            )
        }
    }

    fun shareVideo(video: VideoItem) {
        viewModelScope.launch {
            repository.recordWatchSignal(video, weight = 8)
            repository.recordVideoShare(video.id)
            updateVideoInResults(video.id) { it.copy(sharesCount = it.sharesCount + 1) }
        }
    }

    fun incrementVideoCommentCount(videoId: String) {
        updateVideoInResults(videoId) { it.copy(commentsCount = it.commentsCount + 1) }
    }

    fun removeVideo(videoId: String) {
        val current = (_uiState.value.videosState as? UiState.Success)?.data ?: return
        _uiState.update {
            it.copy(videosState = UiState.Success(current.filterNot { v -> v.id == videoId }))
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null, requireAuthPrompt = false) }
    }

    private fun updateVideoInResults(videoId: String, transform: (VideoItem) -> VideoItem) {
        val current = (_uiState.value.videosState as? UiState.Success)?.data ?: return
        _uiState.update {
            it.copy(videosState = UiState.Success(current.map { v -> if (v.id == videoId) transform(v) else v }))
        }
    }
}
