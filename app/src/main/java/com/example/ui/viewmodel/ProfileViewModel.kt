package com.example.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TransactionItem
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FollowListType { FOLLOWERS, FOLLOWING }

data class ProfileUiState(
    val profileState: UiState<UserProfile> = UiState.Loading,
    val uploadedVideos: List<VideoItem> = emptyList(),
    val likedVideos: List<VideoItem> = emptyList(),
    val savedVideos: List<VideoItem> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val isFollowing: Boolean = false,
    val followListType: FollowListType? = null,
    val followListState: UiState<List<UserProfile>> = UiState.Loading,
    val isSavingProfile: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val deletingVideoId: String? = null,
    val feedbackMessage: String? = null
)

class ProfileViewModel(private val repository: BladeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            val loadedId = (_uiState.value.profileState as? UiState.Success)?.data?.id
            if (loadedId != userId) {
                _uiState.update { it.copy(profileState = UiState.Loading) }
            }
            repository.getUserProfile(userId).fold(
                onSuccess = { profile ->
                    val uploaded = repository.getUserVideos(userId)
                    val liked = repository.getUserLikedVideos(userId)
                    val saved = repository.getUserSavedVideos(userId)
                    val tx = repository.getTransactions()
                    val following = repository.isFollowingUser(userId)
                    _uiState.update {
                        it.copy(
                            profileState = UiState.Success(profile),
                            uploadedVideos = uploaded,
                            likedVideos = liked,
                            savedVideos = saved,
                            transactions = tx,
                            isFollowing = following
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(profileState = UiState.Error(err.message ?: "Unable to load profile"))
                    }
                }
            )
        }
    }

    fun openFollowList(userId: String, type: FollowListType) {
        _uiState.update {
            it.copy(followListType = type, followListState = UiState.Loading)
        }
        viewModelScope.launch {
            val result = when (type) {
                FollowListType.FOLLOWERS -> repository.getFollowerProfiles(userId)
                FollowListType.FOLLOWING -> repository.getFollowingProfiles(userId)
            }
            result.fold(
                onSuccess = { users ->
                    _uiState.update { it.copy(followListState = UiState.Success(users)) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(followListState = UiState.Error(err.message ?: "Unable to load users"))
                    }
                }
            )
        }
    }

    fun closeFollowList() {
        _uiState.update { it.copy(followListType = null) }
    }

    fun toggleFollow(targetUserId: String) {
        viewModelScope.launch {
            repository.toggleFollow(targetUserId).fold(
                onSuccess = { nowFollowing ->
                    _uiState.update { state ->
                        val updatedProfile = (state.profileState as? UiState.Success)?.data?.let { p ->
                            val delta = if (nowFollowing) 1 else -1
                            p.copy(followersCount = (p.followersCount + delta).coerceAtLeast(0))
                        }
                        state.copy(
                            isFollowing = nowFollowing,
                            profileState = updatedProfile?.let { UiState.Success(it) } ?: state.profileState
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(feedbackMessage = err.message) }
                }
            )
        }
    }

    fun saveProfileEdits(
        displayName: String,
        username: String,
        bio: String,
        isPrivate: Boolean,
        newAvatarUri: Uri?,
        contentResolver: ContentResolver,
        onSaved: (UserProfile) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true) }
            repository.updateProfile(displayName, username, bio, isPrivate, newAvatarUri, contentResolver).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            profileState = UiState.Success(updated),
                            feedbackMessage = "Profile updated"
                        )
                    }
                    onSaved(updated)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isSavingProfile = false, feedbackMessage = err.message ?: "Update failed")
                    }
                }
            )
        }
    }

    fun uploadProfilePhoto(
        photoUri: Uri,
        contentResolver: ContentResolver,
        onUploaded: (UserProfile) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true) }
            repository.uploadProfilePhoto(photoUri, contentResolver).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            isUploadingPhoto = false,
                            profileState = UiState.Success(updated),
                            feedbackMessage = "Profile photo updated!"
                        )
                    }
                    onUploaded(updated)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isUploadingPhoto = false,
                            feedbackMessage = err.message ?: "Photo upload failed"
                        )
                    }
                }
            )
        }
    }

    fun claimRewardedAdCoins(onUserUpdated: (Int) -> Unit) {
        viewModelScope.launch {
            repository.claimRewardCoins(25, "Rewarded Video Ad").fold(
                onSuccess = { newBalance ->
                    onUserUpdated(newBalance)
                    val tx = repository.getTransactions()
                    _uiState.update { state ->
                        val p = (state.profileState as? UiState.Success)?.data?.copy(coinsBalance = newBalance)
                        state.copy(
                            profileState = p?.let { UiState.Success(it) } ?: state.profileState,
                            transactions = tx,
                            feedbackMessage = "+25 Blade Coins credited!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(feedbackMessage = err.message ?: "Unable to claim reward") }
                }
            )
        }
    }

    fun deleteUploadedVideo(videoId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletingVideoId = videoId) }
            repository.deleteVideo(videoId).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            uploadedVideos = state.uploadedVideos.filterNot { it.id == videoId },
                            likedVideos = state.likedVideos.filterNot { it.id == videoId },
                            savedVideos = state.savedVideos.filterNot { it.id == videoId },
                            deletingVideoId = null,
                            feedbackMessage = "Video deleted"
                        )
                    }
                    onDeleted()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            deletingVideoId = null,
                            feedbackMessage = err.message ?: "Unable to delete video"
                        )
                    }
                }
            )
        }
    }

    fun toggleVideoLike(video: VideoItem, forceLike: Boolean = false) {
        if (forceLike && video.isLiked) return
        viewModelScope.launch {
            val optimisticLiked = !video.isLiked
            val delta = if (optimisticLiked) 1 else -1
            updateVideoAcrossTabs(video.id) {
                it.copy(isLiked = optimisticLiked, likesCount = (it.likesCount + delta).coerceAtLeast(0))
            }
            val res = repository.toggleLike(video.id)
            if (res.isFailure) {
                updateVideoAcrossTabs(video.id) {
                    it.copy(isLiked = video.isLiked, likesCount = video.likesCount)
                }
                _uiState.update {
                    it.copy(feedbackMessage = res.exceptionOrNull()?.message ?: "Sign in required")
                }
            }
        }
    }

    fun toggleVideoSave(video: VideoItem) {
        viewModelScope.launch {
            val optimisticSaved = !video.isSaved
            val delta = if (optimisticSaved) 1 else -1
            updateVideoAcrossTabs(video.id) {
                it.copy(isSaved = optimisticSaved, savesCount = (it.savesCount + delta).coerceAtLeast(0))
            }
            val res = repository.toggleSave(video.id, video.isSaved)
            if (res.isFailure) {
                updateVideoAcrossTabs(video.id) {
                    it.copy(isSaved = video.isSaved, savesCount = video.savesCount)
                }
                _uiState.update {
                    it.copy(feedbackMessage = res.exceptionOrNull()?.message ?: "Sign in required")
                }
            }
        }
    }

    fun shareProfileVideo(video: VideoItem) {
        viewModelScope.launch {
            repository.recordVideoShare(video.id)
            updateVideoAcrossTabs(video.id) { it.copy(sharesCount = it.sharesCount + 1) }
        }
    }

    fun incrementVideoCommentCount(videoId: String) {
        updateVideoAcrossTabs(videoId) { it.copy(commentsCount = it.commentsCount + 1) }
    }

    private fun updateVideoAcrossTabs(videoId: String, transform: (VideoItem) -> VideoItem) {
        _uiState.update { state ->
            state.copy(
                uploadedVideos = state.uploadedVideos.map { if (it.id == videoId) transform(it) else it },
                likedVideos = state.likedVideos.map { if (it.id == videoId) transform(it) else it },
                savedVideos = state.savedVideos.map { if (it.id == videoId) transform(it) else it }
            )
        }
    }

    fun requestVerification(onUpdated: (UserProfile) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true) }
            repository.requestUserVerification().fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            profileState = UiState.Success(updated),
                            feedbackMessage = "Verification request submitted for admin review."
                        )
                    }
                    onUpdated(updated)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            feedbackMessage = err.message ?: "Unable to submit verification request"
                        )
                    }
                }
            )
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
