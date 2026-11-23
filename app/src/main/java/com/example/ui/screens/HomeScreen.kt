package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ModeComment
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.i18n.bladeStrings
import com.example.data.model.AdItem
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.ui.components.BladeAvatar
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.VideoOptionsSheet
import com.example.ui.components.VideoPlayerBox
import com.example.ui.components.VideoRailActionButton
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.BladeCountCrisp
import com.example.ui.theme.BladeCountLiked
import com.example.ui.theme.BladeCountSaved
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeGold
import com.example.ui.theme.BladeObsidian
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.FeedTab
import com.example.ui.viewmodel.FeedViewModel

@Composable
fun HomeScreen(
    feedViewModel: FeedViewModel,
    currentUser: UserProfile?,
    isSupabaseConfigured: Boolean,
    onNavigateToSearch: () -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    onNavigateToSound: (String) -> Unit,
    onNavigateToCreator: (String) -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val context = LocalContext.current
    val uiState by feedViewModel.uiState.collectAsStateWithLifecycle()

    var optionsVideo by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            onShowMessage(msg)
            if (uiState.requireAuthPrompt) {
                onNavigateToAuth()
            }
            feedViewModel.clearFeedback()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = uiState.videosState) {
            is UiState.Loading -> {
                CircularProgressIndicator(
                    color = BladeCrimson,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is UiState.Error -> {
                EmptyOrErrorFeedState(
                    title = "Feed Unavailable",
                    subtitle = state.message,
                    isSupabaseConfigured = isSupabaseConfigured,
                    onRetry = { feedViewModel.loadFeed(isFreshReload = true) },
                    onCreate = onNavigateToCreate
                )
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyOrErrorFeedState(
                        title = strings.noVideosTitle,
                        subtitle = strings.noVideosSubtitle,
                        isSupabaseConfigured = isSupabaseConfigured,
                        onRetry = { feedViewModel.loadFeed(isFreshReload = true) },
                        onCreate = onNavigateToCreate
                    )
                } else {
                    val videos = state.data
                    val pagerState = rememberPagerState(pageCount = { videos.size })

                    LaunchedEffect(uiState.feedVersion) {
                        if (videos.isNotEmpty()) {
                            pagerState.scrollToPage(0)
                        }
                    }

                    val currentPageIndex = pagerState.currentPage.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
                    val activeVideo = videos.getOrNull(currentPageIndex)
                    val latestActiveVideo by rememberUpdatedState(activeVideo)

                    LaunchedEffect(currentPageIndex, videos.size) {
                        feedViewModel.loadMoreIfNeeded(currentPageIndex)
                    }

                    LaunchedEffect(activeVideo?.id, currentPageIndex, uiState.feedVersion) {
                        val current = activeVideo ?: return@LaunchedEffect
                        feedViewModel.onActivePageChanged(currentPageIndex)
                        delay(3_500L)
                        feedViewModel.onWatchMilestone(current, currentPageIndex, 3_500L)
                        delay(5_000L)
                        feedViewModel.onWatchMilestone(current, currentPageIndex, 8_500L)
                        delay(7_000L)
                        feedViewModel.onWatchMilestone(current, currentPageIndex, 15_500L)
                        delay(10_000L)
                        feedViewModel.onWatchMilestone(current, currentPageIndex, 25_500L)
                    }

                    DisposableEffect(activeVideo?.id, currentPageIndex, uiState.feedVersion) {
                        val startedAtMs = SystemClock.elapsedRealtime()
                        val capturedFeedVersion = uiState.feedVersion
                        onDispose {
                            val leftVideo = latestActiveVideo
                            if (leftVideo != null && leftVideo.id == activeVideo?.id) {
                                val watchedMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                                feedViewModel.onVideoLeft(leftVideo, currentPageIndex, watchedMs, capturedFeedVersion)
                            }
                        }
                    }

                    VerticalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        key = { page -> pageItemKey(videos, page) },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val video = videos[page]
                        val isActive = pagerState.currentPage == page
                        val nativeAd = if (uiState.activeAds.isNotEmpty() && page % 5 == 3) {
                            uiState.activeAds[(page / 5) % uiState.activeAds.size]
                        } else null

                        VideoFeedPage(
                            video = video,
                            nativeAd = nativeAd,
                            isActive = isActive,
                            currentUserId = currentUser?.id,
                            onStartedPlayback = { feedViewModel.onVideoViewed(video) },
                            onDoubleTapLike = { feedViewModel.toggleLike(video, forceLike = true) },
                            onLikeClick = { feedViewModel.toggleLike(video) },
                            onCommentClick = { feedViewModel.openComments(video.id) },
                            onSaveClick = { feedViewModel.toggleSave(video) },
                            onShareClick = {
                                feedViewModel.onVideoShared(video)
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
                            onFollowClick = { feedViewModel.toggleFollowCreator(video.userId) },
                            onCreatorClick = { onNavigateToCreator(video.userId) },
                            onHashtagClick = onNavigateToHashtag,
                            onSoundClick = { video.soundId?.let(onNavigateToSound) },
                            onMoreClick = { optionsVideo = video }
                        )
                    }

                    if (uiState.isLoadingMore && currentPageIndex >= videos.size - 1) {
                        CircularProgressIndicator(
                            color = BladeCrimson,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .size(24.dp)
                        )
                    }
                }
            }
        }

        // Top Floating Header: For You | Following + Search + Language Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable { feedViewModel.selectTab(FeedTab.FOR_YOU) }
                    .testTag("home_brand_logo"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.blade_logo_round),
                    contentDescription = strings.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, TextWhite.copy(alpha = 0.25f), CircleShape)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                FeedTabItem(
                    label = strings.tabFollowing,
                    selected = uiState.selectedTab == FeedTab.FOLLOWING,
                    onClick = { feedViewModel.selectTab(FeedTab.FOLLOWING) },
                    modifier = Modifier.testTag("tab_following")
                )
                FeedTabItem(
                    label = strings.tabForYou,
                    selected = uiState.selectedTab == FeedTab.FOR_YOU,
                    onClick = { feedViewModel.selectTab(FeedTab.FOR_YOU) },
                    modifier = Modifier.testTag("tab_for_you")
                )
            }

            IconButton(
                onClick = onNavigateToSearch,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .testTag("home_search_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = strings.navDiscover,
                    tint = TextWhite
                )
            }
        }
    }

    // Comments Sheet
    uiState.commentsVideoId?.let { videoId ->
        CommentsBottomSheet(
            commentsState = uiState.commentsState,
            onDismiss = { feedViewModel.closeComments() },
            onPostComment = { text, parentId -> feedViewModel.submitComment(videoId, text, parentId) },
            onCreatorClick = { uid ->
                feedViewModel.closeComments()
                onNavigateToCreator(uid)
            }
        )
    }

    // Safety Options Sheet
    optionsVideo?.let { video ->
        VideoOptionsSheet(
            video = video,
            onReport = { reason -> feedViewModel.reportVideo(video, reason) },
            onBlock = { feedViewModel.blockCreator(video.userId) },
            onDismiss = { optionsVideo = null }
        )
    }
}

