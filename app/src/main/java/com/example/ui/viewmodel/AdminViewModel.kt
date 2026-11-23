package com.example.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminAppSettings
import com.example.data.model.AdminDashboardStats
import com.example.data.model.UiState
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminTab { OVERVIEW, USERS, VIDEOS, MODERATION, SETTINGS }
enum class UserFilter { ALL, PENDING, VERIFIED, REJECTED, BLOCKED, ADMINS }
enum class VideoFilter { ALL, PUBLIC, FRIENDS, PRIVATE, SPONSORED }
enum class ReportFilter { ALL, PENDING, REVIEWED, DISMISSED }

data class AdminUiState(
    val isAdminAuthenticated: Boolean = false,
    val adminId: String = "",
    val isVerifyingGate: Boolean = false,
    val gateError: String? = null,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val selectedTab: AdminTab = AdminTab.OVERVIEW,
    val dashboardState: UiState<AdminDashboardStats> = UiState.Loading,
    val userQuery: String = "",
    val userFilter: UserFilter = UserFilter.ALL,
    val videoQuery: String = "",
    val videoFilter: VideoFilter = VideoFilter.ALL,
    val commentQuery: String = "",
    val reportFilter: ReportFilter = ReportFilter.ALL,
    val isUploadingVideo: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploadError: String? = null,
    val isPerformingAction: Boolean = false,
    val feedbackMessage: String? = null
)

