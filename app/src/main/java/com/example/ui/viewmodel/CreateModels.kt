package com.example.ui.viewmodel

import android.net.Uri
import com.example.core.camera.ArEffectType
import com.example.core.camera.BeautySettings
import com.example.data.ai.AiCaptionCue
import com.example.data.ai.AiEffectOutput
import com.example.data.ai.AiEffectType
import com.example.data.model.SoundItem
import kotlinx.serialization.Serializable
import java.util.UUID

enum class CreateStep {
    CAPTURE,
    EDIT_MEDIA,
    PUBLISH_MEDIA
}

/**
 * Video-only capture durations (photo mode completely removed per requirements).
 */
enum class CaptureMode(val label: String, val maxDurationSec: Int) {
    M3("3m", 180),
    S60("60s", 60),
    S15("15s", 15)
}

enum class CameraToolPanel {
    NONE,
    TIMER,
    FILTERS,
    BEAUTIFY,
    AR_FACE,
    GREEN_SCREEN,
    SPEED
}

enum class VideoTransition(val label: String) {
    CUT("Cut"),
    CROSSFADE("Crossfade"),
    SLIDE("Slide"),
    ZOOM_BLUR("Zoom Blur"),
    FLASH("Flash")
}

enum class OverlayAnimation(val label: String) {
    STATIC("Static"),
    POP("Pop"),
    TYPEWRITER("Typewriter"),
    BOUNCE("Bounce")
}

data class CameraEffect(
    val id: String,
    val name: String,
    val category: String,
    val badge: String,
    val tintArgb: Long,
    val vignetteAlpha: Float = 0f,
    val description: String = "",
    val arEffect: ArEffectType = ArEffectType.NONE
)

data class GreenScreenPreset(
    val id: String,
    val name: String,
    val startArgb: Long,
    val endArgb: Long
)

data class OverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isSticker: Boolean = false,
    val colorArgb: Long = 0xFFFFFFFF,
    val positionIndex: Int = 1, // 0 = Top, 1 = Center, 2 = Bottom
    val animation: OverlayAnimation = OverlayAnimation.STATIC,
    val startMs: Long = 0L,
    val endMs: Long = 60_000L
)

data class VideoSegment(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val speed: Float = 1f,
    val transitionAfter: VideoTransition = VideoTransition.CUT
) {
    val effectiveDurationMs: Long
        get() = (((trimEndMs - trimStartMs).coerceAtLeast(400L)) / speed.coerceIn(0.3f, 3f)).toLong()
}

data class ColorCorrectionState(
    val brightness: Float = 0f,    // -0.4..0.4
    val contrast: Float = 1f,      // 0.6..1.5
    val saturation: Float = 1f,    // 0.0..1.8
    val sharpness: Float = 0.25f,  // 0.0..1.0
    val warmth: Float = 0f         // -0.35..0.35
)

data class AudioMixState(
    val originalVolume: Float = 1f,
    val musicVolume: Float = 0.75f,
    val voiceOverVolume: Float = 1f,
    val voiceOverPath: String? = null,
    val activeSfxLabel: String? = null
)

data class EditorSnapshot(
    val segments: List<VideoSegment>,
    val overlays: List<OverlayItem>,
    val captions: List<AiCaptionCue>,
    val selectedFilter: String,
    val filterIntensity: Float,
    val colorCorrection: ColorCorrectionState,
    val speedMultiplier: Float,
    val reversePlayback: Boolean,
    val rotationDegrees: Int,
    val aspectRatioMode: String,
    val zoomScale: Float,
    val chromaKeyEnabled: Boolean,
    val chromaKeyColorHex: Long,
    val chromaKeyTolerance: Float,
    val pipVideoUriString: String?,
    val audioMix: AudioMixState,
    val selectedSound: SoundItem?,
    val selectedSoundDurationMs: Long = 30_000L,
    val soundTrimStartMs: Long = 0L,
    val soundTrimEndMs: Long = 30_000L,
    val isDeviceAudio: Boolean = false,
    val coverFrameMs: Long
)

@Serializable
data class SavedProjectDraft(
    val primaryUriString: String,
    val segments: List<SavedSegmentDto>,
    val overlays: List<SavedOverlayDto>,
    val captions: List<SavedCaptionDto>,
    val selectedFilter: String,
    val filterIntensity: Float,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val speedMultiplier: Float,
    val reversePlayback: Boolean,
    val rotationDegrees: Int,
    val aspectRatioMode: String,
    val zoomScale: Float,
    val title: String,
    val caption: String,
    val savedAtEpochMs: Long
)

@Serializable
data class SavedSegmentDto(
    val id: String,
    val uriString: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speed: Float,
    val transitionName: String
)

