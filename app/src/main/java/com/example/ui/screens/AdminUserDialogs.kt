package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.ui.components.BladeAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite

@Composable
fun AdminViewUserDialog(
    target: UserProfile,
    userVideos: List<VideoItem>,
    onDismiss: () -> Unit,
    onPlayVideo: (VideoItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BladeAvatar(avatarUrl = target.avatarUrl, username = target.username, size = 44.dp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = target.displayName.ifBlank { "@${target.username}" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (target.isVerified) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedBadge(size = 16.dp)
                        }
                    }
                    Text(
                        text = "@${target.username} • ${target.resolvedVerificationStatus.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Email: ${target.email?.ifBlank { "Not set" } ?: "Not set"}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Role: ${target.role} • Coins: ${target.coinsBalance} • Account: ${if (target.isBlocked) "BLOCKED" else "Active"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Followers: ${formatCompactCount(target.followersCount)} • Following: ${formatCompactCount(target.followingCount)} • Likes: ${formatCompactCount(target.totalLikes)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (target.bio.isNotBlank()) {
                    Text("Bio: ${target.bio}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Uploaded Videos (${userVideos.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                if (userVideos.isEmpty()) {
                    Text(
                        "No videos uploaded by this user.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    userVideos.forEach { v ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = v.title.ifBlank { v.caption }.ifBlank { "Untitled Video" },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${v.viewsCount} views • ${v.likesCount} likes • ${v.privacy}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onPlayVideo(v) }) {
                                    Text("Play")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun AdminEditUserDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Boolean, Boolean) -> Unit
) {
    var displayName by remember(user.id) { mutableStateOf(user.displayName) }
    var username by remember(user.id) { mutableStateOf(user.username) }
    var bio by remember(user.id) { mutableStateOf(user.bio) }
    var role by remember(user.id) { mutableStateOf(user.role.ifBlank { "user" }) }
    var coinsText by remember(user.id) { mutableStateOf(user.coinsBalance.toString()) }
    var isVerified by remember(user.id) { mutableStateOf(user.isVerified) }
    var isBlocked by remember(user.id) { mutableStateOf(user.isBlocked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage @${user.username}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coinsText,
                    onValueChange = { coinsText = it.filter(Char::isDigit) },
                    label = { Text("Blade Coins Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Account Role", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("user", "creator", "admin").forEach { option ->
                        FilterChip(
                            selected = role.equals(option, ignoreCase = true),
                            onClick = { role = option },
                            label = { Text(option.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Verified Badge")
                    Switch(checked = isVerified, onCheckedChange = { isVerified = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Blocked / Suspended")
                    Switch(checked = isBlocked, onCheckedChange = { isBlocked = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        displayName,
                        username,
                        bio,
                        role,
                        coinsText.toIntOrNull() ?: user.coinsBalance,
                        isVerified,
                        isBlocked
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
