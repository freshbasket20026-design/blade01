package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminDashboardStats
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.ui.components.BladeAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.BladeEmerald
import com.example.ui.theme.BladeGold
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.AdminUiState
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.UserFilter

@Composable
fun AdminUsersTab(
    stats: AdminDashboardStats,
    uiState: AdminUiState,
    adminViewModel: AdminViewModel,
    onPlayVideo: (VideoItem) -> Unit,
    onFeedMutated: () -> Unit
) {
    var viewingUser by remember { mutableStateOf<UserProfile?>(null) }
    var editingUser by remember { mutableStateOf<UserProfile?>(null) }
    var deletingUser by remember { mutableStateOf<UserProfile?>(null) }

    val videosByUser = remember(stats.recentVideos) { stats.recentVideos.groupBy { it.userId } }

    val pendingCount = remember(stats.recentUsers) {
        stats.recentUsers.count { it.resolvedVerificationStatus == "pending" }
    }
    val rejectedCount = remember(stats.recentUsers) {
        stats.recentUsers.count { it.resolvedVerificationStatus == "rejected" }
    }

    val filteredUsers = remember(stats.recentUsers, uiState.userQuery, uiState.userFilter) {
        val q = uiState.userQuery.trim().lowercase()
        stats.recentUsers.filter { user ->
            val matchesFilter = when (uiState.userFilter) {
                UserFilter.ALL -> true
                UserFilter.PENDING -> user.resolvedVerificationStatus == "pending"
                UserFilter.VERIFIED -> user.isVerified
                UserFilter.REJECTED -> user.resolvedVerificationStatus == "rejected"
                UserFilter.BLOCKED -> user.isBlocked
                UserFilter.ADMINS -> user.role.equals("admin", ignoreCase = true)
            }
            val matchesQuery = q.isBlank() ||
                user.username.lowercase().contains(q) ||
                user.displayName.lowercase().contains(q) ||
                user.email.orEmpty().lowercase().contains(q) ||
                user.id.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registered Users (${stats.totalUsers})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Pending: $pendingCount • Verified: ${stats.verifiedUsersCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = uiState.userQuery,
                onValueChange = adminViewModel::updateUserQuery,
                label = { Text("Search name, @username, or email") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.userQuery.isNotBlank()) {
                        IconButton(onClick = { adminViewModel.updateUserQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(UserFilter.entries) { filter ->
                    FilterChip(
                        selected = uiState.userFilter == filter,
                        onClick = { adminViewModel.updateUserFilter(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    UserFilter.ALL -> "All (${stats.recentUsers.size})"
                                    UserFilter.PENDING -> "Pending ($pendingCount)"
                                    UserFilter.VERIFIED -> "Verified (${stats.verifiedUsersCount})"
                                    UserFilter.REJECTED -> "Rejected ($rejectedCount)"
                                    UserFilter.BLOCKED -> "Blocked (${stats.blockedUsersCount})"
                                    UserFilter.ADMINS -> "Admins"
                                }
                            )
                        },
                        modifier = Modifier.testTag("admin_user_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        if (filteredUsers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (uiState.userFilter == UserFilter.PENDING) {
                            "No users pending verification."
                        } else {
                            "No users match this search/filter."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredUsers, key = { it.id }) { user ->
                val uploadedCount = videosByUser[user.id]?.size ?: 0
                AdminUserCard(
                    user = user,
                    uploadedCount = uploadedCount,
                    onViewProfile = { viewingUser = user },
                    onVerify = { adminViewModel.verifyUser(user.id, onFeedMutated) },
                    onReject = { adminViewModel.rejectUser(user.id, onFeedMutated) },
                    onToggleBlock = { adminViewModel.setUserBlocked(user.id, !user.isBlocked, onFeedMutated) },
                    onEdit = { editingUser = user },
                    onDelete = { deletingUser = user }
                )
            }
        }
    }

    viewingUser?.let { target ->
        AdminViewUserDialog(
            target = target,
            userVideos = videosByUser[target.id].orEmpty(),
            onDismiss = { viewingUser = null },
            onPlayVideo = { v ->
                viewingUser = null
                onPlayVideo(v)
            }
        )
    }

    editingUser?.let { target ->
        AdminEditUserDialog(
            user = target,
            onDismiss = { editingUser = null },
            onSave = { name, handle, bio, role, coins, verified, blocked ->
                adminViewModel.updateUserAccount(
                    userId = target.id,
                    displayName = name,
                    username = handle,
                    bio = bio,
                    role = role,
                    coinsBalance = coins,
                    isVerified = verified,
                    isBlocked = blocked,
                    onComplete = {
                        editingUser = null
                        onFeedMutated()
                    }
                )
            }
        )
    }

    deletingUser?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text("Delete User Account?") },
            text = {
                Text("Delete @${target.username} and all their videos, comments, and profile data? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteUser(target.id, onFeedMutated)
                        deletingUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                    modifier = Modifier.testTag("admin_confirm_delete_user")
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUser = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminUserCard(
    user: UserProfile,
    uploadedCount: Int,
    onViewProfile: () -> Unit,
    onVerify: () -> Unit,
    onReject: () -> Unit,
    onToggleBlock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val verificationStatus = user.resolvedVerificationStatus

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_user_card_${user.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BladeAvatar(avatarUrl = user.avatarUrl, username = user.username, size = 46.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.displayName.ifBlank { "@${user.username}" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.isVerified) VerifiedBadge(size = 15.dp)

                        val (badgeText, badgeColor) = when (verificationStatus) {
                            "verified" -> "VERIFIED" to BladeEmerald
                            "rejected" -> "REJECTED" to BladeRed
                            else -> "PENDING" to BladeGold
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (user.isBlocked) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BladeRed.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "BLOCKED",
                                    color = BladeRed,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "@${user.username}" + (user.email?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$uploadedCount videos • ${formatCompactCount(user.followersCount)} followers • ${user.coinsBalance} coins • ${user.role}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (verificationStatus != "verified") {
                    item {
                        Button(
                            onClick = onVerify,
                            colors = ButtonDefaults.buttonColors(containerColor = BladeEmerald, contentColor = TextWhite),
                            modifier = Modifier.testTag("admin_verify_user_${user.id}")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Verify")
                        }
                    }
                }
                if (verificationStatus == "pending" || verificationStatus == "verified") {
                    item {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.testTag("admin_reject_user_${user.id}")
                        ) {
                            Icon(
                                imageVector = if (verificationStatus == "verified") Icons.Filled.Verified else Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = BladeRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (verificationStatus == "verified") "Unverify" else "Reject",
                                color = BladeRed
                            )
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onViewProfile,
                        modifier = Modifier.testTag("admin_view_user_${user.id}")
                    ) {
                        Icon(Icons.Filled.PersonSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Profile & Videos")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onToggleBlock,
                        modifier = Modifier.testTag("admin_block_user_${user.id}")
                    ) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = null,
                            tint = if (user.isBlocked) BladeGold else BladeRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (user.isBlocked) "Unblock" else "Block")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("admin_edit_user_${user.id}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("admin_delete_user_${user.id}")
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
