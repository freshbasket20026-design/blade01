package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdItem
import com.example.data.model.CommentItem
import com.example.data.model.GiftItem
import com.example.data.model.UiState
import com.example.data.model.VideoItem
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedTab { FOR_YOU, FOLLOWING }

data class FeedUiState(
    val selectedTab: FeedTab = FeedTab.FOR_YOU,
    val videosState: UiState<List<VideoItem>> = UiState.Loading,
    val isLoadingMore: Boolean = false,
    val feedVersion: Int = 0,
    val activeAds: List<AdItem> = emptyList(),
    val commentsVideoId: String? = null,
    val commentsState: UiState<List<CommentItem>> = UiState.Loading,
    val giftTargetVideo: VideoItem? = null,
    val gifts: List<GiftItem> = emptyList(),
    val feedbackMessage: String? = null,
    val requireAuthPrompt: Boolean = false
)

class FeedViewModel(private val repository: BladeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var activePageIndex: Int = 0
    private val recordedMilestones = mutableSetOf<String>()
    private var activeReloadCycle: Int = 0
    private var replayBatchCycle: Int = 0
    private var paginationOffset: Int = 0
    private var hasMoreServerVideos: Boolean = true
    private var hasMoreVideos: Boolean = true
    private var suppressedLeaveFeedVersion: Int = -1
    private var loadFeedJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadFeed(FeedTab.FOR_YOU, isFreshReload = false)
    }

    fun selectTab(tab: FeedTab) {
        loadFeed(tab = tab, isFreshReload = true)
    }

    fun onNewVideoPublished(video: VideoItem) {
        if (video.privacy.lowercase() != "public") {
            loadFeed(tab = FeedTab.FOR_YOU, isFreshReload = false)
            return
        }
        val existing = (_uiState.value.videosState as? UiState.Success)?.data.orEmpty()
        val updated = prependPublishedVideo(existing, video)
        activePageIndex = 0
        _uiState.update {
            it.copy(
                selectedTab = FeedTab.FOR_YOU,
                videosState = UiState.Success(updated),
                isLoadingMore = false,
                feedVersion = it.feedVersion + 1
            )
        }
        loadFeedJob?.cancel()
        loadFeedJob = viewModelScope.launch {
            val res = repository.fetchFeed(forYou = true, offset = 0)
            res.onSuccess { remoteList ->
                val synced = prependPublishedVideo(remoteList, video)
                _uiState.update { latest ->
                    latest.copy(videosState = UiState.Success(synced))
                }
            }
        }
    }

    companion object {
        fun prependPublishedVideo(current: List<VideoItem>, video: VideoItem): List<VideoItem> =
            (listOf(video) + current).distinctBy { it.id }
    }

    fun loadFeed(
        tab: FeedTab = _uiState.value.selectedTab,
        isFreshReload: Boolean = false
    ) {
        loadFeedJob?.cancel()
        loadMoreJob?.cancel()
        loadFeedJob = viewModelScope.launch {
            val previousTab = _uiState.value.selectedTab
            val previousVideos = (_uiState.value.videosState as? UiState.Success)?.data.orEmpty()
            val previousPageIndex = activePageIndex
            val isForYou = tab == FeedTab.FOR_YOU

            // Guard against DisposableEffect.onDispose false skip penalty when VerticalPager unmounts during reload
            suppressedLeaveFeedVersion = _uiState.value.feedVersion
            activePageIndex = 0
            recordedMilestones.clear()
            replayBatchCycle = 0
            hasMoreServerVideos = true
            hasMoreVideos = true
            _uiState.update {
                it.copy(
                    selectedTab = tab,
                    videosState = UiState.Loading,
                    isLoadingMore = false
                )
            }

            val currentPersisted = repository.getFeedReloadState()
            val reloadState = if (isFreshReload) {
                val surfacedIds = if (previousVideos.isNotEmpty()) {
                    val count = (previousPageIndex + 1).coerceIn(1, previousVideos.size)
                    previousVideos.take(count).map { it.id }
                } else {
                    emptyList()
                }
                val currentTabOffset = if (isForYou) currentPersisted.forYouOffset else currentPersisted.followingOffset
                val nextTabOffset = when {
                    previousTab != tab -> currentTabOffset
                    previousVideos.size >= 50 -> currentTabOffset + 45
                    else -> 0
                }
                repository.updateFeedReloadState(
                    forYou = isForYou,
                    newlySurfacedIds = surfacedIds,
                    incrementCycle = true,
                    nextOffset = nextTabOffset
                )
            } else {
                currentPersisted
            }

            activeReloadCycle = reloadState.reloadCycle
            val fetchOffset = if (isForYou) reloadState.forYouOffset else reloadState.followingOffset

            val result = repository.fetchFeed(
                forYou = isForYou,
                offset = fetchOffset,
                demoteIds = reloadState.lastBatchIds.toSet(),
                reloadCycle = activeReloadCycle,
                currentSeq = reloadState.surfacedSeq
            )
            val ads = repository.fetchFeedAds()
            result.fold(
                onSuccess = { list ->
                    paginationOffset = if (fetchOffset > 0) fetchOffset + 60 else list.size
                    hasMoreServerVideos = list.size >= 50 || fetchOffset > 0
                    hasMoreVideos = list.size >= 2
                    _uiState.update {
                        it.copy(
                            videosState = UiState.Success(list),
                            isLoadingMore = false,
                            feedVersion = it.feedVersion + 1,
                            activeAds = ads
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            videosState = UiState.Error(err.message ?: "Failed to load feed"),
                            isLoadingMore = false
                        )
                    }
                }
            )
        }
    }

    fun loadMoreIfNeeded(currentIndex: Int) {
        val state = _uiState.value
        if (state.isLoadingMore || !hasMoreVideos) return
        val current = (state.videosState as? UiState.Success)?.data ?: return
        if (current.isEmpty()) return
        val triggerThreshold = if (current.size <= 3) 0 else (current.size - 3)
        if (currentIndex < triggerThreshold) return

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val isForYou = state.selectedTab == FeedTab.FOR_YOU
            if (hasMoreServerVideos) {
                val result = repository.fetchMoreVideos(
                    forYou = isForYou,
                    offset = paginationOffset,
                    existingIds = current.mapTo(HashSet()) { it.id },
                    reloadCycle = activeReloadCycle
                )
                result.fold(
                    onSuccess = { batch ->
                        paginationOffset += batch.rawFetchedCount
                        if (batch.rawFetchedCount < 50) {
                            hasMoreServerVideos = false
                        }
                        if (batch.videos.isNotEmpty()) {
                            _uiState.update { latest ->
                                val latestList = (latest.videosState as? UiState.Success)?.data ?: current
                                val merged = (latestList + batch.videos).distinctBy { it.id }
                                latest.copy(
                                    videosState = UiState.Success(merged),
                                    isLoadingMore = false
                                )
                            }
                        } else {
                            appendOlderReplayBatch(isForYou = isForYou, currentSnapshot = current)
                        }
                    },
                    onFailure = {
                        _uiState.update { it.copy(isLoadingMore = false) }
                    }
                )
            } else {
                appendOlderReplayBatch(isForYou = isForYou, currentSnapshot = current)
            }
        }
    }

    private suspend fun appendOlderReplayBatch(
        isForYou: Boolean,
        currentSnapshot: List<VideoItem>
    ) {
        if (currentSnapshot.size <= 1) {
            hasMoreVideos = false
            _uiState.update { it.copy(isLoadingMore = false) }
            return
        }
        val excludeTailIds = currentSnapshot
            .takeLast(minOf(2, currentSnapshot.size - 1))
            .map { it.id }
            .toSet()
        val replayResult = repository.fetchOlderReplayBatch(
            forYou = isForYou,
            excludeTailIds = excludeTailIds,
            reloadCycle = ++replayBatchCycle
        )
        replayResult.fold(
            onSuccess = { replayVideos ->
                if (replayVideos.isEmpty()) {
                    hasMoreVideos = false
                    _uiState.update { it.copy(isLoadingMore = false) }
                } else {
                    _uiState.update { latest ->
                        val latestList = (latest.videosState as? UiState.Success)?.data ?: currentSnapshot
                        // Keep list bounded if user scrolls endlessly, while allowing one-by-one older cycling
                        latest.copy(
                            videosState = UiState.Success(latestList + replayVideos),
                            isLoadingMore = false
                        )
                    }
                }
            },
            onFailure = {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        )
    }

    fun onActivePageChanged(index: Int) {
        activePageIndex = index
    }

    fun onVideoViewed(video: VideoItem) {
        viewModelScope.launch { repository.recordVideoView(video) }
    }

    fun onWatchMilestone(video: VideoItem, pageIndex: Int, watchedMs: Long) {
        val (tier, weight) = when {
            watchedMs >= 25_000L -> 4 to 5
            watchedMs >= 15_000L -> 3 to 4
            watchedMs >= 8_500L -> 2 to 3
            watchedMs >= 3_500L -> 1 to 2
            else -> return
        }
        if (!recordedMilestones.add("${video.id}_${pageIndex}_$tier")) return
        viewModelScope.launch {
            repository.recordWatchSignal(video, weight = weight)
            reRankUpcomingVideos(afterIndex = pageIndex)
        }
    }

    fun onVideoLeft(
        video: VideoItem,
        pageIndex: Int,
        watchedMs: Long,
        feedVersion: Int = _uiState.value.feedVersion
    ) {
        if (feedVersion == suppressedLeaveFeedVersion && watchedMs < 3_500L) return
        viewModelScope.launch {
            when {
                watchedMs < 2_000L && !video.isLiked && !video.isSaved -> {
                    repository.recordWatchSignal(video, weight = -4)
                    reRankUpcomingVideos(afterIndex = pageIndex)
                }
                watchedMs in 2_000L..3_499L -> {
                    if (recordedMilestones.add("${video.id}_${pageIndex}_0")) {
                        repository.recordWatchSignal(video, weight = 1)
                    }
                }
                watchedMs >= 3_500L -> {
                    onWatchMilestone(video, pageIndex, watchedMs)
                }
            }
        }
    }

    fun onVideoShared(video: VideoItem) {
        viewModelScope.launch {
            repository.recordWatchSignal(video, weight = 8)
            repository.recordVideoShare(video.id)
            updateVideoInList(video.id) { it.copy(sharesCount = it.sharesCount + 1) }
            reRankUpcomingVideos(afterIndex = activePageIndex)
        }
    }

    fun toggleLike(video: VideoItem, forceLike: Boolean = false) {
        if (forceLike && video.isLiked) return
        viewModelScope.launch {
            val optimisticLiked = !video.isLiked
            val delta = if (optimisticLiked) 1 else -1
            repository.recordWatchSignal(video, weight = if (optimisticLiked) 6 else -4)
            updateVideoInList(video.id) {
                it.copy(isLiked = optimisticLiked, likesCount = (it.likesCount + delta).coerceAtLeast(0))
            }
            if (optimisticLiked) {
                reRankUpcomingVideos(afterIndex = activePageIndex)
            }
            val res = repository.toggleLike(video.id)
            if (res.isFailure) {
                updateVideoInList(video.id) {
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

    fun toggleSave(video: VideoItem) {
        viewModelScope.launch {
            val optimisticSaved = !video.isSaved
            val delta = if (optimisticSaved) 1 else -1
            repository.recordWatchSignal(video, weight = if (optimisticSaved) 6 else -4)
            updateVideoInList(video.id) {
                it.copy(isSaved = optimisticSaved, savesCount = (it.savesCount + delta).coerceAtLeast(0))
            }
            if (optimisticSaved) {
                reRankUpcomingVideos(afterIndex = activePageIndex)
            }
            val res = repository.toggleSave(video.id, video.isSaved)
            if (res.isFailure) {
                updateVideoInList(video.id) { it.copy(isSaved = video.isSaved, savesCount = video.savesCount) }
                val msg = res.exceptionOrNull()?.message ?: "Sign in required"
                _uiState.update {
                    it.copy(feedbackMessage = msg, requireAuthPrompt = msg.contains("Sign in", ignoreCase = true))
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
                    reRankUpcomingVideos(afterIndex = activePageIndex)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Sign in required"
                    _uiState.update {
                        it.copy(feedbackMessage = msg, requireAuthPrompt = msg.contains("Sign in", ignoreCase = true))
                    }
                }
            )
        }
    }

    fun openComments(videoId: String) {
        _uiState.update { it.copy(commentsVideoId = videoId, commentsState = UiState.Loading) }
        viewModelScope.launch {
            repository.getComments(videoId).fold(
                onSuccess = { list -> _uiState.update { it.copy(commentsState = UiState.Success(list)) } },
                onFailure = { err ->
                    _uiState.update { it.copy(commentsState = UiState.Error(err.message ?: "Failed to load comments")) }
                }
            )
        }
    }

    fun closeComments() {
        _uiState.update { it.copy(commentsVideoId = null) }
    }

    fun submitComment(videoId: String, text: String, parentId: String? = null) {
        if (text.isBlank()) return
        val currentVideo = (_uiState.value.videosState as? UiState.Success)?.data?.firstOrNull { it.id == videoId }
        val currentCount = currentVideo?.commentsCount ?: 0
        viewModelScope.launch {
            repository.postComment(videoId, text, parentId, currentCount).fold(
                onSuccess = {
                    currentVideo?.let { repository.recordWatchSignal(it, weight = 7) }
                    updateVideoInList(videoId) { v -> v.copy(commentsCount = v.commentsCount + 1) }
                    reRankUpcomingVideos(afterIndex = activePageIndex)
                    openComments(videoId)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Sign in to comment"
                    _uiState.update {
                        it.copy(feedbackMessage = msg, requireAuthPrompt = msg.contains("Sign in", ignoreCase = true))
                    }
                }
            )
        }
    }

    fun openGiftSheet(video: VideoItem) {
        _uiState.update { it.copy(giftTargetVideo = video) }
        viewModelScope.launch {
            val catalog = repository.getGifts()
            _uiState.update { it.copy(gifts = catalog) }
        }
    }

    fun closeGiftSheet() {
        _uiState.update { it.copy(giftTargetVideo = null) }
    }

    fun sendGift(video: VideoItem, gift: GiftItem, onCoinsUpdated: (Int) -> Unit) {
        viewModelScope.launch {
            repository.sendGift(video.userId, video.id, gift).fold(
                onSuccess = { remaining ->
                    onCoinsUpdated(remaining)
                    _uiState.update {
                        it.copy(
                            giftTargetVideo = null,
                            feedbackMessage = "Sent ${gift.name}!"
                        )
                    }
                },
                onFailure = { err ->
                    val msg = err.message ?: "Unable to send gift"
                    _uiState.update {
                        it.copy(feedbackMessage = msg, requireAuthPrompt = msg.contains("Sign in", ignoreCase = true))
                    }
                }
            )
        }
    }

    fun reportVideo(video: VideoItem, reason: String) {
        viewModelScope.launch {
            repository.reportVideo(video.id, video.userId, reason).fold(
                onSuccess = { _uiState.update { it.copy(feedbackMessage = "Report submitted. Thank you.") } },
                onFailure = { err -> _uiState.update { it.copy(feedbackMessage = err.message ?: "Sign in to report") } }
            )
        }
    }

    fun blockCreator(userId: String) {
        viewModelScope.launch {
            repository.blockUser(userId).fold(
                onSuccess = {
                    val current = (_uiState.value.videosState as? UiState.Success)?.data ?: emptyList()
                    _uiState.update {
                        it.copy(
                            videosState = UiState.Success(current.filterNot { v -> v.userId == userId }),
                            feedbackMessage = "Creator blocked"
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(feedbackMessage = err.message ?: "Sign in to block") } }
            )
        }
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

    private suspend fun reRankUpcomingVideos(afterIndex: Int) {
        val current = (_uiState.value.videosState as? UiState.Success)?.data ?: return
        val pivot = (afterIndex + 1).coerceIn(0, current.size)
        if (pivot >= current.size - 1) return
        val prefix = current.take(pivot)
        val tail = current.drop(pivot).distinctBy { it.id }
        val activeDemote = listOfNotNull(current.getOrNull(afterIndex)?.id).toSet()
        val reorderedTail = repository.rankUpcomingVideos(
            videos = tail,
            forYou = _uiState.value.selectedTab == FeedTab.FOR_YOU,
            demoteIds = activeDemote,
            reloadCycle = activeReloadCycle
        )
        _uiState.update { it.copy(videosState = UiState.Success(prefix + reorderedTail)) }
    }

    private fun updateVideoInList(videoId: String, transform: (VideoItem) -> VideoItem) {
        val current = (_uiState.value.videosState as? UiState.Success)?.data ?: return
        _uiState.update {
            it.copy(videosState = UiState.Success(current.map { v -> if (v.id == videoId) transform(v) else v }))
        }
    }
}
