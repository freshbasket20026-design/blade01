package com.example.ui.screens.create

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.camera.ArEffectType
import com.example.ui.components.filterOverlayColor
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeGold
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.CameraToolPanel
import com.example.ui.viewmodel.CaptureMode
import com.example.ui.viewmodel.CreateUiState
import com.example.ui.viewmodel.CreateViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@Composable
fun CameraCaptureStep(
    createViewModel: CreateViewModel,
    uiState: CreateUiState,
    onPickVideoGallery: () -> Unit,
    onPickGreenScreenVideo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cameraController = rememberBladeCameraController()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var showSoundSheet by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showFilterLibrarySheet by remember { mutableStateOf(false) }
    var showBeautyStudioSheet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            statusMessage = "Camera permission is required to record. You can still upload a video."
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(3200)
            statusMessage = null
        }
    }

    val maxDurationSec = uiState.captureMode.maxDurationSec.coerceAtLeast(1)

    fun triggerVideoRecording() {
        createViewModel.closeToolPanel()
        if (!hasAudioPermission) {
            statusMessage = "Recording video (microphone permission disabled)."
        }
        cameraController.startVideoRecording(
            onStarted = { statusMessage = null },
            onError = { err -> statusMessage = err },
            onFinalized = { uri: Uri ->
                val durMs = (elapsedSec * 1000L).coerceAtLeast(1500L)
                createViewModel.onVideoSelected(uri, durMs)
            }
        )
    }

    LaunchedEffect(cameraController.isRecording, maxDurationSec) {
        if (cameraController.isRecording) {
            elapsedSec = 0
            while (cameraController.isRecording && elapsedSec < maxDurationSec) {
                delay(1000)
                if (cameraController.isRecording) elapsedSec++
            }
            if (cameraController.isRecording) cameraController.stopVideoRecording()
        } else {
            elapsedSec = 0
        }
    }

    LaunchedEffect(countdownRemaining) {
        if (countdownRemaining > 0) {
            val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }.getOrNull()
            runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
            delay(1000)
            runCatching { toneGen?.release() }
            if (countdownRemaining == 1) {
                countdownRemaining = 0
                triggerVideoRecording()
            } else if (countdownRemaining > 1) {
                countdownRemaining--
            }
        }
    }

    val recordSyncAudioPlayer = remember(context) {
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(attrs, /* handleAudioFocus = */ false)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(Unit) {
        onDispose { recordSyncAudioPlayer.release() }
    }

    LaunchedEffect(
        cameraController.isRecording,
        uiState.selectedSound?.id,
        uiState.soundTrimStartMs,
        uiState.soundTrimEndMs,
        uiState.audioMix.musicVolume
    ) {
        val sound = uiState.selectedSound
        if (cameraController.isRecording && sound != null && sound.audioUrl.isNotBlank()) {
            val builder = MediaItem.Builder().setUri(Uri.parse(sound.audioUrl))
            if (uiState.soundTrimEndMs > uiState.soundTrimStartMs) {
                builder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(uiState.soundTrimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(uiState.soundTrimEndMs)
                        .build()
                )
            }
            recordSyncAudioPlayer.setMediaItem(builder.build())
            recordSyncAudioPlayer.volume = uiState.audioMix.musicVolume.coerceIn(0f, 1f)
            recordSyncAudioPlayer.prepare()
            recordSyncAudioPlayer.playWhenReady = true
        } else {
            recordSyncAudioPlayer.pause()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreviewViewport(
                controller = cameraController,
                hasCameraPermission = hasCameraPermission,
                useFrontCamera = uiState.useFrontCamera,
                flashEnabled = uiState.flashEnabled,
                beauty = uiState.beauty,
                arEffect = uiState.arEffect,
                greenScreenEnabled = uiState.greenScreenEnabled,
                greenScreenPreset = uiState.greenScreenPreset,
                onLumaSampled = { createViewModel.updateSceneLuma(it) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = BladeCrimson, modifier = Modifier.size(56.dp))
                Text("Camera & Audio Access", color = TextWhite, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Allow Camera and Microphone permissions to record crisp front or rear videos, or upload a video from your library.",
                    color = TextWhite.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                    colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson)
                ) { Text("Allow Camera & Mic") }
                OutlinedButton(onClick = onPickVideoGallery, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = TextWhite)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload Video", color = TextWhite)
                }
            }
        }

        // Front Soft Fill-Light Ring Glow (brightens faces cleanly in dim rooms without harsh glare)
        if (uiState.useFrontCamera && uiState.frontFillLightEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 18.dp,
                        brush = Brush.radialGradient(listOf(Color(0xD8FFF9F0), Color(0x88FFF6E5))),
                        shape = RoundedCornerShape(0.dp)
                    )
                    .background(Color(0x16FFF9F0))
            )
        }

        // Filter & Effect fallback tint (for API < 31 or subtle vignette polish)
        val filterTint = filterOverlayColor(uiState.selectedFilter)
        if (filterTint != Color.Transparent) {
            Box(modifier = Modifier.fillMaxSize().background(filterTint.copy(alpha = filterTint.alpha * uiState.filterIntensity)))
        }
        uiState.selectedEffect?.let { eff ->
            if (eff.vignetteAlpha > 0f) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = eff.vignetteAlpha)))
                    )
                )
            }
        }

        // Green Screen selected backdrop pill
        if (uiState.greenScreenEnabled) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, BladeCyan.copy(alpha = 0.8f)),
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 74.dp)
            ) {
                Text(
                    text = "Green Screen: ${uiState.greenScreenPreset?.name ?: "Video Clip"}",
                    color = TextWhite,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Clear camera feed: no full-width dark top/bottom scrims covering the front preview!

        cameraController.cameraError?.let { err ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, BladeCrimson),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(err, color = TextWhite, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { cameraController.retryBinding() }, colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry Camera")
                        }
                        OutlinedButton(onClick = onPickVideoGallery) { Text("Upload Video", color = TextWhite) }
                    }
                }
            }
        }

        // Top Bar: Close (X) — ♪ Sounds Pill — Draft / Flip
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { cameraController.release(); onClose() },
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.36f)).testTag("camera_close_button")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close camera", tint = TextWhite, modifier = Modifier.size(26.dp))
            }

            Surface(
                onClick = { if (!cameraController.isRecording) showSoundSheet = true },
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.56f),
                border = BorderStroke(1.dp, if (uiState.selectedSound != null) BladeCrimson else Color.White.copy(alpha = 0.16f)),
                modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("camera_sounds_button")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = if (uiState.selectedSound != null) BladeCrimson else TextWhite, modifier = Modifier.size(16.dp))
                    Text(uiState.selectedSound?.title ?: "Add Audio", color = TextWhite, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (uiState.selectedSound != null) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear sound", tint = TextWhite.copy(alpha = 0.78f), modifier = Modifier.size(16.dp).clickable { createViewModel.removeSelectedSound() })
                    }
                }
            }

            when {
                uiState.hasDraftMedia && !cameraController.isRecording -> {
                    Surface(onClick = { createViewModel.resumeDraftEdit() }, shape = RoundedCornerShape(50), color = BladeCrimson, modifier = Modifier.testTag("resume_draft_chip")) {
                        Text("Draft", color = TextWhite, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                uiState.hasRecoverableDraft && !cameraController.isRecording -> {
                    Surface(onClick = { createViewModel.recoverSavedDraft() }, shape = RoundedCornerShape(50), color = BladeCyan, modifier = Modifier.testTag("recover_draft_chip")) {
                        Text("Restore", color = Color.Black, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                else -> {
                    IconButton(
                        onClick = { if (!cameraController.isRecording) createViewModel.toggleFrontCamera() },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.36f)).testTag("camera_top_flip_button")
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip camera", tint = TextWhite, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }

        // Right-Side Vertical Camera Tools Rail
        AnimatedVisibility(
            visible = !cameraController.isRecording && countdownRemaining == 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 72.dp, end = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RightCameraToolItem(Icons.Filled.Cameraswitch, "Flip", uiState.useFrontCamera, { createViewModel.toggleFrontCamera() }, "tool_flip")
                if (uiState.useFrontCamera) {
                    RightCameraToolItem(Icons.Filled.WbSunny, "Fill Light", uiState.frontFillLightEnabled, { createViewModel.toggleFrontFillLight() }, "tool_fill_light", activeTint = BladeGold)
                } else {
                    RightCameraToolItem(
                        icon = if (uiState.flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        label = "Flash",
                        active = uiState.flashEnabled,
                        activeTint = BladeGold,
                        onClick = {
                            if (!cameraController.hasFlashUnit) statusMessage = "Flash not supported on this lens."
                            else createViewModel.toggleFlash(cameraController.hasFlashUnit)
                        },
                        testTag = "tool_flash"
                    )
                }
                RightCameraToolItem(Icons.Filled.Timer, if (uiState.timerSeconds > 0) "${uiState.timerSeconds}s" else "Timer", uiState.timerSeconds > 0 || uiState.activeToolPanel == CameraToolPanel.TIMER, { createViewModel.toggleToolPanel(CameraToolPanel.TIMER) }, "tool_timer")
                RightCameraToolItem(Icons.Filled.AutoAwesome, "Filters", uiState.selectedFilter != "Original" || uiState.activeToolPanel == CameraToolPanel.FILTERS, { createViewModel.toggleToolPanel(CameraToolPanel.FILTERS) }, "tool_filters")
                RightCameraToolItem(Icons.Filled.FaceRetouchingNatural, "Beautify", uiState.beauty.enabled || uiState.activeToolPanel == CameraToolPanel.BEAUTIFY, { createViewModel.toggleToolPanel(CameraToolPanel.BEAUTIFY) }, "tool_beautify")
                RightCameraToolItem(Icons.Filled.Face, "AR Face", uiState.arEffect != ArEffectType.NONE || uiState.activeToolPanel == CameraToolPanel.AR_FACE, { createViewModel.toggleToolPanel(CameraToolPanel.AR_FACE) }, "tool_ar_face")
                RightCameraToolItem(Icons.Filled.Wallpaper, "Green Scn", uiState.greenScreenEnabled || uiState.activeToolPanel == CameraToolPanel.GREEN_SCREEN, { createViewModel.toggleToolPanel(CameraToolPanel.GREEN_SCREEN) }, "tool_green_screen")
                RightCameraToolItem(Icons.Filled.Speed, if (uiState.speedMultiplier == 1f) "Speed" else "${uiState.speedMultiplier}x", uiState.speedMultiplier != 1f || uiState.activeToolPanel == CameraToolPanel.SPEED, { createViewModel.toggleToolPanel(CameraToolPanel.SPEED) }, "tool_speed")
            }
        }

        // Countdown Overlay
        AnimatedVisibility(
            visible = countdownRemaining > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { countdownRemaining = 0 }
        ) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(countdownRemaining.toString(), color = TextWhite, fontSize = 104.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                Text("Tap anywhere to cancel", color = TextWhite.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            }
        }

        statusMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 68.dp, start = 24.dp, end = 24.dp)
            ) {
                Text(msg, color = TextWhite, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }

        // Bottom Dock: Active Tray, Recording Timer, Video Duration Pills (3m | 60s | 15s), Bottom Capture Row
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!cameraController.isRecording && countdownRemaining == 0) {
                ActiveCameraToolTray(
                    uiState = uiState,
                    createViewModel = createViewModel,
                    onPickGreenScreenVideo = onPickGreenScreenVideo,
                    onOpenFilterLibrary = { showFilterLibrarySheet = true },
                    onOpenBeautyStudio = { showBeautyStudioSheet = true }
                )
            }

            AnimatedVisibility(visible = cameraController.isRecording) {
                Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.65f), border = BorderStroke(1.dp, BladeCrimson.copy(alpha = 0.7f))) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(BladeCrimson))
                        Text(
                            text = "${formatDurationMs(elapsedSec * 1000L)} / ${formatDurationMs(maxDurationSec * 1000L)}",
                            color = TextWhite,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                        )
                    }
                }
            }

            if (!cameraController.isRecording) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier.testTag("capture_mode_selector")
                ) {
                    items(CaptureMode.entries, key = { it.name }) { mode ->
                        val selected = uiState.captureMode == mode
                        Surface(
                            onClick = { createViewModel.setCaptureMode(mode) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) Color.White else Color.Black.copy(alpha = 0.28f),
                            modifier = Modifier.testTag("mode_pill_${mode.label}")
                        ) {
                            Text(
                                text = mode.label,
                                color = if (selected) Color.Black else TextWhite,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (cameraController.isRecording) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { cameraController.cancelVideoRecording(); statusMessage = "Recording cancelled." }
                            .padding(4.dp).testTag("cancel_recording_button")
                    ) {
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.36f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel recording", tint = TextWhite)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Cancel", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { showEffectsSheet = true }.padding(2.dp).testTag("camera_effects_button")
                    ) {
                        val eff = uiState.selectedEffect
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (eff != null) Color(eff.tintArgb).copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.32f))
                                .border(1.5.dp, if (eff != null) TextWhite else Color.White.copy(alpha = 0.42f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (eff != null) Text(eff.badge, fontSize = 20.sp)
                            else Icon(Icons.Filled.AutoFixHigh, contentDescription = "AR & Effects", tint = TextWhite, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Effects",
                            color = TextWhite,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 1.5f), 4f)
                            )
                        )
                    }
                }

                val progressFraction by animateFloatAsState(
                    targetValue = if (cameraController.isRecording) (elapsedSec.toFloat() / maxDurationSec.toFloat()).coerceIn(0f, 1f) else 0f,
                    animationSpec = tween(250), label = "recProgress"
                )
                val innerSize by animateDpAsState(targetValue = if (cameraController.isRecording) 32.dp else 64.dp, animationSpec = tween(180), label = "shutterSize")
                val innerCorner by animateDpAsState(targetValue = if (cameraController.isRecording) 8.dp else 32.dp, animationSpec = tween(180), label = "shutterCorner")

                Box(
                    modifier = Modifier.size(86.dp).clip(CircleShape)
                        .clickable(enabled = hasCameraPermission && countdownRemaining == 0 && !cameraController.isPreparingRecording) {
                            if (cameraController.isRecording) cameraController.stopVideoRecording()
                            else if (uiState.timerSeconds > 0) countdownRemaining = uiState.timerSeconds
                            else triggerVideoRecording()
                        }.testTag("record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.White, style = Stroke(width = 4.dp.toPx()))
                        if (progressFraction > 0f) {
                            drawArc(color = BladeRed, startAngle = -90f, sweepAngle = 360f * progressFraction, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                    Box(modifier = Modifier.size(innerSize).clip(RoundedCornerShape(innerCorner)).background(if (cameraController.isRecording) BladeRed else BladeCrimson))
                }

                if (cameraController.isRecording) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onPickVideoGallery).padding(2.dp).testTag("pick_gallery_video")
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.32f))
                                .border(1.5.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = "Upload video", tint = TextWhite, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Upload",
                            color = TextWhite,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 1.5f), 4f)
                            )
                        )
                    }
                }
            }
        }
    }

    if (showSoundSheet) {
        SoundPickerModalSheet(uiState = uiState, createViewModel = createViewModel, onDismiss = { showSoundSheet = false })
    }
    if (showEffectsSheet) {
        EffectsGallerySheet(uiState = uiState, createViewModel = createViewModel, onDismiss = { showEffectsSheet = false })
    }
    if (showFilterLibrarySheet) {
        FilterLibraryModalSheet(uiState = uiState, createViewModel = createViewModel, onDismiss = { showFilterLibrarySheet = false })
    }
    if (showBeautyStudioSheet) {
        BeautyStudioModalSheet(uiState = uiState, createViewModel = createViewModel, onDismiss = { showBeautyStudioSheet = false })
    }
}

@Composable
private fun RightCameraToolItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    activeTint: Color = BladeCrimson
) {
    val shadow = remember { Shadow(color = Color.Black.copy(alpha = 0.78f), offset = Offset(0f, 1.5f), blurRadius = 4f) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) {}.testTag(testTag)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = if (active) activeTint else TextWhite, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (active) activeTint else TextWhite,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, shadow = shadow),
            maxLines = 1
        )
    }
}