class AdminViewModel(private val repository: BladeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.adminSessionFlow.collect { session ->
                val wasAuth = _uiState.value.isAdminAuthenticated
                _uiState.update {
                    it.copy(
                        isAdminAuthenticated = session.isAuthenticated,
                        adminId = session.adminId
                    )
                }
                if (session.isAuthenticated && !wasAuth) {
                    loadDashboard()
                }
            }
        }
    }

    fun verifyGatePassword(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingGate = true, gateError = null) }
            repository.verifyAdminGatePassword(password).fold(
                onSuccess = {
                    _uiState.update { it.copy(isVerifyingGate = false, gateError = null) }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isVerifyingGate = false,
                            gateError = err.message ?: "Incorrect password."
                        )
                    }
                }
            )
        }
    }

    fun loginAdmin(adminId: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            repository.authenticateAdmin(adminId, password).fold(
                onSuccess = { cleanId ->
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            isAdminAuthenticated = true,
                            adminId = cleanId,
                            loginError = null
                        )
                    }
                    loadDashboard()
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = err.message ?: "Admin authentication failed."
                        )
                    }
                }
            )
        }
    }

    fun logoutAdmin(onLoggedOut: () -> Unit = {}) {
        viewModelScope.launch {
            repository.signOutAdmin()
            _uiState.update {
                AdminUiState(
                    isAdminAuthenticated = false,
                    adminId = "",
                    feedbackMessage = "Signed out of Admin Panel"
                )
            }
            onLoggedOut()
        }
    }

    fun selectTab(tab: AdminTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            if (_uiState.value.dashboardState !is UiState.Success) {
                _uiState.update { it.copy(dashboardState = UiState.Loading) }
            }
            repository.getAdminDashboardStats().fold(
                onSuccess = { stats ->
                    _uiState.update { it.copy(dashboardState = UiState.Success(stats)) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(dashboardState = UiState.Error(err.message ?: "Failed to load admin data"))
                    }
                }
            )
        }
    }

    fun updateUserQuery(query: String) {
        _uiState.update { it.copy(userQuery = query) }
    }

    fun updateUserFilter(filter: UserFilter) {
        _uiState.update { it.copy(userFilter = filter) }
    }

    fun updateVideoQuery(query: String) {
        _uiState.update { it.copy(videoQuery = query) }
    }

    fun updateVideoFilter(filter: VideoFilter) {
        _uiState.update { it.copy(videoFilter = filter) }
    }

    fun updateCommentQuery(query: String) {
        _uiState.update { it.copy(commentQuery = query) }
    }

    fun updateReportFilter(filter: ReportFilter) {
        _uiState.update { it.copy(reportFilter = filter) }
    }

    fun verifyUser(userId: String, onSynced: () -> Unit = {}) {
        updateUserVerificationStatus(userId, "verified", onSynced)
    }

    fun rejectUser(userId: String, onSynced: () -> Unit = {}) {
        updateUserVerificationStatus(userId, "rejected", onSynced)
    }

    fun toggleUserVerified(userId: String, verify: Boolean, onSynced: () -> Unit = {}) {
        updateUserVerificationStatus(userId, if (verify) "verified" else "rejected", onSynced)
    }

    private fun updateUserVerificationStatus(
        userId: String,
        status: String,
        onSynced: () -> Unit = {}
    ) {
        val isVerified = status == "verified"
        val successMsg = when (status) {
            "verified" -> "User verified successfully"
            "rejected" -> "User verification rejected"
            else -> "User marked pending verification"
        }
        performAction(
            successMsg = successMsg,
            onBeforeReload = {
                mutateDashboard { stats ->
                    val updatedUsers = stats.recentUsers.map { u ->
                        if (u.id == userId) u.copy(isVerified = isVerified, verificationStatus = status) else u
                    }
                    val updatedVideos = stats.recentVideos.map { v ->
                        if (v.creator?.id == userId) {
                            v.copy(creator = v.creator.copy(isVerified = isVerified, verificationStatus = status))
                        } else v
                    }
                    stats.copy(
                        recentUsers = updatedUsers,
                        recentVideos = updatedVideos,
                        verifiedUsersCount = updatedUsers.count { it.isVerified },
                        pendingVerificationUsersCount = updatedUsers.count { it.resolvedVerificationStatus == "pending" }
                    )
                }
            },
            onAfterSuccess = onSynced
        ) {
            repository.adminSetUserVerificationStatus(userId, status)
        }
    }

    fun setUserBlocked(userId: String, blocked: Boolean, onUserStateChanged: () -> Unit = {}) {
        performAction(
            successMsg = if (blocked) "User blocked" else "User unblocked",
            onBeforeReload = {
                mutateDashboard { stats ->
                    val updatedUsers = stats.recentUsers.map { u ->
                        if (u.id == userId) u.copy(isBlocked = blocked) else u
                    }
                    val updatedVideos = stats.recentVideos.map { v ->
                        if (v.creator?.id == userId) v.copy(creator = v.creator.copy(isBlocked = blocked)) else v
                    }
                    stats.copy(
                        recentUsers = updatedUsers,
                        recentVideos = updatedVideos,
                        blockedUsersCount = updatedUsers.count { it.isBlocked }
                    )
                }
            },
            onAfterSuccess = onUserStateChanged
        ) {
            repository.adminSetUserBlocked(userId, blocked)
        }
    }

    fun updateUserAccount(
        userId: String,
        displayName: String,
        username: String,
        bio: String,
        role: String,
        coinsBalance: Int,
        isVerified: Boolean,
        isBlocked: Boolean,
        onComplete: () -> Unit
    ) {
        performAction(
            successMsg = "User account updated",
            onAfterSuccess = onComplete
        ) {
            repository.adminUpdateUserAccount(
                userId = userId,
                displayName = displayName,
                username = username,
                bio = bio,
                role = role,
                coinsBalance = coinsBalance,
                isVerified = isVerified,
                isBlocked = isBlocked
            )
        }
    }

    fun deleteUser(userId: String, onFeedMutated: () -> Unit = {}) {
        performAction(
            successMsg = "User account deleted",
            onBeforeReload = {
                mutateDashboard { stats ->
                    val remainingUsers = stats.recentUsers.filterNot { it.id == userId }
                    val remainingVideos = stats.recentVideos.filterNot { it.userId == userId }
                    stats.copy(
                        totalUsers = remainingUsers.size,
                        totalVideos = remainingVideos.size,
                        recentUsers = remainingUsers,
                        recentVideos = remainingVideos,
                        verifiedUsersCount = remainingUsers.count { it.isVerified },
                        pendingVerificationUsersCount = remainingUsers.count { it.resolvedVerificationStatus == "pending" },
                        blockedUsersCount = remainingUsers.count { it.isBlocked }
                    )
                }
            },
            onAfterSuccess = onFeedMutated
        ) {
            repository.adminDeleteUser(userId)
        }
    }

    fun publishVideoForUser(
        contentResolver: ContentResolver,
        targetUserId: String,
        targetUsername: String,
        videoUri: Uri?,
        remoteVideoUrl: String,
        title: String,
        caption: String,
        category: String,
        location: String,
        privacy: String,
        onPublished: () -> Unit
    ) {
        if (targetUserId.isBlank()) {
            _uiState.update { it.copy(uploadError = "Select a user account to post this video under.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingVideo = true, uploadProgress = 0.05f, uploadError = null) }
            repository.adminPublishVideoForUser(
                contentResolver = contentResolver,
                targetUserId = targetUserId,
                videoUri = videoUri,
                remoteVideoUrl = remoteVideoUrl,
                title = title,
                caption = caption,
                category = category,
                location = location,
                privacy = privacy,
                onProgress = { progress ->
                    _uiState.update { it.copy(uploadProgress = progress) }
                }
            ).fold(
                onSuccess = { created ->
                    mutateDashboard { stats ->
                        val updatedVideos = (listOf(created) + stats.recentVideos).distinctBy { it.id }
                        stats.copy(
                            totalVideos = updatedVideos.size,
                            recentVideos = updatedVideos
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isUploadingVideo = false,
                            uploadProgress = 0f,
                            uploadError = null,
                            feedbackMessage = "Video published under @$targetUsername"
                        )
                    }
                    loadDashboard()
                    onPublished()
                },
                onFailure = { err ->
                    val message = err.message ?: "Admin video upload failed"
                    _uiState.update {
                        it.copy(
                            isUploadingVideo = false,
                            uploadProgress = 0f,
                            uploadError = message,
                            feedbackMessage = message
                        )
                    }
                }
            )
        }
    }

    fun updateVideoData(
        videoId: String,
        title: String,
        caption: String,
        category: String,
        privacy: String,
        viewsCount: Int,
        likesCount: Int,
        sharesCount: Int,
        savesCount: Int,
        isSponsored: Boolean,
        sponsorLabel: String,
        onComplete: () -> Unit
    ) {
        performAction(
            successMsg = "Video details & stats updated",
            onAfterSuccess = onComplete
        ) {
            repository.adminUpdateVideoData(
                videoId = videoId,
                title = title,
                caption = caption,
                category = category,
                privacy = privacy,
                viewsCount = viewsCount,
                likesCount = likesCount,
                sharesCount = sharesCount,
                savesCount = savesCount,
                isSponsored = isSponsored,
                sponsorLabel = sponsorLabel
            )
        }
    }

    fun deleteVideo(videoId: String, onVideoDeleted: () -> Unit = {}) {
        performAction(
            successMsg = "Video permanently deleted",
            onBeforeReload = {
                mutateDashboard { stats ->
                    val remainingVideos = stats.recentVideos.filterNot { it.id == videoId }
                    val remainingComments = stats.recentComments.filterNot { it.comment.videoId == videoId }
                    stats.copy(
                        totalVideos = remainingVideos.size,
                        totalComments = remainingComments.size,
                        totalViews = remainingVideos.sumOf { it.viewsCount.toLong() },
                        totalLikes = remainingVideos.sumOf { it.likesCount.toLong() },
                        recentVideos = remainingVideos,
                        recentComments = remainingComments
                    )
                }
            },
            onAfterSuccess = onVideoDeleted
        ) {
            repository.adminDeleteVideo(videoId)
        }
    }

    fun deleteComment(commentId: String, videoId: String, currentVideoCommentsCount: Int, onSynced: () -> Unit = {}) {
        performAction(
            successMsg = "Comment removed",
            onBeforeReload = {
                mutateDashboard { stats ->
                    val remaining = stats.recentComments.filterNot { it.comment.id == commentId }
                    val updatedVideos = stats.recentVideos.map { v ->
                        if (v.id == videoId) v.copy(commentsCount = (v.commentsCount - 1).coerceAtLeast(0)) else v
                    }
                    stats.copy(
                        totalComments = remaining.size,
                        recentComments = remaining,
                        recentVideos = updatedVideos
                    )
                }
            },
            onAfterSuccess = onSynced
        ) {
            repository.adminDeleteComment(commentId, videoId, currentVideoCommentsCount)
        }
    }

    fun resolveReport(reportId: String, status: String) {
        performAction(
            successMsg = "Report marked $status",
            onBeforeReload = {
                mutateDashboard { stats ->
                    val updated = stats.pendingReports.map { r ->
                        if (r.id == reportId) r.copy(status = status) else r
                    }
                    stats.copy(
                        pendingReports = updated,
                        pendingReportsCount = updated.count { it.status == "pending" }
                    )
                }
            }
        ) {
            repository.resolveReport(reportId, status)
        }
    }

    fun deleteReport(reportId: String) {
        performAction(
            successMsg = "Report deleted",
            onBeforeReload = {
                mutateDashboard { stats ->
                    val remaining = stats.pendingReports.filterNot { it.id == reportId }
                    stats.copy(
                        pendingReports = remaining,
                        pendingReportsCount = remaining.count { it.status == "pending" }
                    )
                }
            }
        ) {
            repository.adminDeleteReport(reportId)
        }
    }

    fun saveAppSettings(settings: AdminAppSettings) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformingAction = true) }
            repository.adminSaveAppSettings(settings).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isPerformingAction = false, feedbackMessage = "App settings saved")
                    }
                    loadDashboard()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            feedbackMessage = err.message ?: "Failed to save settings"
                        )
                    }
                }
            )
        }
    }

    fun updateAdminCredentials(
        currentPassword: String,
        newAdminId: String,
        newPassword: String,
        onUpdated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformingAction = true) }
            repository.adminUpdateCredentials(currentPassword, newAdminId, newPassword).fold(
                onSuccess = { cleanId ->
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            adminId = cleanId,
                            feedbackMessage = "Admin credentials updated"
                        )
                    }
                    onUpdated()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            feedbackMessage = err.message ?: "Failed to update admin credentials"
                        )
                    }
                }
            )
        }
    }

    private fun mutateDashboard(transform: (AdminDashboardStats) -> AdminDashboardStats) {
        _uiState.update { state ->
            val current = (state.dashboardState as? UiState.Success)?.data ?: return@update state
            state.copy(dashboardState = UiState.Success(transform(current)))
        }
    }

    private fun performAction(
        successMsg: String,
        onBeforeReload: () -> Unit = {},
        onAfterSuccess: () -> Unit = {},
        block: suspend () -> Result<Unit>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformingAction = true) }
            block().fold(
                onSuccess = {
                    onBeforeReload()
                    _uiState.update {
                        it.copy(isPerformingAction = false, feedbackMessage = successMsg)
                    }
                    loadDashboard()
                    onAfterSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            feedbackMessage = err.message ?: "Action failed"
                        )
                    }
                }
            )
        }
    }

    fun clearUploadError() {
        _uiState.update { it.copy(uploadError = null) }
    }

    fun clearGateError() {
        _uiState.update { it.copy(gateError = null) }
    }

    fun clearLoginError() {
        _uiState.update { it.copy(loginError = null) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
