package com.example.ui.screens.create

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeSurfaceDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.CreateUiState
import com.example.ui.viewmodel.CreateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPickerModalSheet(
    uiState: CreateUiState,
    createViewModel: CreateViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewingSoundId by remember { mutableStateOf<String?>(null) }

    val previewPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(Unit) { onDispose { previewPlayer.release() } }

    // Modern Android Storage Access Framework (SAF) picker: supports MP3, M4A, AAC, WAV, OGG, FLAC without broad storage permissions
    val audioDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            previewPlayer.pause()
            previewingSoundId = null
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            createViewModel.importDeviceAudio(context, uri)
        }
    }

    fun launchSystemAudioPicker() {
        audioDocumentPicker.launch(
            arrayOf(
                "audio/*",
                "audio/mpeg",
                "audio/mp4",
                "audio/aac",
                "audio/wav",
                "audio/x-wav",
                "audio/ogg",
                "audio/flac"
            )
        )
    }

    fun playClippedPreview(audioUrl: String, id: String, startMs: Long, endMs: Long) {
        if (audioUrl.isBlank()) return
        val itemBuilder = MediaItem.Builder().setUri(Uri.parse(audioUrl))
        if (endMs > startMs) {
            itemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs.coerceAtLeast(0L))
                    .setEndPositionMs(endMs)
                    .build()
            )
        }
        previewPlayer.setMediaItem(itemBuilder.build())
        previewPlayer.prepare()
        previewPlayer.playWhenReady = true
        previewingSoundId = id
    }

    val filteredSounds = remember(uiState.availableSounds, uiState.soundSearchQuery, uiState.soundCategory) {
        val q = uiState.soundSearchQuery.trim().lowercase()
        uiState.availableSounds.filter { sound ->
            val matchesQuery = q.isBlank() || sound.title.lowercase().contains(q) || sound.artist.lowercase().contains(q)
            val matchesCat = when (uiState.soundCategory) {
                "All" -> true
                "Trending" -> sound.usageCount > 0 || uiState.availableSounds.indexOf(sound) < 5
                "Pop" -> sound.title.contains("pop", true) || sound.artist.contains("neon", true) || uiState.availableSounds.indexOf(sound) % 3 == 0
                "Hip-Hop" -> sound.title.contains("beat", true) || sound.title.contains("bass", true) || sound.title.contains("drop", true)
                "Chill" -> sound.title.contains("chill", true) || sound.title.contains("lofi", true) || sound.title.contains("night", true)
                "Desi Beats" -> sound.title.contains("dhaka", true) || sound.title.contains("desi", true) || sound.title.contains("bangla", true)
                else -> true
            }
            matchesQuery && matchesCat
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BladeSurfaceDark,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Select Audio", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextWhite)
                Button(
                    onClick = { launchSystemAudioPicker() },
                    enabled = !uiState.isImportingAudio,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeCyan, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("add_phone_audio_button")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "Add audio from phone storage", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Audio", fontWeight = FontWeight.Bold)
                }
            }

            if (uiState.isImportingAudio) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BladeCyan.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, BladeCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("audio_import_progress_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = uiState.audioImportLabel.ifBlank { "Importing audio from storage..." },
                            color = TextWhite,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { uiState.audioImportProgress.coerceIn(0f, 1f) },
                            color = BladeCyan,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            uiState.audioImportError?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BladeCrimson.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, BladeCrimson),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(err, color = TextWhite, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { createViewModel.clearAudioImportError() }) { Text("Dismiss", color = BladeCyan) }
                    }
                }
            }

            // Selected Audio Trim & Preview Card (for both imported phone songs & curated catalog tracks)
            uiState.selectedSound?.let { selected ->
                val isPlayingSelected = previewingSoundId == selected.id
                AudioTrimControlCard(
                    title = selected.title,
                    subtitle = if (uiState.isDeviceAudio) "Phone Storage" else selected.artist,
                    totalDurationMs = uiState.selectedSoundDurationMs,
                    trimStartMs = uiState.soundTrimStartMs,
                    trimEndMs = uiState.soundTrimEndMs,
                    isPlaying = isPlayingSelected,
                    onTogglePlay = {
                        if (isPlayingSelected) {
                            previewPlayer.pause()
                            previewingSoundId = null
                        } else {
                            playClippedPreview(selected.audioUrl, selected.id, uiState.soundTrimStartMs, uiState.soundTrimEndMs)
                        }
                    },
                    onTrimChange = { startMs, endMs ->
                        if (isPlayingSelected) {
                            previewPlayer.pause()
                            previewingSoundId = null
                        }
                        createViewModel.updateSoundTrimRange(startMs, endMs)
                    },
                    onReplace = { launchSystemAudioPicker() },
                    onRemove = {
                        previewPlayer.pause()
                        previewingSoundId = null
                        createViewModel.removeSelectedSound()
                    },
                    onConfirmDone = {
                        previewPlayer.pause()
                        previewingSoundId = null
                        onDismiss()
                    }
                )
            }

            OutlinedTextField(
                value = uiState.soundSearchQuery,
                onValueChange = { createViewModel.updateSoundSearch(it) },
                placeholder = { Text("Search catalog songs or tap Add Audio above...", color = TextMutedDark) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMutedDark) },
                trailingIcon = {
                    if (uiState.soundSearchQuery.isNotBlank()) {
                        IconButton(onClick = { createViewModel.updateSoundSearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextWhite)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = BladeCrimson,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("sound_search_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(createViewModel.soundCategories) { cat ->
                    FilterChip(
                        selected = uiState.soundCategory == cat,
                        onClick = { createViewModel.updateSoundCategory(cat) },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BladeCrimson, selectedLabelColor = TextWhite)
                    )
                }
            }

            if (filteredSounds.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("No matching catalog tracks. Use Add Audio for phone songs.", color = TextMutedDark)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filteredSounds, key = { it.id }) { sound ->
                        val isSelected = uiState.selectedSound?.id == sound.id
                        val isPlaying = previewingSoundId == sound.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) BladeCrimson.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, if (isSelected) BladeCrimson else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .clickable {
                                            if (isPlaying) {
                                                previewPlayer.pause()
                                                previewingSoundId = null
                                            } else {
                                                playClippedPreview(sound.audioUrl, sound.id, 0L, sound.durationSeconds * 1000L)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (sound.coverUrl.isNotBlank()) {
                                        AsyncImage(model = sound.coverUrl, contentDescription = sound.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                                    } else {
                                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextWhite)
                                    }
                                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Preview", tint = TextWhite, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sound.title, color = TextWhite, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${sound.artist} • ${sound.durationSeconds}s", color = TextMutedDark, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        previewPlayer.pause()
                                        previewingSoundId = null
                                        createViewModel.selectSound(sound)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) TextWhite else BladeCrimson,
                                        contentColor = if (isSelected) Color.Black else TextWhite
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(if (isSelected) "Trimming" else "Use", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioTrimControlCard(
    title: String,
    subtitle: String,
    totalDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onTrimChange: (Long, Long) -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onConfirmDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val maxDur = totalDurationMs.coerceAtLeast(1_000L)
    val safeStart = trimStartMs.coerceIn(0L, maxDur)
    val safeEnd = trimEndMs.coerceIn(safeStart, maxDur)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, BladeCrimson.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth().testTag("audio_trim_card")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(BladeCrimson).testTag("trim_audio_play_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause trimmed audio" else "Play trimmed audio",
                        tint = TextWhite
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, tint = BladeCyan, modifier = Modifier.size(14.dp))
                        Text(title, color = TextWhite, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = "$subtitle • Clip ${formatDurationMs(safeStart)} – ${formatDurationMs(safeEnd)} (${formatDurationMs(safeEnd - safeStart)})",
                        color = BladeCyan,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onReplace, modifier = Modifier.size(40.dp).testTag("replace_audio_button")) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Replace audio", tint = TextWhite)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp).testTag("remove_audio_button")) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove audio", tint = BladeCrimson)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(14.dp))
                Text("Cut audio portion", color = TextMutedDark, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text("Total ${formatDurationMs(maxDur)}", color = TextMutedDark, style = MaterialTheme.typography.labelSmall)
            }

            RangeSlider(
                value = safeStart.toFloat()..safeEnd.toFloat(),
                onValueChange = { range -> onTrimChange(range.start.toLong(), range.endInclusive.toLong()) },
                valueRange = 0f..maxDur.toFloat(),
                colors = SliderDefaults.colors(thumbColor = TextWhite, activeTrackColor = BladeCyan, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.testTag("audio_trim_slider")
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(15_000L to "15s", 30_000L to "30s", maxDur to "Full").forEach { (span, label) ->
                    val targetEnd = (safeStart + span).coerceAtMost(maxDur)
                    val presetStart = if (span == maxDur) 0L else safeStart
                    OutlinedButton(
                        onClick = { onTrimChange(presetStart, if (span == maxDur) maxDur else targetEnd) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.height(34.dp)
                    ) { Text(label, color = TextWhite, style = MaterialTheme.typography.labelSmall) }
                }
                Spacer(Modifier.weight(1f))
                if (onConfirmDone != null) {
                    Button(
                        onClick = onConfirmDone,
                        colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp).testTag("confirm_audio_selection")
                    ) { Text("Apply Audio", color = TextWhite, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                }
            }
        }
    }
}
