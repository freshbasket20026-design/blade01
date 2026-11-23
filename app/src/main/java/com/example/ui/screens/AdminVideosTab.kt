package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AdminDashboardStats
import com.example.data.model.VideoItem
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.BladeGold
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.AdminUiState
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.VideoFilter

@Composable
fun AdminVideosTab(
    stats: AdminDashboardStats,
    uiState: AdminUiState,
    adminViewModel: AdminViewModel,
    onPlayVideo: (VideoItem) -> Unit,
    onVideoDeleted: (String) -> Unit,
    onFeedMutated: () -> Unit
) {
    var showUploadDialog by remember { mutableStateOf(false) }
    var editingVideo by remember { mutableStateOf<VideoItem?>(null) }
    var deletingVideo by remember { mutableStateOf<VideoItem?>(null) }

    val filteredVideos = remember(stats.recentVideos, uiState.videoQuery, uiState.videoFilter) {
        val q = uiState.videoQuery.trim().lowercase()
        stats.recentVideos.filter { video ->
            val matchesFilter = when (uiState.videoFilter) {
                VideoFilter.ALL -> true
                VideoFilter.PUBLIC -> video.privacy.equals("public", ignoreCase = true)
                VideoFilter.FRIENDS -> video.privacy.equals("friends", ignoreCase = true)
                VideoFilter.PRIVATE -> video.privacy.equals("private", ignoreCase = true)
                VideoFilter.SPONSORED -> video.isSponsored
            }
            val matchesQuery = q.isBlank() ||
                video.title.lowercase().contains(q) ||
                video.caption.lowercase().contains(q) ||
                video.category.lowercase().contains(q) ||
                video.creator?.username.orEmpty().lowercase().contains(q) ||
                video.creator?.displayName.orEmpty().lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = {
                    adminViewModel.clearUploadError()
                    showUploadDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_upload_video_button")
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Upload / Post Video as Selected User", fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Uploaded Videos (${stats.totalVideos})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${formatCompactCount(stats.totalViews.toInt())} views • ${formatCompactCount(stats.totalLikes.toInt())} likes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = uiState.videoQuery,
                onValueChange = adminViewModel::updateVideoQuery,
                label = { Text("Search videos, captions, categories, or @uploader") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.videoQuery.isNotBlank()) {
                        IconButton(onClick = { adminViewModel.updateVideoQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_video_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VideoFilter.entries) { filter ->
                    FilterChip(
                        selected = uiState.videoFilter == filter,
                        onClick = { adminViewModel.updateVideoFilter(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    VideoFilter.ALL -> "All"
                                    VideoFilter.PUBLIC -> "Public"
                                    VideoFilter.FRIENDS -> "Friends"
                                    VideoFilter.PRIVATE -> "Private"
                                    VideoFilter.SPONSORED -> "Sponsored"
                                }
                            )
                        },
                        modifier = Modifier.testTag("admin_video_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        if (filteredVideos.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No videos match this filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredVideos, key = { it.id }) { video ->
                AdminVideoCard(
                    video = video,
                    onPlay = { onPlayVideo(video) },
                    onEdit = { editingVideo = video },
                    onDelete = { deletingVideo = video },
                    onToggleBlockUploader = {
                        video.creator?.let { creator ->
                            adminViewModel.setUserBlocked(creator.id, !creator.isBlocked, onFeedMutated)
                        }
                    }
                )
            }
        }
    }

    if (showUploadDialog) {
        AdminUploadVideoDialog(
            users = stats.recentUsers,
            isUploading = uiState.isUploadingVideo,
            uploadProgress = uiState.uploadProgress,
            uploadError = uiState.uploadError,
            onClearError = adminViewModel::clearUploadError,
            onDismiss = {
                if (!uiState.isUploadingVideo) {
                    adminViewModel.clearUploadError()
                    showUploadDialog = false
                }
            },
            onPublish = { targetUser, videoUri, remoteUrl, title, caption, category, location, privacy, resolver ->
                adminViewModel.publishVideoForUser(
                    contentResolver = resolver,
                    targetUserId = targetUser.id,
                    targetUsername = targetUser.username,
                    videoUri = videoUri,
                    remoteVideoUrl = remoteUrl,
                    title = title,
                    caption = caption,
                    category = category,
                    location = location,
                    privacy = privacy,
                    onPublished = {
                        showUploadDialog = false
                        onFeedMutated()
                    }
                )
            }
        )
    }

    editingVideo?.let { target ->
        AdminEditVideoDialog(
            video = target,
            onDismiss = { editingVideo = null },
            onSave = { title, caption, category, privacy, views, likes, shares, saves, sponsored, sponsorLabel ->
                adminViewModel.updateVideoData(
                    videoId = target.id,
                    title = title,
                    caption = caption,
                    category = category,
                    privacy = privacy,
                    viewsCount = views,
                    likesCount = likes,
                    sharesCount = shares,
                    savesCount = saves,
                    isSponsored = sponsored,
                    sponsorLabel = sponsorLabel,
                    onComplete = {
                        editingVideo = null
                        onFeedMutated()
                    }
                )
            }
        )
    }

    deletingVideo?.let { target ->
        val label = target.title.ifBlank { target.caption }.ifBlank { "this video" }
        AlertDialog(
            onDismissRequest = { deletingVideo = null },
            title = { Text("Delete Video Permanently?") },
            text = {
                Text(
                    "Remove \"$label\" uploaded by @${target.creator?.username ?: "user"}? It will be permanently deleted from the app and database."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        deletingVideo = null
                        adminViewModel.deleteVideo(id) {
                            onVideoDeleted(id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                    modifier = Modifier.testTag("admin_confirm_delete_video")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVideo = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminVideoCard(
    video: VideoItem,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleBlockUploader: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_video_card_${video.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 88.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    if (video.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = video.thumbnailUrl,
                            contentDescription = video.title.ifBlank { video.caption },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Preview video",
                        tint = TextWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = video.title.ifBlank { video.caption }.ifBlank { "(Untitled video)" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Uploader: @${video.creator?.username ?: video.userId.take(8)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = BladeRed
                        )
                        if (video.creator?.isVerified == true) VerifiedBadge(size = 13.dp)
                        if (video.creator?.isBlocked == true) {
                            Text("(BLOCKED)", style = MaterialTheme.typography.labelSmall, color = BladeGold)
                        }
                    }
                    Text(
                        text = "${video.category} • ${video.privacy}" + if (video.isSponsored) " • Sponsored" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatCompactCount(video.viewsCount)} views • ${formatCompactCount(video.likesCount)} likes • ${formatCompactCount(video.commentsCount)} comments • ${formatCompactCount(video.sharesCount)} shares",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("admin_edit_video_${video.id}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit & Stats")
                    }
                }
                if (video.creator != null) {
                    item {
                        OutlinedButton(onClick = onToggleBlockUploader) {
                            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (video.creator.isBlocked) "Unblock Uploader" else "Block Uploader")
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("admin_delete_video_${video.id}")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
