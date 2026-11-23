package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.i18n.bladeStrings
import com.example.data.model.CommentItem
import com.example.data.model.GiftItem
import com.example.data.model.UiState
import com.example.data.model.VideoItem
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    commentsState: UiState<List<CommentItem>>,
    onDismiss: () -> Unit,
    onPostComment: (text: String, parentId: String?) -> Unit,
    onCreatorClick: (String) -> Unit
) {
    val strings = bladeStrings()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.comments,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 380.dp)
            ) {
                when (commentsState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is UiState.Error -> {
                        Text(
                            text = commentsState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is UiState.Success -> {
                        if (commentsState.data.isEmpty()) {
                            Text(
                                text = "No comments yet. Start the conversation!",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(commentsState.data, key = { it.id }) { comment ->
                                    val authorName = comment.author?.username?.ifBlank { "creator" } ?: "creator"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = if (comment.parentId != null) 28.dp else 0.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        BladeAvatar(
                                            avatarUrl = comment.author?.avatarUrl ?: "",
                                            username = authorName,
                                            size = 36.dp,
                                            modifier = Modifier.clickable { onCreatorClick(comment.userId) }
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "@$authorName",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (comment.author?.isVerified == true) {
                                                    Spacer(Modifier.width(4.dp))
                                                    VerifiedBadge(size = 13.dp)
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = comment.content,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            TextButton(
                                                onClick = { replyTarget = comment },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    text = "Reply",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            replyTarget?.let { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replying to @${target.author?.username ?: "user"}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel reply",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { replyTarget = null }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(strings.addCommentPlaceholder) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onPostComment(inputText, replyTarget?.id)
                            inputText = ""
                            replyTarget = null
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BladeCrimson)
                        .testTag("send_comment_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = strings.post,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftBottomSheet(
    video: VideoItem,
    gifts: List<GiftItem>,
    coinsBalance: Int,
    onSendGift: (GiftItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Send Gift to @${video.creator?.username ?: "creator"}",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(BladeGold.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stars,
                        contentDescription = null,
                        tint = BladeGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "$coinsBalance Coins",
                        style = MaterialTheme.typography.labelMedium,
                        color = BladeGold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val displayGifts = gifts.ifEmpty {
                listOf(
                    GiftItem("spark", "Spark", "bolt", 10),
                    GiftItem("rose", "Rose", "favorite", 25),
                    GiftItem("tiger", "Bengal Tiger", "pets", 100),
                    GiftItem("crown", "Blade Crown", "workspace_premium", 250),
                    GiftItem("rocket", "Prism Rocket", "rocket_launch", 500)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 260.dp)
            ) {
                items(displayGifts) { gift ->
                    Surface(
                        onClick = { onSendGift(gift) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.testTag("gift_${gift.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val icon = when (gift.iconName) {
                                "favorite" -> Icons.Filled.Favorite
                                "pets" -> Icons.Filled.Pets
                                "workspace_premium" -> Icons.Filled.WorkspacePremium
                                "rocket_launch" -> Icons.Filled.RocketLaunch
                                else -> Icons.Filled.Bolt
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = gift.name,
                                tint = BladeGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = gift.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "${gift.coinCost} coins",
                                style = MaterialTheme.typography.labelSmall,
                                color = BladeGold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(
    video: VideoItem,
    onReport: (String) -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = bladeStrings()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Safety & Options", style = MaterialTheme.typography.titleMedium)
            listOf(
                "Spam or misleading",
                "Harassment or hate speech",
                "Inappropriate content"
            ).forEach { reason ->
                Surface(
                    onClick = {
                        onReport(reason)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text("${strings.reportVideo}: $reason", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Surface(
                onClick = {
                    onBlock()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${strings.blockUser} @${video.creator?.username ?: "creator"}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