@Serializable
data class SavedOverlayDto(
    val id: String,
    val text: String,
    val isSticker: Boolean,
    val colorArgb: Long,
    val positionIndex: Int,
    val animationName: String,
    val startMs: Long,
    val endMs: Long
)

@Serializable
data class SavedCaptionDto(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class CreateUiState(
    val step: CreateStep = CreateStep.CAPTURE,
    val captureMode: CaptureMode = CaptureMode.S60,
    val selectedVideoUri: Uri? = null,
    // Camera hardware / front-video defaults (front camera bright & active by default)
    val useFrontCamera: Boolean = true,
    val frontFillLightEnabled: Boolean = false,
    val flashEnabled: Boolean = false,
    val timerSeconds: Int = 0, // 0, 3, 10
    val speedMultiplier: Float = 1f, // 0.3, 0.5, 1.0, 2.0, 3.0
    val beauty: BeautySettings = BeautySettings(),
    val arEffect: ArEffectType = ArEffectType.NONE,
    val selectedFilter: String = "Original",
    val filterIntensity: Float = 0.85f,
    val filterSearchQuery: String = "",
    val selectedFilterCategory: String = "All",
    val favoriteFilterIds: Set<String> = setOf("natural", "portrait", "golden_hour", "beauty", "cinematic"),
    val downloadedFilterIds: Set<String> = setOf("original", "natural", "portrait", "warm", "cool", "bright", "beauty"),
    val downloadingFilterIds: Set<String> = emptySet(),
    val selectedEffect: CameraEffect? = null,
    val greenScreenPreset: GreenScreenPreset? = null,
    val greenScreenVideoUri: Uri? = null,
    val greenScreenEnabled: Boolean = false,
    val sceneLuma: Float = 128f,
    val activeToolPanel: CameraToolPanel = CameraToolPanel.NONE,
    // Timeline & Editing Studio state
    val segments: List<VideoSegment> = emptyList(),
    val selectedSegmentIndex: Int = 0,
    val playheadMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 60_000L,
    val videoDurationMs: Long = 60_000L,
    val coverFrameMs: Long = 0L,
    val overlays: List<OverlayItem> = emptyList(),
    val captions: List<AiCaptionCue> = emptyList(),
    val colorCorrection: ColorCorrectionState = ColorCorrectionState(),
    val reversePlayback: Boolean = false,
    val rotationDegrees: Int = 0,
    val aspectRatioMode: String = "9:16",
    val zoomScale: Float = 1f,
    val chromaKeyEnabled: Boolean = false,
    val chromaKeyColorHex: Long = 0xFF10B981,
    val chromaKeyTolerance: Float = 0.35f,
    val pipVideoUri: Uri? = null,
    val pipCornerIndex: Int = 1,
    val audioMix: AudioMixState = AudioMixState(),
    val isRecordingVoiceOver: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasRecoverableDraft: Boolean = false,
    val draftSavedNotice: String? = null,
    // AI-Powered Effects state
    val isProcessingAi: Boolean = false,
    val aiProgress: Float = 0f,
    val aiProgressLabel: String = "",
    val aiOutput: AiEffectOutput? = null,
    val aiError: String? = null,
    val lastAiEffectType: AiEffectType? = null,
    // Sound picker & local audio import/trimming state
    val selectedSound: SoundItem? = null,
    val selectedSoundDurationMs: Long = 30_000L,
    val soundTrimStartMs: Long = 0L,
    val soundTrimEndMs: Long = 30_000L,
    val isDeviceAudio: Boolean = false,
    val isImportingAudio: Boolean = false,
    val audioImportProgress: Float = 0f,
    val audioImportLabel: String = "",
    val audioImportError: String? = null,
    val availableSounds: List<SoundItem> = emptyList(),
    val soundSearchQuery: String = "",
    val soundCategory: String = "All",
    // Publish metadata & idempotent retry-safe upload state
    val title: String = "",
    val caption: String = "",
    val location: String = "",
    val category: String = "All",
    val privacy: String = "Public",
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val isCheckingSafety: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploadStatusText: String = "",
    val uploadError: String? = null,
    val uploadSuccess: Boolean = false,
    val pendingUploadKey: String? = null,
    val cachedExportUri: Uri? = null,
    val uploadedVideoUrl: String? = null,
    val uploadedThumbUrl: String? = null
) {
    val hasDraftMedia: Boolean get() = selectedVideoUri != null
    val totalTimelineDurationMs: Long
        get() = if (segments.isEmpty()) videoDurationMs else segments.sumOf { it.effectiveDurationMs }
    val beautySmoothing: Float get() = beauty.skinSmoothing
    val beautyBrightness: Float get() = beauty.brightnessBoost
}