@Composable
private fun FeedTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) TextWhite else TextWhite.copy(alpha = 0.65f),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 24.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BladeCrimson)
        )
    }
}

@Composable
private fun VideoFeedPage(
    video: VideoItem,
    nativeAd: AdItem?,
    isActive: Boolean,
    currentUserId: String?,
    onStartedPlayback: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onFollowClick: () -> Unit,
    onCreatorClick: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onSoundClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val strings = bladeStrings()
    val creatorName = video.creator?.username?.ifBlank { "creator" } ?: "creator"

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
                .padding(end = 10.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Creator Avatar
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCreatorClick)
                    .testTag("feed_avatar_${video.id}"),
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
                label = "likeScale"
            )

            VideoRailActionButton(
                icon = if (video.isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                tint = if (video.isLiked) BladeCrimson else TextWhite,
                label = formatCompactCount(video.likesCount),
                labelColor = if (video.isLiked) BladeCountLiked else BladeCountCrisp,
                onClick = onLikeClick,
                iconModifier = Modifier.scale(likeScale),
                modifier = Modifier.testTag("feed_like_${video.id}")
            )

            VideoRailActionButton(
                icon = Icons.Rounded.ModeComment,
                tint = TextWhite,
                label = formatCompactCount(video.commentsCount),
                labelColor = BladeCountCrisp,
                onClick = onCommentClick,
                modifier = Modifier.testTag("feed_comment_${video.id}")
            )

            VideoRailActionButton(
                icon = if (video.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                tint = if (video.isSaved) BladeGold else TextWhite,
                label = formatCompactCount(video.savesCount),
                labelColor = if (video.isSaved) BladeCountSaved else BladeCountCrisp,
                onClick = onSaveClick,
                modifier = Modifier.testTag("feed_save_${video.id}")
            )

            VideoRailActionButton(
                icon = TikTokShareIcon,
                tint = TextWhite,
                label = formatCompactCount(video.sharesCount),
                labelColor = BladeCountCrisp,
                onClick = onShareClick,
                modifier = Modifier.testTag("feed_share_${video.id}")
            )

            IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "Options", tint = TextWhite)
            }

            SpinningVinylDisc(
                isPlaying = isActive,
                onClick = onSoundClick
            )
        }

        // Bottom Left Metadata + Clickable Hashtags
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.76f)
                .padding(start = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (video.isSponsored) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BladeGold.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = video.sponsorLabel.ifBlank { strings.sponsored },
                        color = BladeGold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (nativeAd != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BladeObsidian.copy(alpha = 0.78f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BladeCyan.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AD • ${nativeAd.sponsorName}: ${nativeAd.title}",
                            color = BladeCyan,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val textShadow = remember {
                Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 4f
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(onClick = onCreatorClick)
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
                if (currentUserId != video.userId) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (video.isFollowingCreator) Color.White.copy(alpha = 0.18f) else BladeCrimson,
                        border = if (video.isFollowingCreator) {
                            androidx.compose.foundation.BorderStroke(1.dp, TextWhite.copy(alpha = 0.55f))
                        } else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onFollowClick)
                            .testTag("feed_username_follow_${video.id}")
                    ) {
                        Text(
                            text = if (video.isFollowingCreator) strings.following else strings.follow,
                            color = TextWhite,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
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
                val annotatedCaption = buildAnnotatedString {
                    val words = video.caption.split(" ")
                    words.forEachIndexed { idx, word ->
                        if (word.startsWith("#") && word.length > 1) {
                            pushStringAnnotation(tag = "HASHTAG", annotation = word)
                            pushStyle(SpanStyle(color = BladeCyan, fontWeight = FontWeight.SemiBold, shadow = textShadow))
                            append(word)
                            pop()
                            pop()
                        } else {
                            pushStyle(SpanStyle(color = TextWhite, shadow = textShadow))
                            append(word)
                            pop()
                        }
                        if (idx < words.lastIndex) append(" ")
                    }
                }
                @Suppress("DEPRECATION")
                ClickableText(
                    text = annotatedCaption,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    onClick = { offset ->
                        annotatedCaption.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset)
                            .firstOrNull()?.let { onHashtagClick(it.item) }
                    }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onSoundClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = video.soundTitle.ifBlank { "Original Sound - @$creatorName" },
                    color = TextWhite,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpinningVinylDisc(isPlaying: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "disc")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "discRotation"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .rotate(angle)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(BladeCrimson, Color(0xFF1E2230), Color.Black))
            )
            .border(2.dp, TextWhite.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = "Sound",
            tint = TextWhite,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun EmptyOrErrorFeedState(
    title: String,
    subtitle: String,
    isSupabaseConfigured: Boolean,
    onRetry: () -> Unit,
    onCreate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BladeObsidian, Color(0xFF141826), BladeObsidian))
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BladeCrimson.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSupabaseConfigured) Icons.Filled.Videocam else Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = BladeCrimson,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = title,
                color = TextWhite,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = subtitle,
                color = TextWhite.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCreate,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson)
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create Blade")
                }
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = TextWhite, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh", color = TextWhite)
                }
            }
        }
    }
}

private val TikTokShareIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TikTokShare",
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

private fun pageItemKey(videos: List<VideoItem>, page: Int): String {
    val videoId = videos[page].id
    var priorOccurrences = 0
    for (i in 0 until page) {
        if (videos[i].id == videoId) priorOccurrences++
    }
    return "${videoId}_$priorOccurrences"
}
