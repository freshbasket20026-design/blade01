package com.example.ui.screens

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.ui.components.BladeAvatar
import com.example.ui.theme.BladeEmerald
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun AdminUploadVideoDialog(
    users: List<UserProfile>,
    isUploading: Boolean,
    uploadProgress: Float,
    uploadError: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onPublish: (
        targetUser: UserProfile,
        videoUri: Uri?,
        remoteUrl: String,
        title: String,
        caption: String,
        category: String,
        location: String,
        privacy: String,
        resolver: ContentResolver
    ) -> Unit
) {
    val context = LocalContext.current
    var userSearch by remember { mutableStateOf("") }
    var selectedUser by remember(users) { mutableStateOf(users.firstOrNull()) }
    var pickedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var autoThumbPreview by remember { mutableStateOf<Bitmap?>(null) }
    var detectedDurationMs by remember { mutableLongStateOf(0L) }
    var remoteVideoUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Entertainment") }
    var location by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("public") }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedVideoUri = uri
            onClearError()
        }
    }

    LaunchedEffect(pickedVideoUri) {
        val uri = pickedVideoUri
        if (uri == null) {
            autoThumbPreview = null
            detectedDurationMs = 0L
            return@LaunchedEffect
        }
        val (previewBmp, dur) = loadVideoPreviewMetadata(context.contentResolver, uri)
        autoThumbPreview = previewBmp
        detectedDurationMs = dur
    }

    val matchingUsers = remember(users, userSearch) {
        val q = userSearch.trim().lowercase()
        if (q.isBlank()) users else users.filter {
            it.username.lowercase().contains(q) || it.displayName.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Video as Selected User") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uploadError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_upload_error_banner")
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Text(
                    text = "1. Select Post Owner (Account Profile)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedTextField(
                    value = userSearch,
                    onValueChange = { userSearch = it },
                    label = { Text("Filter registered users") },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                )
                if (matchingUsers.isEmpty()) {
                    Text(
                        text = "No registered users available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchingUsers.take(30).forEach { user ->
                            val isSelected = selectedUser?.id == user.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) BladeRed.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isUploading) { selectedUser = user }
                                    .testTag("admin_pick_owner_${user.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BladeAvatar(avatarUrl = user.avatarUrl, username = user.username, size = 30.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${user.displayName.ifBlank { user.username }} (@${user.username})",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected owner", tint = BladeRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "2. Video Source (Thumbnail Auto-Generated)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedButton(
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_pick_video_file_button")
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (pickedVideoUri != null) "Change Selected Video" else "Select Video from Device")
                }

                if (pickedVideoUri != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 74.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = autoThumbPreview
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Auto-generated thumbnail preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                } else {
                                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = BladeRed)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Video Ready",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = BladeEmerald
                                )
                                Text(
                                    text = if (detectedDurationMs > 0L) {
                                        "Duration: ${formatDurationLabel(detectedDurationMs)} • Thumbnail auto-generated"
                                    } else {
                                        "Thumbnail will be auto-generated from video"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = remoteVideoUrl,
                    onValueChange = {
                        remoteVideoUrl = it
                        onClearError()
                    },
                    label = { Text("Or Video URL (https://...)") },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Video Title") },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_upload_title_input")
                )

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption & #hashtags") },
                    maxLines = 3,
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_upload_caption_input")
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional)") },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Entertainment", "Comedy", "Music", "Dance", "Cricket", "Tech", "Gaming", "Food", "Travel")) { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Text("Privacy", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("public", "friends", "private").forEach { option ->
                        FilterChip(
                            selected = privacy == option,
                            onClick = { privacy = option },
                            label = { Text(option.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                if (isUploading) {
                    LinearProgressIndicator(
                        progress = { uploadProgress.coerceIn(0f, 1f) },
                        color = BladeRed,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = when {
                            uploadProgress < 0.85f -> "Uploading video (${(uploadProgress * 100).toInt()}%)..."
                            uploadProgress < 0.94f -> "Generating & saving thumbnail..."
                            else -> "Publishing post to app feed..."
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedUser?.let { owner ->
                        onPublish(
                            owner,
                            pickedVideoUri,
                            remoteVideoUrl,
                            title,
                            caption,
                            category,
                            location,
                            privacy,
                            context.contentResolver
                        )
                    }
                },
                enabled = !isUploading && selectedUser != null && (pickedVideoUri != null || remoteVideoUrl.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                modifier = Modifier.testTag("admin_publish_video_confirm_button")
            ) {
                Text(if (isUploading) "Publishing..." else "Publish")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) { Text("Cancel") }
        }
    )
}

@Composable
fun AdminEditVideoDialog(
    video: VideoItem,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        caption: String,
        category: String,
        privacy: String,
        views: Int,
        likes: Int,
        shares: Int,
        saves: Int,
        sponsored: Boolean,
        sponsorLabel: String
    ) -> Unit
) {
    var title by remember(video.id) { mutableStateOf(video.title) }
    var caption by remember(video.id) { mutableStateOf(video.caption) }
    var category by remember(video.id) { mutableStateOf(video.category) }
    var privacy by remember(video.id) { mutableStateOf(video.privacy.ifBlank { "public" }) }
    var viewsText by remember(video.id) { mutableStateOf(video.viewsCount.toString()) }
    var likesText by remember(video.id) { mutableStateOf(video.likesCount.toString()) }
    var sharesText by remember(video.id) { mutableStateOf(video.sharesCount.toString()) }
    var savesText by remember(video.id) { mutableStateOf(video.savesCount.toString()) }
    var isSponsored by remember(video.id) { mutableStateOf(video.isSponsored) }
    var sponsorLabel by remember(video.id) { mutableStateOf(video.sponsorLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Video & Metrics") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("public", "friends", "private").forEach { opt ->
                        FilterChip(
                            selected = privacy.equals(opt, ignoreCase = true),
                            onClick = { privacy = opt },
                            label = { Text(opt.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = viewsText,
                        onValueChange = { viewsText = it.filter(Char::isDigit) },
                        label = { Text("Views") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = likesText,
                        onValueChange = { likesText = it.filter(Char::isDigit) },
                        label = { Text("Likes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sharesText,
                        onValueChange = { sharesText = it.filter(Char::isDigit) },
                        label = { Text("Shares") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = savesText,
                        onValueChange = { savesText = it.filter(Char::isDigit) },
                        label = { Text("Saves") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sponsored Promotion")
                    Switch(checked = isSponsored, onCheckedChange = { isSponsored = it })
                }
                if (isSponsored) {
                    OutlinedTextField(
                        value = sponsorLabel,
                        onValueChange = { sponsorLabel = it },
                        label = { Text("Sponsor Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title,
                        caption,
                        category,
                        privacy,
                        viewsText.toIntOrNull() ?: video.viewsCount,
                        likesText.toIntOrNull() ?: video.likesCount,
                        sharesText.toIntOrNull() ?: video.sharesCount,
                        savesText.toIntOrNull() ?: video.savesCount,
                        isSponsored,
                        sponsorLabel
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

private suspend fun loadVideoPreviewMetadata(
    contentResolver: ContentResolver,
    videoUri: Uri
): Pair<Bitmap?, Long> = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    var tempFile: File? = null
    try {
        var ready = runCatching {
            contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                true
            } ?: false
        }.getOrDefault(false)
        if (!ready) {
            val copied = File.createTempFile("blade_prev_", ".mp4")
            tempFile = copied
            contentResolver.openInputStream(videoUri)?.use { input ->
                copied.outputStream().use { out -> input.copyTo(out) }
            }
            if (copied.length() > 0L) {
                retriever.setDataSource(copied.absolutePath)
                ready = true
            }
        }
        if (!ready) return@withContext null to 0L
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
        frame to duration
    } catch (_: Exception) {
        null to 0L
    } finally {
        runCatching { retriever.release() }
        runCatching { tempFile?.delete() }
    }
}

private fun formatDurationLabel(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
