package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminCommentEntry
import com.example.data.model.AdminDashboardStats
import com.example.data.model.ReportItem
import com.example.data.model.VideoItem
import com.example.ui.theme.BladeEmerald
import com.example.ui.theme.BladeGold
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.AdminUiState
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.ReportFilter

@Composable
fun AdminModerationTab(
    stats: AdminDashboardStats,
    uiState: AdminUiState,
    adminViewModel: AdminViewModel,
    onPlayVideo: (VideoItem) -> Unit,
    onVideoDeleted: (String) -> Unit,
    onFeedMutated: () -> Unit
) {
    var pendingDeleteComment by remember { mutableStateOf<AdminCommentEntry?>(null) }
    var pendingDeleteVideoId by remember { mutableStateOf<String?>(null) }

    val userMap = remember(stats.recentUsers) { stats.recentUsers.associateBy { it.id } }
    val videoMap = remember(stats.recentVideos) { stats.recentVideos.associateBy { it.id } }

    val filteredReports = remember(stats.pendingReports, uiState.reportFilter) {
        stats.pendingReports.filter { report ->
            when (uiState.reportFilter) {
                ReportFilter.ALL -> true
                ReportFilter.PENDING -> report.status.equals("pending", ignoreCase = true)
                ReportFilter.REVIEWED -> report.status.equals("reviewed", ignoreCase = true)
                ReportFilter.DISMISSED -> report.status.equals("dismissed", ignoreCase = true)
            }
        }
    }

    val filteredComments = remember(stats.recentComments, uiState.commentQuery) {
        val q = uiState.commentQuery.trim().lowercase()
        if (q.isBlank()) stats.recentComments
        else stats.recentComments.filter { entry ->
            val authorName = entry.comment.author?.username.orEmpty()
            val authorDisplay = entry.comment.author?.displayName.orEmpty()
            val videoTitle = entry.video?.title.orEmpty() + " " + entry.video?.caption.orEmpty()
            entry.comment.content.lowercase().contains(q) ||
                authorName.lowercase().contains(q) ||
                authorDisplay.lowercase().contains(q) ||
                videoTitle.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Reports Queue (${filteredReports.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportFilter.entries) { filter ->
                    FilterChip(
                        selected = uiState.reportFilter == filter,
                        onClick = { adminViewModel.updateReportFilter(filter) },
                        label = {
                            val label = when (filter) {
                                ReportFilter.ALL -> "All (${stats.pendingReports.size})"
                                ReportFilter.PENDING -> "Pending (${stats.pendingReportsCount})"
                                ReportFilter.REVIEWED -> "Reviewed"
                                ReportFilter.DISMISSED -> "Dismissed"
                            }
                            Text(label)
                        },
                        modifier = Modifier.testTag("admin_report_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        if (filteredReports.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No reports match this filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredReports, key = { "report_${it.id}" }) { report ->
                val targetVideo = report.videoId?.let { videoMap[it] }
                val reportedUser = report.reportedUserId?.let { userMap[it] } ?: targetVideo?.creator
                val reporter = userMap[report.reporterId]
                ReportModerationCard(
                    report = report,
                    targetVideo = targetVideo,
                    reportedUsername = reportedUser?.username ?: report.reportedUserId?.take(8) ?: "unknown",
                    isReportedUserBlocked = reportedUser?.isBlocked == true,
                    reporterUsername = reporter?.username ?: report.reporterId.take(8),
                    onReview = { adminViewModel.resolveReport(report.id, "reviewed") },
                    onDismiss = { adminViewModel.resolveReport(report.id, "dismissed") },
                    onPlayVideo = { targetVideo?.let(onPlayVideo) },
                    onDeleteVideo = { report.videoId?.let { pendingDeleteVideoId = it } },
                    onToggleBlockUser = {
                        reportedUser?.let {
                            adminViewModel.setUserBlocked(it.id, !it.isBlocked, onFeedMutated)
                        }
                    },
                    onDeleteReport = { adminViewModel.deleteReport(report.id) }
                )
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Forum, contentDescription = null, tint = BladeRed)
                Text(
                    text = "Comments Moderation (${filteredComments.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.commentQuery,
                onValueChange = adminViewModel::updateCommentQuery,
                label = { Text("Search comments, authors, or videos") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.commentQuery.isNotBlank()) {
                        IconButton(onClick = { adminViewModel.updateCommentQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_comment_search_input")
            )
        }

        if (filteredComments.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No comments found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredComments, key = { "comment_${it.comment.id}" }) { entry ->
                val author = entry.comment.author ?: userMap[entry.comment.userId]
                val uploader = entry.video?.creator ?: entry.video?.userId?.let { userMap[it] }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_comment_card_${entry.comment.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "@${author?.username ?: entry.comment.userId.take(8)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = buildString {
                                        append("On video: ")
                                        append(
                                            entry.video?.let { v ->
                                                v.title.ifBlank { v.caption }.ifBlank { "Untitled" }
                                            } ?: entry.comment.videoId.take(8)
                                        )
                                        if (uploader != null) {
                                            append(" (Uploader @${uploader.username})")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row {
                                if (author != null) {
                                    IconButton(
                                        onClick = {
                                            adminViewModel.setUserBlocked(author.id, !author.isBlocked, onFeedMutated)
                                        },
                                        modifier = Modifier.testTag("admin_block_commenter_${author.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Block,
                                            contentDescription = if (author.isBlocked) "Unblock commenter" else "Block commenter",
                                            tint = if (author.isBlocked) BladeRed else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { pendingDeleteComment = entry },
                                    modifier = Modifier.testTag("admin_delete_comment_${entry.comment.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Remove Comment",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Text(
                            text = entry.comment.content,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    pendingDeleteComment?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteComment = null },
            title = { Text("Remove Comment?") },
            text = {
                Text("Delete this comment by @${entry.comment.author?.username ?: "user"}? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteComment(
                            commentId = entry.comment.id,
                            videoId = entry.comment.videoId,
                            currentVideoCommentsCount = entry.video?.commentsCount ?: 1,
                            onSynced = onFeedMutated
                        )
                        pendingDeleteComment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                    modifier = Modifier.testTag("admin_confirm_delete_comment")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteComment = null }) { Text("Cancel") }
            }
        )
    }

    pendingDeleteVideoId?.let { videoId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteVideoId = null },
            title = { Text("Delete Reported Video?") },
            text = { Text("Permanently remove this reported video from the platform?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteVideoId = null
                        adminViewModel.deleteVideo(videoId) {
                            onVideoDeleted(videoId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                    modifier = Modifier.testTag("admin_confirm_delete_reported_video")
                ) {
                    Text("Delete Video")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteVideoId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReportModerationCard(
    report: ReportItem,
    targetVideo: VideoItem?,
    reportedUsername: String,
    isReportedUserBlocked: Boolean,
    reporterUsername: String,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    onPlayVideo: () -> Unit,
    onDeleteVideo: () -> Unit,
    onToggleBlockUser: () -> Unit,
    onDeleteReport: () -> Unit
) {
    val statusColor = when (report.status.lowercase()) {
        "pending" -> BladeGold
        "reviewed" -> BladeEmerald
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_report_card_${report.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                    Text(
                        text = report.reason,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = report.status.uppercase(),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Target: @$reportedUsername • Reported by @$reporterUsername",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (targetVideo != null) {
                Text(
                    text = "Video: ${targetVideo.title.ifBlank { targetVideo.caption }.ifBlank { "Untitled" }}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Button(
                        onClick = onReview,
                        colors = ButtonDefaults.buttonColors(containerColor = BladeEmerald, contentColor = TextWhite),
                        modifier = Modifier.testTag("admin_review_report_${report.id}")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reviewed")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("admin_dismiss_report_${report.id}")
                    ) {
                        Text("Dismiss")
                    }
                }
                if (targetVideo != null) {
                    item {
                        OutlinedButton(onClick = onPlayVideo) {
                            Text("Watch Video")
                        }
                    }
                    item {
                        OutlinedButton(onClick = onDeleteVideo) {
                            Text("Delete Video", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (report.reportedUserId != null) {
                    item {
                        OutlinedButton(onClick = onToggleBlockUser) {
                            Text(if (isReportedUserBlocked) "Unblock User" else "Block User", color = BladeRed)
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = onDeleteReport) {
                        Text("Delete Log")
                    }
                }
            }
        }
    }
}
