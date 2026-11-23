package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.MessageItem
import com.example.ui.audio.VoiceAudioUiState
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeObsidian
import com.example.ui.theme.BladeTickRead
import com.example.ui.theme.TextWhite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ChatMessageBubble(
    message: MessageItem,
    isMine: Boolean,
    audioState: VoiceAudioUiState,
    onToggleVoicePlayback: (MessageItem) -> Unit,
    onOpenImagePreview: (String) -> Unit,
    onRetryFailed: (MessageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = message.resolvedStatus
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )
    val bubbleColor = when {
        status == "failed" && isMine -> MaterialTheme.colorScheme.errorContainer
        isMine -> BladeCrimson
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        status == "failed" && isMine -> MaterialTheme.colorScheme.onErrorContainer
        isMine -> TextWhite
        else -> MaterialTheme.colorScheme.onSurface
    }
    val metaColor = contentColor.copy(alpha = 0.78f)
    val formattedTime = remember(message.createdAt) { formatMessageTimestamp(message.createdAt) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_message_${message.id}"),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            tonalElevation = if (isMine) 0.dp else 1.dp,
            modifier = Modifier
                .widthIn(min = 84.dp, max = 290.dp)
                .then(
                    if (status == "failed" && isMine) {
                        Modifier.clickable { onRetryFailed(message) }
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (message.isImageMessage) 6.dp else 12.dp,
                    vertical = if (message.isImageMessage) 6.dp else 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    message.isImageMessage -> {
                        val imageModel = message.localPreviewUri?.takeIf { it.isNotBlank() } ?: message.mediaUrl
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f))
                                .clickable(enabled = status != "sending") {
                                    onOpenImagePreview(imageModel)
                                }
                                .testTag("chat_image_${message.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "Shared photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (status == "sending") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(BladeObsidian.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = TextWhite,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        if (message.content.isNotBlank()) {
                            Text(
                                text = message.content,
                                color = contentColor,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    message.isVoiceMessage -> {
                        val isThisPlaying = audioState.activePlaybackId == message.id && audioState.isPlaying
                        val isThisBuffering = audioState.activePlaybackId == message.id && audioState.isBuffering
                        val isThisActive = audioState.activePlaybackId == message.id
                        val totalDuration = if (isThisActive && audioState.playbackDurationMs > 0L) {
                            audioState.playbackDurationMs
                        } else {
                            message.mediaDurationMs.coerceAtLeast(1000L)
                        }
                        val currentPos = if (isThisActive) audioState.playbackPositionMs else 0L
                        val progress = if (totalDuration > 0L) {
                            (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .testTag("chat_voice_${message.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = { onToggleVoicePlayback(message) },
                                shape = CircleShape,
                                color = if (isMine) TextWhite.copy(alpha = 0.22f) else BladeCrimson.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("chat_voice_play_${message.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    when {
                                        isThisBuffering || status == "sending" -> {
                                            CircularProgressIndicator(
                                                color = if (isMine) TextWhite else BladeCrimson,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        isThisPlaying -> {
                                            Icon(
                                                imageVector = Icons.Rounded.Pause,
                                                contentDescription = "Pause voice message",
                                                tint = if (isMine) TextWhite else BladeCrimson
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Play voice message",
                                                tint = if (isMine) TextWhite else BladeCrimson
                                            )
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = if (isMine) TextWhite else BladeCrimson,
                                    trackColor = contentColor.copy(alpha = 0.25f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isThisActive && (isThisPlaying || currentPos > 0L)) {
                                            formatAudioDuration(currentPos)
                                        } else {
                                            formatAudioDuration(totalDuration)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = metaColor
                                    )
                                    Text(
                                        text = "Voice note",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = metaColor
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = message.content,
                            color = contentColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Meta row: timestamp + failed retry / WhatsApp-style single & double ticks
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = if (message.isImageMessage) 6.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (status == "failed" && isMine) {
                        Text(
                            text = "Failed • Tap to retry",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (formattedTime.isNotBlank()) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = metaColor
                        )
                    }

                    if (isMine) {
                        MessageStatusTicks(
                            status = status,
                            defaultTint = metaColor,
                            modifier = Modifier.testTag("chat_tick_${message.id}_$status")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusTicks(
    status: String,
    defaultTint: Color,
    modifier: Modifier = Modifier
) {
    when (status) {
        "sending" -> {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = "Sending message",
                tint = defaultTint,
                modifier = modifier.size(14.dp)
            )
        }
        "failed" -> {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = "Failed to send",
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier.size(14.dp)
            )
        }
        "read" -> {
            Icon(
                imageVector = Icons.Rounded.DoneAll,
                contentDescription = "Read by recipient",
                tint = BladeTickRead,
                modifier = modifier.size(16.dp)
            )
        }
        "delivered" -> {
            Icon(
                imageVector = Icons.Rounded.DoneAll,
                contentDescription = "Delivered to recipient",
                tint = defaultTint,
                modifier = modifier.size(16.dp)
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Rounded.Done,
                contentDescription = "Sent",
                tint = defaultTint,
                modifier = modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun ChatImageViewerDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BladeObsidian)
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full photo view",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp, end = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BladeObsidian.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close photo viewer",
                    tint = TextWhite
                )
            }
        }
    }
}

internal fun formatAudioDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

internal fun formatMessageTimestamp(isoString: String): String {
    if (isoString.isBlank()) return ""
    return runCatching {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    }.getOrDefault("")
}
