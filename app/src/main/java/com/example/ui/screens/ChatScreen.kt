package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.i18n.bladeStrings
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.audio.VoiceNoteController
import com.example.ui.components.BladeAvatar
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeEmerald
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.InboxViewModel

@Composable
fun ChatScreen(
    conversationId: String,
    inboxViewModel: InboxViewModel,
    currentUser: UserProfile?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val context = LocalContext.current
    val density = LocalDensity.current
    val uiState by inboxViewModel.uiState.collectAsStateWithLifecycle()

    val voiceController = remember(context) { VoiceNoteController(context.applicationContext) }
    val audioState by voiceController.state.collectAsStateWithLifecycle()

    var messageInput by remember { mutableStateOf("") }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            inboxViewModel.stageImage(uri)
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionMessage = null
            voiceController.startRecording()
        } else {
            permissionMessage = "Microphone permission is needed to record voice messages."
        }
    }

    fun requestOrStartVoiceRecording() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceController.startRecording()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(conversationId, currentUser?.id) {
        inboxViewModel.startChatPolling(conversationId, currentUser?.id)
        onDispose {
            inboxViewModel.stopChatPolling()
            voiceController.release()
        }
    }

    val messageList = (uiState.messagesState as? UiState.Success)?.data.orEmpty()

    // Keep latest messages smoothly visible both when new messages arrive and when keyboard opens
    LaunchedEffect(messageList.size, imeBottomPx) {
        if (messageList.isNotEmpty()) {
            val lastIdx = messageList.lastIndex
            if (imeBottomPx > 0 || listState.firstVisibleItemIndex >= (lastIdx - 5).coerceAtLeast(0)) {
                listState.animateScrollToItem(lastIdx)
            } else {
                listState.scrollToItem(lastIdx)
            }
        }
    }

    fun submitCurrentComposer() {
        val uid = currentUser?.id ?: ""
        val stagedUri = uiState.stagedImageUri
        if (stagedUri != null) {
            val caption = messageInput
            messageInput = ""
            inboxViewModel.sendImageMessage(
                contentResolver = context.contentResolver,
                conversationId = conversationId,
                senderId = uid,
                imageUri = stagedUri,
                caption = caption
            )
            return
        }
        val recorded = voiceController.consumeRecordedFileForSend()
        if (recorded != null) {
            val (file, dur) = recorded
            inboxViewModel.sendVoiceMessage(
                contentResolver = context.contentResolver,
                conversationId = conversationId,
                senderId = uid,
                voiceFile = file,
                durationMs = dur
            )
            return
        }
        if (messageInput.isNotBlank()) {
            val text = messageInput
            messageInput = ""
            inboxViewModel.sendMessage(
                conversationId = conversationId,
                text = text,
                senderId = uid
            )
        }
    }

    val otherUser = uiState.activeConversation?.otherUser

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Fixed Header (remains locked at top regardless of keyboard)
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                BladeAvatar(
                    avatarUrl = otherUser?.avatarUrl ?: "",
                    username = otherUser?.username ?: "Chat",
                    size = 40.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = otherUser?.displayName?.ifBlank { "@${otherUser.username}" } ?: "Direct Message",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isRealtimeConnected) BladeEmerald
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                )
                        )
                        Text(
                            text = buildString {
                                if (otherUser != null) append("@${otherUser.username} • ")
                                append(if (uiState.isRealtimeConnected) "Live" else "Syncing")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Scrollable Message History (resizes smoothly above keyboard & input bar)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val msgs = uiState.messagesState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(
                        color = BladeCrimson,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is UiState.Error -> {
                    Text(
                        text = msgs.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
                is UiState.Success -> {
                    if (msgs.data.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Say hello!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Send a message, photo, or voice note.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("chat_messages_list")
                        ) {
                            items(msgs.data, key = { it.clientId.ifBlank { it.id } }) { msg ->
                                val isMine = msg.senderId == currentUser?.id
                                val audioSource = msg.mediaUrl.ifBlank { msg.localPreviewUri.orEmpty() }
                                ChatMessageBubble(
                                    message = msg,
                                    isMine = isMine,
                                    audioState = audioState,
                                    onToggleVoicePlayback = { item ->
                                        voiceController.togglePlayback(
                                            messageId = item.id,
                                            source = audioSource,
                                            knownDurationMs = item.mediaDurationMs
                                        )
                                    },
                                    onOpenImagePreview = { url -> fullScreenImageUrl = url },
                                    onRetryFailed = { failed ->
                                        inboxViewModel.retryFailedMessage(context.contentResolver, failed)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Fixed Composer & Previews (seamlessly tracks keyboard with zero jump/gap)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(bottomInsets)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                val bannerError = permissionMessage ?: audioState.audioError ?: uiState.error
                AnimatedVisibility(visible = !bannerError.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bannerError.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                permissionMessage = null
                                voiceController.clearError()
                                inboxViewModel.clearError()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Photo Attachment Preview Strip before sending
                AnimatedVisibility(visible = uiState.stagedImageUri != null) {
                    uiState.stagedImageUri?.let { uri ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("chat_staged_image_preview")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected photo preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Photo ready to send",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Add a caption below or tap Send",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { inboxViewModel.stageImage(null) },
                                    modifier = Modifier.testTag("chat_cancel_staged_image")
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove selected photo")
                                }
                                Button(
                                    onClick = { submitCurrentComposer() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BladeCrimson,
                                        contentColor = TextWhite
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("chat_send_staged_image")
                                ) {
                                    Text("Send")
                                }
                            }
                        }
                    }
                }

                // Voice Recording or Recorded Voice Preview Bar
                when {
                    audioState.isRecording -> {
                        VoiceRecordingActiveBar(
                            durationMs = audioState.recordingDurationMs,
                            onCancel = { voiceController.cancelRecording() },
                            onStop = { voiceController.stopRecording() },
                            onSendNow = { submitCurrentComposer() }
                        )
                    }

                    audioState.recordedFile != null -> {
                        val file = audioState.recordedFile!!
                        val isPlayingPreview = audioState.activePlaybackId == "preview_draft" && audioState.isPlaying
                        val previewPos = if (audioState.activePlaybackId == "preview_draft") audioState.playbackPositionMs else 0L
                        val previewDur = audioState.recordedDurationMs.coerceAtLeast(1000L)
                        val previewProgress = (previewPos.toFloat() / previewDur.toFloat()).coerceIn(0f, 1f)

                        VoiceRecordedPreviewBar(
                            durationMs = previewDur,
                            isPlaying = isPlayingPreview,
                            progress = previewProgress,
                            onTogglePlay = {
                                voiceController.togglePlayback(
                                    messageId = "preview_draft",
                                    source = file.absolutePath,
                                    knownDurationMs = previewDur
                                )
                            },
                            onDiscard = { voiceController.clearRecordedPreview() },
                            onSend = { submitCurrentComposer() }
                        )
                    }

                    else -> {
                        // Standard Composer Row: Photo Picker + Input Pill + Mic + Send
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_attach_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = "Attach photo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = { messageInput = it },
                                placeholder = {
                                    Text(
                                        text = if (uiState.stagedImageUri != null) "Add a caption..." else strings.typeMessageHint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BladeCrimson,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submitCurrentComposer() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_message_input")
                            )

                            // Microphone button (tap to start recording OR press-and-hold)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { requestOrStartVoiceRecording() },
                                            onLongPress = { requestOrStartVoiceRecording() }
                                        )
                                    }
                                    .testTag("chat_voice_record_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Record voice message",
                                    tint = BladeCrimson
                                )
                            }

                            val canSend = messageInput.isNotBlank() || uiState.stagedImageUri != null
                            IconButton(
                                onClick = { submitCurrentComposer() },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSend) BladeCrimson
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (canSend) TextWhite else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fullScreenImageUrl?.let { url ->
        ChatImageViewerDialog(
            imageUrl = url,
            onDismiss = { fullScreenImageUrl = null }
        )
    }
}

@Composable
private fun VoiceRecordingActiveBar(
    durationMs: Long,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onSendNow: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "rec_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("chat_voice_recording_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(48.dp)
                .testTag("chat_voice_cancel_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Cancel recording",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(BladeRed.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FiberManualRecord,
                contentDescription = "Recording active",
                tint = BladeRed,
                modifier = Modifier
                    .size(14.dp)
                    .alpha(pulseAlpha)
            )
            Text(
                text = "Recording ${formatAudioDuration(durationMs)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = BladeRed
            )
        }

        IconButton(
            onClick = onStop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("chat_voice_stop_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = "Stop recording to preview",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(
            onClick = onSendNow,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(BladeCrimson)
                .testTag("chat_voice_send_now_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send voice message",
                tint = TextWhite
            )
        }
    }
}

@Composable
private fun VoiceRecordedPreviewBar(
    durationMs: Long,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onDiscard: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("chat_voice_preview_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onDiscard,
            modifier = Modifier
                .size(48.dp)
                .testTag("chat_voice_discard_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Discard voice message",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(BladeCrimson)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                    tint = TextWhite
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                color = BladeCrimson,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Text(
                text = formatAudioDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(BladeCrimson)
                .testTag("chat_voice_send_preview_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send recorded voice message",
                tint = TextWhite
            )
        }
    }
}
