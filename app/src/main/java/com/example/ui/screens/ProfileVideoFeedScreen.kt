package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ModeComment
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.bladeStrings
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.ui.components.BladeAvatar
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.VideoPlayerBox
import com.example.ui.components.VideoRailActionButton
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.BladeCountCrisp
import com.example.ui.theme.BladeCountLiked
import com.example.ui.theme.BladeCountSaved
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeGold
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileVideoFeedScreen(
    tabIndex: Int,
    startIndex: Int,
    isOwnProfile: Boolean,
    currentUser: UserProfile?,
    profileViewModel: ProfileViewModel,
    feedViewModel: FeedViewModel,
    onBack: () -> Unit,
    onNavigateToCreator: (String) -> Unit,
    onVideoDeleted: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val context = LocalContext.current
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val feedUiState by feedViewModel.uiState.collectAsStateWithLifecycle()

    val videos = when (tabIndex) {
        0 -> profileUiState.uploadedVideos
        1 -> profileUiState.likedVideos
        else -> profileUiState.savedVideos
    }

    var pendingDeleteVideo by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(profileUiState.feedbackMessage) {
        profileUiState.feedbackMessage?.let { msg ->
            onShowMessage(msg)
            profileViewModel.clearFeedback()
        }
    }

    LaunchedEffect(videos.isEmpty()) {
        if (videos.isEmpty()) {
            onBack()
        }
    }

    if (videos.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BladeCrimson)
        }
        return
    }

    val safeStartIndex = startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeStartIndex,
        pageCount = { videos.size }
    )

    val currentPageIndex = pagerState.currentPage.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    val currentVideo = videos.getOrNull(currentPageIndex)
    val canDeleteCurrent = currentVideo != null &&
        currentUser?.id != null &&
        currentVideo.userId == currentUser.id &&
        (isOwnProfile || tabIndex == 0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { page -> videos[page].id },
            modifier = Modifier
                .fillMaxSize()
                .testTag("profile_video_vertical_pager")
        ) { page ->
            val video = videos[page]
            val isActive = pagerState.currentPage == page
            ProfileVideoPagerPage(
                video = video,
                isActive = isActive,
                onStartedPlayback = { feedViewModel.onVideoViewed(video) },
                onDoubleTapLike = { profileViewModel.toggleVideoLike(video, forceLike = true) },
                onLikeClick = { profileViewModel.toggleVideoLike(video) },
                onCommentClick = { feedViewModel.openComments(video.id) },
                onSaveClick = { profileViewModel.toggleVideoSave(video) },
                onShareClick = {
                    profileViewModel.shareProfileVideo(video)
                    val shareLabel = video.title.ifBlank { video.caption }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Watch @${video.creator?.username ?: "creator"} on Blade: $shareLabel\n${video.videoUrl}"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, strings.share))
                },
                onCreatorClick = { onNavigateToCreator(video.userId) }
            )
        }

        // Top Floating Header: Back + Position Counter + Delete Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .testTag("profile_player_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite
                )
            }

            val tabTitle = when (tabIndex) {
                0 -> strings.videosTab
                1 -> strings.likedTab
                else -> strings.savedTab
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "$tabTitle • ${currentPageIndex + 1} / ${videos.size}",
                    color = TextWhite,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            if (canDeleteCurrent && currentVideo != null) {
                Surface(
                    onClick = { pendingDeleteVideo = currentVideo },
                    enabled = profileUiState.deletingVideoId == null,
                    shape = RoundedCornerShape(50),
                    color = BladeCrimson.copy(alpha = 0.88f),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("profile_player_delete_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (profileUiState.deletingVideoId == currentVideo.id) {
                            CircularProgressIndicator(
                                color = TextWhite,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete video",
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Delete",
                            color = TextWhite,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
    }

    // Comments Sheet
    feedUiState.commentsVideoId?.let { videoId ->
        CommentsBottomSheet(
            commentsState = feedUiState.commentsState,
            onDismiss = { feedViewModel.closeComments() },
            onPostComment = { text, parentId ->
                feedViewModel.submitComment(videoId, text, parentId)
                profileViewModel.incrementVideoCommentCount(videoId)
            },
            onCreatorClick = { uid ->
                feedViewModel.closeComments()
                onNavigateToCreator(uid)
            }
        )
    }

    // Delete Confirmation Dialog
    pendingDeleteVideo?.let { target ->
        val isDeleting = profileUiState.deletingVideoId == target.id
        val label = target.title.ifBlank { target.caption }.ifBlank { "this video" }
        AlertDialog(
            onDismissRequest = { if (!isDeleting) pendingDeleteVideo = null },
            title = { Text("Delete Video?") },
            text = {
                Text("Are you sure you want to permanently delete \"$label\"? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        profileViewModel.deleteUploadedVideo(target.id) {
                            onVideoDeleted(target.id)
                            pendingDeleteVideo = null
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson, contentColor = TextWhite),
                    modifier = Modifier.testTag("confirm_delete_video_button")
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = TextWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (isDeleting) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteVideo = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileVideoPagerPage(
    video: VideoItem,
    isActive: Boolean,
    onStartedPlayback: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onCreatorClick: () -> Unit
) {
    val creatorName = video.creator?.username?.ifBlank { "creator" } ?: "creator"
    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(0f, 1.5f),
            blurRadius = 4f
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VideoPlayerBox(
            videoUri = video.videoUrl,
            isActive = isActive,
            filterName = video.filterName,
            trimStartMs = video.trimStartMs,
            trimEndMs = video.trimEndMs,
            durationMs = video.durationMs,
            onDoubleTapLike = onDoubleTapLike,
            onStartedPlayback = onStartedPlayback
        )

        // Right Vertical Action Rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCreatorClick),
                contentAlignment = Alignment.Center
            ) {
                BladeAvatar(
                    avatarUrl = video.creator?.avatarUrl ?: "",
                    username = creatorName,
                    size = 44.dp
                )
            }

            val likeScale by animateFloatAsState(
                targetValue = if (video.isLiked) 1.15f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "profileLikeScale"
            )

            VideoRailActionButton(
                icon = if (video.isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                tint = if (video.isLiked) BladeCrimson else TextWhite,
                label = formatCompactCount(video.likesCount),
                labelColor = if (video.isLiked) BladeCountLiked else BladeCountCrisp,
                onClick = onLikeClick,
                iconModifier = Modifier.scale(likeScale),
                modifier = Modifier.testTag("profile_player_like_${video.id}")
            )

            VideoRailActionButton(
                icon = Icons.Rounded.ModeComment,
                tint = TextWhite,
                label = formatCompactCount(video.commentsCount),
                labelColor = BladeCountCrisp,
                onClick = onCommentClick,
                modifier = Modifier.testTag("profile_player_comment_${video.id}")
            )

            VideoRailActionButton(
                icon = if (video.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                tint = if (video.isSaved) BladeGold else TextWhite,
                label = formatCompactCount(video.savesCount),
                labelColor = if (video.isSaved) BladeCountSaved else BladeCountCrisp,
                onClick = onSaveClick,
                modifier = Modifier.testTag("profile_player_save_${video.id}")
            )

            VideoRailActionButton(
                icon = ProfileShareIcon,
                tint = TextWhite,
                label = formatCompactCount(video.sharesCount),
                labelColor = BladeCountCrisp,
                onClick = onShareClick,
                modifier = Modifier.testTag("profile_player_share_${video.id}")
            )
        }

        // Bottom Left Video Metadata
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .fillMaxWidth(0.76f)
                .padding(start = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onCreatorClick)
            ) {
                Text(
                    text = "@$creatorName",
                    color = TextWhite,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = textShadow
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.creator?.isVerified == true) {
                    Spacer(Modifier.width(5.dp))
                    VerifiedBadge(size = 15.dp)
                }
            }

            if (video.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = TextWhite.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = video.location,
                        color = TextWhite.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (video.title.isNotBlank()) {
                Text(
                    text = video.title,
                    color = TextWhite,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = textShadow
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (video.caption.isNotBlank()) {
                Text(
                    text = video.caption,
                    color = TextWhite,
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val ProfileShareIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfileTikTokShare",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(13.8f, 3.8f)
            curveTo(13.8f, 3.1f, 14.6f, 2.7f, 15.1f, 3.2f)
            lineTo(22.1f, 9.7f)
            curveTo(22.5f, 10.1f, 22.5f, 10.7f, 22.1f, 11.1f)
            lineTo(15.1f, 17.6f)
            curveTo(14.6f, 18.1f, 13.8f, 17.7f, 13.8f, 17.0f)
            lineTo(13.8f, 13.6f)
            curveTo(8.9f, 13.6f, 5.4f, 15.2f, 3.0f, 19.3f)
            curveTo(2.6f, 20.0f, 1.6f, 19.6f, 1.7f, 18.8f)
            curveTo(2.7f, 12.1f, 6.7f, 7.6f, 13.8f, 7.2f)
            close()
        }
    }.build()
}
