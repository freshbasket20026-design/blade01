package com.example.ui.screens.create

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.camera.StudioVoiceOverRecorder
import com.example.ui.components.VideoPlayerBox
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeGold
import com.example.ui.theme.BladeObsidian
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.CreateUiState
import com.example.ui.viewmodel.CreateViewModel
import com.example.ui.viewmodel.OverlayAnimation
import com.example.ui.viewmodel.OverlayItem
import com.example.ui.viewmodel.VideoTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class StudioTab(val label: String) {
    TIMELINE("Timeline"),
    TRANSFORM("Transform"),
    TEXT_CAPTIONS("Text & Captions"),
    AUDIO("Audio & Voice"),
    COLOR("Color & Filters"),
    LAYERS("Chroma & PiP"),
    AI_STUDIO("AI Studio")
}

@Composable
fun VideoEditorStudio(
    createViewModel: CreateViewModel,
    uiState: CreateUiState,
    onBackToCamera: () -> Unit,
    onDiscardDraft: () -> Unit,
    onPickMergeVideo: () -> Unit,
    onPickPipVideo: () -> Unit,
    onNextToPublish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeSeg = uiState.segments.getOrNull(uiState.selectedSegmentIndex)
    val activeUri = activeSeg?.let { Uri.parse(it.uriString) } ?: uiState.selectedVideoUri ?: return

    var activeTab by remember { mutableStateOf(StudioTab.TIMELINE) }
    var showSoundSheet by remember { mutableStateOf(false) }
    var showFilterLibrary by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }
    var selectedTextColor by remember { mutableLongStateOf(0xFFFFFFFFL) }
    var selectedTextPos by remember { mutableIntStateOf(1) }
    var selectedTextAnim by remember { mutableStateOf(OverlayAnimation.POP) }

    val voiceRecorder = remember(context) { StudioVoiceOverRecorder(context.applicationContext) }
    val syncedMusicPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    var isPreviewingTrimmedAudio by remember { mutableStateOf(false) }

    val studioAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            createViewModel.importDeviceAudio(context, uri)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.release()
            syncedMusicPlayer.release()
        }
    }

    // Synchronize selected & trimmed song playback with the current video project
    LaunchedEffect(
        uiState.selectedSound?.id,
        uiState.soundTrimStartMs,
        uiState.soundTrimEndMs,
        uiState.audioMix.musicVolume,
        showSoundSheet
    ) {
        val sound = uiState.selectedSound
        if (!showSoundSheet && sound != null && sound.audioUrl.isNotBlank() && uiState.audioMix.musicVolume > 0.02f) {
            val itemBuilder = MediaItem.Builder().setUri(Uri.parse(sound.audioUrl))
            if (uiState.soundTrimEndMs > uiState.soundTrimStartMs) {
                itemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(uiState.soundTrimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(uiState.soundTrimEndMs)
                        .build()
                )
            }
            syncedMusicPlayer.setMediaItem(itemBuilder.build())
            syncedMusicPlayer.volume = uiState.audioMix.musicVolume.coerceIn(0f, 1f)
            syncedMusicPlayer.prepare()
            syncedMusicPlayer.playWhenReady = true
            isPreviewingTrimmedAudio = true
        } else {
            syncedMusicPlayer.pause()
            isPreviewingTrimmedAudio = false
        }
    }

    LaunchedEffect(uiState.draftSavedNotice) {
        if (uiState.draftSavedNotice != null) {
            delay(2500)
            createViewModel.clearDraftNotice()
        }
    }

    val coverFrames by produceState<List<Pair<Long, Bitmap>>>(
        initialValue = emptyList(),
        key1 = activeUri,
        key2 = activeSeg?.sourceDurationMs ?: uiState.videoDurationMs
    ) {
        value = withContext(Dispatchers.IO) {
            extractStudioFrames(context.contentResolver, activeUri, activeSeg?.sourceDurationMs ?: uiState.videoDurationMs)
        }
    }

    val previewRatio = when (uiState.aspectRatioMode) {
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        "16:9" -> 16f / 9f
        else -> 9f / 16f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BladeObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header: Back, Sound, Undo, Redo, Save Draft, Discard
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackToCamera, modifier = Modifier.size(48.dp).testTag("edit_back_to_camera")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera", tint = TextWhite)
            }
            Surface(
                onClick = { showSoundSheet = true },
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, if (uiState.selectedSound != null) BladeCrimson else Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp).testTag("edit_sound_button")
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = BladeCyan, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(uiState.selectedSound?.title ?: "Sound", color = TextWhite, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { createViewModel.undo() }, enabled = uiState.canUndo, modifier = Modifier.size(40.dp).testTag("undo_button")) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (uiState.canUndo) TextWhite else TextMutedDark)
                }
                IconButton(onClick = { createViewModel.redo() }, enabled = uiState.canRedo, modifier = Modifier.size(40.dp).testTag("redo_button")) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (uiState.canRedo) TextWhite else TextMutedDark)
                }
                IconButton(onClick = { createViewModel.saveProjectDraft() }, modifier = Modifier.size(40.dp).testTag("save_draft_button")) {
                    Icon(Icons.Filled.Bookmark, contentDescription = "Save draft", tint = BladeCyan)
                }
                IconButton(onClick = onDiscardDraft, modifier = Modifier.size(40.dp).testTag("discard_draft_button")) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Discard project", tint = TextWhite.copy(alpha = 0.78f))
                }
            }
        }

        uiState.draftSavedNotice?.let { note ->
            Surface(shape = RoundedCornerShape(50), color = BladeCyan.copy(alpha = 0.18f), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(note, color = BladeCyan, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }

        // Video Viewport (aspect ratio framed, rotation/zoom, overlays, PiP, status chips)
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .aspectRatio(previewRatio)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            ) {
                VideoPlayerBox(
                    videoUri = activeUri.toString(),
                    isActive = true,
                    filterName = uiState.selectedFilter,
                    trimStartMs = activeSeg?.trimStartMs ?: uiState.trimStartMs,
                    trimEndMs = activeSeg?.trimEndMs ?: uiState.trimEndMs,
                    durationMs = activeSeg?.sourceDurationMs ?: uiState.videoDurationMs,
                    showSeekBar = false,
                    playbackSpeed = activeSeg?.speed ?: uiState.speedMultiplier,
                    playbackVolume = uiState.audioMix.originalVolume,
                    filterIntensity = uiState.filterIntensity,
                    extraBrightness = uiState.colorCorrection.brightness,
                    extraWarmth = uiState.colorCorrection.warmth,
                    isPhoto = false,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        rotationZ = uiState.rotationDegrees.toFloat()
                        scaleX = uiState.zoomScale
                        scaleY = uiState.zoomScale
                    }
                )

                StudioOverlayLayer(
                    overlays = uiState.overlays,
                    captions = uiState.captions,
                    playheadMs = uiState.playheadMs,
                    onCyclePosition = { createViewModel.cycleOverlayPosition(it) },
                    modifier = Modifier.fillMaxSize()
                )

                uiState.pipVideoUri?.let { pip ->
                    val align = when (uiState.pipCornerIndex % 4) {
                        0 -> Alignment.TopStart
                        1 -> Alignment.TopEnd
                        2 -> Alignment.BottomStart
                        else -> Alignment.BottomEnd
                    }
                    Surface(
                        onClick = { createViewModel.cyclePipCorner() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, BladeCyan),
                        modifier = Modifier.align(align).padding(10.dp).size(width = 68.dp, height = 98.dp)
                    ) {
                        VideoPlayerBox(videoUri = pip.toString(), isActive = false, showSeekBar = false, modifier = Modifier.fillMaxSize())
                    }
                }

                // Active Studio Status Badges
                Row(modifier = Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (uiState.speedMultiplier != 1f) StudioPill("${uiState.speedMultiplier}x")
                    if (uiState.reversePlayback) StudioPill("REV")
                    if (uiState.chromaKeyEnabled) StudioPill("CHROMA", BladeCyan)
                }
            }
        }

        // Studio Tool Tabs
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StudioTab.entries, key = { it.name }) { tab ->
                val icon = when (tab) {
                    StudioTab.TIMELINE -> Icons.Filled.ContentCut
                    StudioTab.TRANSFORM -> Icons.Filled.CropRotate
                    StudioTab.TEXT_CAPTIONS -> Icons.Filled.TextFields
                    StudioTab.AUDIO -> Icons.Filled.Mic
                    StudioTab.COLOR -> Icons.Filled.Palette
                    StudioTab.LAYERS -> Icons.Filled.Layers
                    StudioTab.AI_STUDIO -> Icons.Filled.AutoAwesome
                }
                FilterChip(
                    selected = activeTab == tab,
                    onClick = {
                        if (tab == StudioTab.AI_STUDIO) showAiSheet = true
                        activeTab = tab
                    },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(tab.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BladeCrimson, selectedLabelColor = TextWhite),
                    modifier = Modifier.testTag("studio_tab_${tab.name}")
                )
            }
        }

        // Studio Control Panel
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (activeTab) {
                    StudioTab.TIMELINE -> {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Playhead: ${formatDurationMs(uiState.playheadMs)} / ${formatDurationMs(uiState.totalTimelineDurationMs)}", color = TextWhite, style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { createViewModel.splitSegmentAtPlayhead() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.testTag("split_segment_button")) {
                                    Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Split", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = onPickMergeVideo, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.testTag("merge_segment_button")) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Merge", fontSize = 12.sp)
                                }
                            }
                        }
                        Slider(
                            value = uiState.playheadMs.toFloat(),
                            onValueChange = { createViewModel.updatePlayheadMs(it.toLong()) },
                            valueRange = 0f..uiState.totalTimelineDurationMs.toFloat().coerceAtLeast(1000f),
                            colors = SliderDefaults.colors(thumbColor = BladeCyan, activeTrackColor = BladeCyan),
                            modifier = Modifier.testTag("timeline_playhead_slider")
                        )
                        // Segment Pills
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(uiState.segments, key = { _, s -> s.id }) { idx, seg ->
                                val selected = idx == uiState.selectedSegmentIndex
                                Surface(
                                    onClick = { createViewModel.selectSegment(idx) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) BladeCrimson.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, if (selected) BladeCrimson else Color.White.copy(alpha = 0.2f))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Clip ${idx + 1} (${formatDurationMs(seg.effectiveDurationMs)})", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                                        if (uiState.segments.size > 1) {
                                            Icon(Icons.Filled.Close, contentDescription = "Delete clip", tint = TextWhite, modifier = Modifier.size(14.dp).clickable { createViewModel.deleteSegment(idx) })
                                        }
                                    }
                                }
                            }
                        }
                        if (activeSeg != null) {
                            Text("Trim Active Clip: ${formatDurationMs(activeSeg.trimStartMs)} – ${formatDurationMs(activeSeg.trimEndMs)}", color = TextMutedDark, style = MaterialTheme.typography.labelSmall)
                            RangeSlider(
                                value = activeSeg.trimStartMs.toFloat()..activeSeg.trimEndMs.toFloat(),
                                onValueChange = { r -> createViewModel.updateTrimRange(r.start.toLong(), r.endInclusive.toLong()) },
                                valueRange = 0f..activeSeg.sourceDurationMs.toFloat().coerceAtLeast(1000f),
                                colors = SliderDefaults.colors(thumbColor = TextWhite, activeTrackColor = BladeCrimson)
                            )
                            Text("Transition After Clip ${uiState.selectedSegmentIndex + 1}", color = TextMutedDark, style = MaterialTheme.typography.labelSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(VideoTransition.entries) { tr ->
                                    FilterChip(selected = activeSeg.transitionAfter == tr, onClick = { createViewModel.setSegmentTransition(uiState.selectedSegmentIndex, tr) }, label = { Text(tr.label) })
                                }
                            }
                        }
                    }
                    StudioTab.TRANSFORM -> {
                        Text("Crop / Aspect Ratio", color = TextWhite, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            createViewModel.aspectRatioOptions.forEach { ratio ->
                                FilterChip(selected = uiState.aspectRatioMode == ratio, onClick = { createViewModel.setAspectRatioMode(ratio) }, label = { Text(ratio) })
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { createViewModel.rotate90Degrees() }, modifier = Modifier.testTag("rotate_90_button")) {
                                Icon(Icons.Filled.CropRotate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rotate (${uiState.rotationDegrees}°)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Reverse", color = TextWhite, style = MaterialTheme.typography.labelMedium)
                                Switch(checked = uiState.reversePlayback, onCheckedChange = { createViewModel.toggleReversePlayback() }, colors = SwitchDefaults.colors(checkedTrackColor = BladeCrimson))
                            }
                        }
                        Text("Resize / Zoom: ${String.format("%.1fx", uiState.zoomScale)}", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                        Slider(value = uiState.zoomScale, valueRange = 0.6f..2.0f, onValueChange = { createViewModel.setZoomScale(it) })
                        Text("Speed", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            createViewModel.speedOptions.forEach { spd ->
                                val selected = uiState.speedMultiplier == spd
                                Surface(onClick = { createViewModel.setSpeedMultiplier(spd) }, shape = RoundedCornerShape(8.dp), color = if (selected) TextWhite else Color.White.copy(alpha = 0.14f), modifier = Modifier.weight(1f)) {
                                    Text("${spd}x", color = if (selected) Color.Black else TextWhite, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                    StudioTab.TEXT_CAPTIONS -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = textDraft, onValueChange = { textDraft = it },
                                placeholder = { Text("Animated title or caption...", color = TextMutedDark) }, singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = BladeCrimson),
                                modifier = Modifier.weight(1f).testTag("overlay_text_input")
                            )
                            Button(
                                onClick = { createViewModel.addOverlay(textDraft, false, selectedTextColor, selectedTextPos, selectedTextAnim); textDraft = "" },
                                colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) { Text("Add") }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            items(OverlayAnimation.entries) { anim ->
                                FilterChip(selected = selectedTextAnim == anim, onClick = { selectedTextAnim = anim }, label = { Text(anim.label) })
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(createViewModel.stickerPresets) { emoji ->
                                Surface(onClick = { createViewModel.addOverlay(emoji, true, positionIndex = 1, animation = OverlayAnimation.BOUNCE) }, shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.14f), modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
                                }
                            }
                        }
                        if (uiState.overlays.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.overlays, key = { it.id }) { item ->
                                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f)) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("${item.text} (${item.animation.label})", color = TextWhite, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { createViewModel.cycleOverlayAnimation(item.id) })
                                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = TextWhite, modifier = Modifier.size(14.dp).clickable { createViewModel.removeOverlay(item.id) })
                                        }
                                    }
                                }
                            }
                        }
                        if (uiState.captions.isNotEmpty()) {
                            Text("Synced Captions (${uiState.captions.size})", color = BladeCyan, style = MaterialTheme.typography.labelSmall)
                            uiState.captions.forEach { cue ->
                                OutlinedTextField(
                                    value = cue.text,
                                    onValueChange = { createViewModel.updateCaptionText(cue.id, it) },
                                    label = { Text("${formatDurationMs(cue.startMs)} - ${formatDurationMs(cue.endMs)}") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    StudioTab.AUDIO -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    studioAudioPicker.launch(arrayOf("audio/*"))
                                },
                                enabled = !uiState.isImportingAudio,
                                colors = ButtonDefaults.buttonColors(containerColor = BladeCyan, contentColor = Color.Black),
                                modifier = Modifier.weight(1f).testTag("studio_select_audio_button")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (uiState.selectedSound == null) "Select Audio" else "Replace Song", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { showSoundSheet = true }, modifier = Modifier.weight(1f)) {
                                Text("Sounds & Trim", color = TextWhite)
                            }
                        }
                        if (uiState.isImportingAudio) {
                            Text(
                                uiState.audioImportLabel.ifBlank { "Importing audio..." },
                                color = BladeCyan,
                                style = MaterialTheme.typography.labelSmall
                            )
                            LinearProgressIndicator(
                                progress = { uiState.audioImportProgress.coerceIn(0f, 1f) },
                                color = BladeCyan,
                                trackColor = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        uiState.audioImportError?.let { err ->
                            Text(err, color = BladeCrimson, style = MaterialTheme.typography.labelSmall)
                        }
                        uiState.selectedSound?.let { selected ->
                            AudioTrimControlCard(
                                title = selected.title,
                                subtitle = if (uiState.isDeviceAudio) "Phone Storage" else selected.artist,
                                totalDurationMs = uiState.selectedSoundDurationMs,
                                trimStartMs = uiState.soundTrimStartMs,
                                trimEndMs = uiState.soundTrimEndMs,
                                isPlaying = isPreviewingTrimmedAudio,
                                onTogglePlay = {
                                    if (isPreviewingTrimmedAudio) {
                                        syncedMusicPlayer.pause()
                                        isPreviewingTrimmedAudio = false
                                    } else {
                                        syncedMusicPlayer.seekTo(0L)
                                        syncedMusicPlayer.playWhenReady = true
                                        isPreviewingTrimmedAudio = true
                                    }
                                },
                                onTrimChange = { sMs, eMs -> createViewModel.updateSoundTrimRange(sMs, eMs) },
                                onReplace = { studioAudioPicker.launch(arrayOf("audio/*")) },
                                onRemove = {
                                    syncedMusicPlayer.pause()
                                    isPreviewingTrimmedAudio = false
                                    createViewModel.removeSelectedSound()
                                }
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (uiState.isRecordingVoiceOver) {
                                        val file = voiceRecorder.stopRecording()
                                        createViewModel.setRecordingVoiceOver(false)
                                        file?.let { f -> createViewModel.updateAudioMix { it.copy(voiceOverPath = f.absolutePath) } }
                                    } else {
                                        if (voiceRecorder.startRecording().isSuccess) createViewModel.setRecordingVoiceOver(true)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isRecordingVoiceOver) BladeGold else BladeCrimson),
                                modifier = Modifier.fillMaxWidth().testTag("voice_over_record_button")
                            ) {
                                Icon(if (uiState.isRecordingVoiceOver) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (uiState.isRecordingVoiceOver) "Stop Voiceover" else "Record Voiceover")
                            }
                        }
                        Text("Original Video Volume: ${(uiState.audioMix.originalVolume * 100).toInt()}%", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                        Slider(value = uiState.audioMix.originalVolume, onValueChange = { v -> createViewModel.updateAudioMix { it.copy(originalVolume = v) } })
                        Text("Music Track Volume: ${(uiState.audioMix.musicVolume * 100).toInt()}%", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                        Slider(value = uiState.audioMix.musicVolume, onValueChange = { v -> createViewModel.updateAudioMix { it.copy(musicVolume = v) } })
                        Text("Sound Effects", color = TextMutedDark, style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(createViewModel.sfxPresets) { sfx ->
                                FilterChip(selected = uiState.audioMix.activeSfxLabel == sfx, onClick = { createViewModel.updateAudioMix { it.copy(activeSfxLabel = if (it.activeSfxLabel == sfx) null else sfx) } }, label = { Text(sfx) })
                            }
                        }
                    }
                    StudioTab.COLOR -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { showFilterLibrary = true }, modifier = Modifier.testTag("studio_open_filters")) {
                                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Filters (${uiState.selectedFilter})")
                            }
                            TextButton(onClick = { createViewModel.resetColorCorrection() }) { Text("Reset Tone", color = BladeCyan) }
                        }
                        Text("Brightness", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                        Slider(value = uiState.colorCorrection.brightness, valueRange = -0.4f..0.4f, onValueChange = { v -> createViewModel.updateColorCorrection { it.copy(brightness = v) } })
                        Text("Contrast", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                        Slider(value = uiState.colorCorrection.contrast, valueRange = 0.6f..1.5f, onValueChange = { v -> createViewModel.updateColorCorrection { it.copy(contrast = v) } })
                        Text("Saturation", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                        Slider(value = uiState.colorCorrection.saturation, valueRange = 0f..1.8f, onValueChange = { v -> createViewModel.updateColorCorrection { it.copy(saturation = v) } })
                        Text("Warmth", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                        Slider(value = uiState.colorCorrection.warmth, valueRange = -0.35f..0.35f, onValueChange = { v -> createViewModel.updateColorCorrection { it.copy(warmth = v) } })
                    }
                    StudioTab.LAYERS -> {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Chroma Key / Green Screen", color = TextWhite, style = MaterialTheme.typography.labelMedium)
                            Switch(checked = uiState.chromaKeyEnabled, onCheckedChange = { createViewModel.setChromaKeyEnabled(it) }, colors = SwitchDefaults.colors(checkedTrackColor = BladeCyan))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0xFF10B981L to "Green", 0xFF0284C7L to "Blue", 0xFFE50914L to "Red", 0xFF111827L to "Dark").forEach { (hex, lbl) ->
                                FilterChip(selected = uiState.chromaKeyColorHex == hex, onClick = { createViewModel.setChromaKeyColor(hex) }, label = { Text(lbl) })
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onPickPipVideo, modifier = Modifier.weight(1f).testTag("pick_pip_video")) {
                                Text(if (uiState.pipVideoUri != null) "Replace Layer 2" else "+ Layer 2 Video (PiP)", color = TextWhite)
                            }
                            if (uiState.pipVideoUri != null) {
                                OutlinedButton(onClick = { createViewModel.setPipVideoUri(null) }) { Text("Clear", color = BladeCrimson) }
                            }
                        }
                        if (coverFrames.isNotEmpty()) {
                            Text("Cover Thumbnail Frame", color = TextMutedDark, style = MaterialTheme.typography.labelSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(coverFrames, key = { it.first }) { (timeMs, bmp) ->
                                    val selected = kotlin.math.abs(uiState.coverFrameMs - timeMs) < 350L
                                    Box(modifier = Modifier.size(width = 48.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)).border(if (selected) 2.dp else 1.dp, if (selected) BladeCrimson else Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).clickable { createViewModel.updateCoverFrameMs(timeMs) }) {
                                        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Cover frame", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                    }
                    StudioTab.AI_STUDIO -> {
                        Button(onClick = { showAiSheet = true }, colors = ButtonDefaults.buttonColors(containerColor = BladeCyan, contentColor = Color.Black), modifier = Modifier.fillMaxWidth().testTag("open_ai_studio_button")) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open 10 AI Effects & Auto-Captions", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBackToCamera, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), modifier = Modifier.weight(1f).height(50.dp)) {
                Text("Camera", color = TextWhite)
            }
            Button(onClick = onNextToPublish, colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson), modifier = Modifier.weight(1.4f).height(50.dp).testTag("edit_next_button")) {
                Text("Next", fontWeight = FontWeight.Bold, color = TextWhite)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextWhite, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showSoundSheet) SoundPickerModalSheet(uiState = uiState, createViewModel = createViewModel, onDismiss = { showSoundSheet = false })
    if (showFilterLibrary) FilterLibraryModalSheet(uiState = uiState, createViewModel = createViewModel, onDismiss = { showFilterLibrary = false })
    if (showAiSheet) AiEffectsModalSheet(uiState = uiState, createViewModel = createViewModel, onDismiss = { showAiSheet = false })
}

@Composable
private fun StudioPill(text: String, accent: Color = BladeGold) {
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.72f), border = BorderStroke(1.dp, accent.copy(alpha = 0.8f))) {
        Text(text, color = accent, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun StudioOverlayLayer(
    overlays: List<OverlayItem>,
    captions: List<com.example.data.ai.AiCaptionCue>,
    playheadMs: Long,
    onCyclePosition: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(14.dp)) {
        val grouped = overlays.groupBy { it.positionIndex }
        listOf(0 to Alignment.TopCenter, 1 to Alignment.Center, 2 to Alignment.BottomCenter).forEach { (pos, align) ->
            val itemsAtPos = grouped[pos].orEmpty()
            if (itemsAtPos.isNotEmpty()) {
                Column(modifier = Modifier.align(align), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsAtPos.forEach { item ->
                        if (item.isSticker) {
                            Text(item.text, fontSize = 34.sp, modifier = Modifier.clickable { onCyclePosition(item.id) })
                        } else {
                            Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.65f), modifier = Modifier.clickable { onCyclePosition(item.id) }) {
                                Text(item.text, color = Color(item.colorArgb), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
        }
        val activeCue = captions.firstOrNull { playheadMs in it.startMs..it.endMs } ?: captions.firstOrNull()
        activeCue?.let { cue ->
            Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                Text(cue.text, color = BladeGold, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

private fun extractStudioFrames(
    contentResolver: android.content.ContentResolver,
    uri: Uri,
    fallbackDurationMs: Long
): List<Pair<Long, Bitmap>> {
    val mmr = MediaMetadataRetriever()
    val frames = mutableListOf<Pair<Long, Bitmap>>()
    try {
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            mmr.setDataSource(pfd.fileDescriptor)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
                ?: fallbackDurationMs.coerceAtLeast(1000L)
            val steps = 6
            for (i in 0 until steps) {
                val timeMs = (dur * i) / steps
                val raw = mmr.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val scaled = Bitmap.createScaledBitmap(raw, 112, 168, true)
                if (scaled !== raw) raw.recycle()
                frames.add(timeMs to scaled)
            }
        }
    } catch (_: Exception) {
    } finally {
        runCatching { mmr.release() }
    }
    return frames
}
