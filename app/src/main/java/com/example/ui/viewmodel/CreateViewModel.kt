package com.example.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.camera.ArEffectType
import com.example.core.camera.AudioImportManager
import com.example.core.camera.BeautySettings
import com.example.core.camera.FilterCategory
import com.example.core.camera.VideoExportEngine
import com.example.core.camera.VideoExportRequest
import com.example.core.camera.VideoFilterCatalog
import com.example.core.camera.VideoFilterPreset
import com.example.core.camera.VideoSegmentExportSpec
import com.example.core.moderation.ModerationResult
import com.example.core.moderation.VideoContentModerator
import com.example.data.ai.AiCaptionCue
import com.example.data.ai.AiEffectType
import com.example.data.ai.AiEffectsRepository
import com.example.data.ai.LocalSampleAiEffectsRepository
import com.example.data.model.SoundItem
import com.example.data.model.VideoItem
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class CreateViewModel(
    private val repository: BladeRepository,
    private val aiRepository: AiEffectsRepository = LocalSampleAiEffectsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private val json = Json { ignoreUnknownKeys = true }

    val filterPresets: List<VideoFilterPreset> = VideoFilterCatalog.allFilters
    val filterCategories: List<FilterCategory> = FilterCategory.entries
    val speedOptions = listOf(0.3f, 0.5f, 1f, 2f, 3f)
    val timerOptions = listOf(0, 3, 10)
    val aspectRatioOptions = listOf("9:16", "1:1", "4:5", "16:9")
    val categories = listOf("All", "Comedy", "Music", "Dance", "Cricket", "Tech", "Food", "Travel", "Gaming")
    val privacies = listOf("Public", "Friends", "Private")
    val effectCategories = listOf("Trending", "Animal", "Funny", "Accessories")
    val soundCategories = listOf("All", "Trending", "Pop", "Hip-Hop", "Chill", "Desi Beats")
    val sfxPresets = listOf("Whoosh", "Pop", "Bass Drop", "Vinyl Scratch", "Applause", "Sparkle")
    val lipstickShades = listOf(0xFFD94A64L, 0xFFE50914L, 0xFFC026D3L, 0xFFEA580CL, 0xFF9F1239L)

    val effectsCatalog = listOf(
        CameraEffect("eff_neon", "Neon Edge", "Trending", "⚡", 0x2400E5FF, 0.16f, "Electric cyan studio glow", ArEffectType.SPARKLE_HALO),
        CameraEffect("eff_blade", "Blade Pulse", "Trending", "🔥", 0x26E50914, 0.2f, "Crimson high-contrast cinema", ArEffectType.NONE),
        CameraEffect("eff_cat", "Cat Ears", "Animal", "🐱", 0x16FFB6C1, 0f, "Face-tracked kitty ears & whiskers", ArEffectType.CAT_EARS),
        CameraEffect("eff_puppy", "Puppy", "Animal", "🐶", 0x18F59E0B, 0f, "Tracked puppy ears & cute nose", ArEffectType.PUPPY),
        CameraEffect("eff_bunny", "Bunny", "Animal", "🐰", 0x18EC4899, 0f, "Floppy bunny ears & whiskers", ArEffectType.BUNNY),
        CameraEffect("eff_bear", "Bear", "Animal", "🐻", 0x1892400E, 0f, "Cozy bear ears & rosy cheeks", ArEffectType.BEAR),
        CameraEffect("eff_eyes", "Big Eyes", "Funny", "👀", 0x1638BDF8, 0f, "Expressive sparkling eye glow", ArEffectType.BIG_EYES),
        CameraEffect("eff_morph", "Funny Morph", "Funny", "🤪", 0x18FFD600, 0f, "Smile reaction & comic cheeks", ArEffectType.FUNNY_MORPH),
        CameraEffect("eff_emoji", "Emoji Pop", "Funny", "🥳", 0x18A855F7, 0.08f, "Orbiting emoji aura", ArEffectType.EMOJI_BURST),
        CameraEffect("eff_shades", "Neon Shades", "Accessories", "🕶️", 0x180F172A, 0.12f, "Head-tracked glowing sunglasses", ArEffectType.NEON_SHADES),
        CameraEffect("eff_crown", "Royal Crown", "Accessories", "👑", 0x18FACC15, 0.06f, "Golden floating crown", ArEffectType.CROWN),
        CameraEffect("eff_cyber", "Cyber Visor", "Accessories", "🤖", 0x1C06B6D4, 0.18f, "Futuristic neon face visor", ArEffectType.CYBER_MASK)
    )

    val greenScreenPresets = listOf(
        GreenScreenPreset("gs_neon", "Neon Stage", 0xFF0F172A, 0xFFE50914),
        GreenScreenPreset("gs_sunset", "Sunset Roof", 0xFF7C2D12, 0xFFF59E0B),
        GreenScreenPreset("gs_studio", "Bright Studio", 0xFF1E293B, 0xFF38BDF8),
        GreenScreenPreset("gs_aurora", "Aurora", 0xFF064E3B, 0xFF10B981)
    )

    val stickerPresets = listOf("🔥", "😂", "❤️", "⚡", "🎬", "✨", "🎵", "💯", "🚀", "🙌", "👑", "🥳")

    init {
        loadSounds()
        loadStudioPrefs()
    }

    private fun loadSounds() {
        viewModelScope.launch {
            val sounds = runCatching { repository.getSounds() }.getOrDefault(emptyList())
            _uiState.update { it.copy(availableSounds = sounds) }
        }
    }

    private fun loadStudioPrefs() {
        viewModelScope.launch {
            val favs = runCatching {
                repository.getFavoriteFilterIds(_uiState.value.favoriteFilterIds)
            }.getOrDefault(_uiState.value.favoriteFilterIds)
            val dls = runCatching {
                repository.getDownloadedFilterIds(_uiState.value.downloadedFilterIds)
            }.getOrDefault(_uiState.value.downloadedFilterIds)
            val draftJson = runCatching { repository.getSavedStudioDraftJson() }.getOrNull()
            _uiState.update {
                it.copy(
                    favoriteFilterIds = favs,
                    downloadedFilterIds = dls,
                    hasRecoverableDraft = !draftJson.isNullOrBlank()
                )
            }
        }
    }

    private fun pushUndoSnapshot() {
        val s = _uiState.value
        val snap = EditorSnapshot(
            segments = s.segments,
            overlays = s.overlays,
            captions = s.captions,
            selectedFilter = s.selectedFilter,
            filterIntensity = s.filterIntensity,
            colorCorrection = s.colorCorrection,
            speedMultiplier = s.speedMultiplier,
            reversePlayback = s.reversePlayback,
            rotationDegrees = s.rotationDegrees,
            aspectRatioMode = s.aspectRatioMode,
            zoomScale = s.zoomScale,
            chromaKeyEnabled = s.chromaKeyEnabled,
            chromaKeyColorHex = s.chromaKeyColorHex,
            chromaKeyTolerance = s.chromaKeyTolerance,
            pipVideoUriString = s.pipVideoUri?.toString(),
            audioMix = s.audioMix,
            selectedSound = s.selectedSound,
            coverFrameMs = s.coverFrameMs
        )
        if (undoStack.size >= 25) undoStack.removeFirst()
        undoStack.addLast(snap)
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
    }

    private fun currentAsSnapshot(): EditorSnapshot {
        val s = _uiState.value
        return EditorSnapshot(
            segments = s.segments,
            overlays = s.overlays,
            captions = s.captions,
            selectedFilter = s.selectedFilter,
            filterIntensity = s.filterIntensity,
            colorCorrection = s.colorCorrection,
            speedMultiplier = s.speedMultiplier,
            reversePlayback = s.reversePlayback,
            rotationDegrees = s.rotationDegrees,
            aspectRatioMode = s.aspectRatioMode,
            zoomScale = s.zoomScale,
            chromaKeyEnabled = s.chromaKeyEnabled,
            chromaKeyColorHex = s.chromaKeyColorHex,
            chromaKeyTolerance = s.chromaKeyTolerance,
            pipVideoUriString = s.pipVideoUri?.toString(),
            audioMix = s.audioMix,
            selectedSound = s.selectedSound,
            selectedSoundDurationMs = s.selectedSoundDurationMs,
            soundTrimStartMs = s.soundTrimStartMs,
            soundTrimEndMs = s.soundTrimEndMs,
            isDeviceAudio = s.isDeviceAudio,
            coverFrameMs = s.coverFrameMs
        )
    }

    private fun restoreSnapshot(snap: EditorSnapshot) {
        val firstSeg = snap.segments.firstOrNull()
        _uiState.update {
            it.copy(
                segments = snap.segments,
                selectedSegmentIndex = it.selectedSegmentIndex.coerceIn(0, (snap.segments.size - 1).coerceAtLeast(0)),
                selectedVideoUri = firstSeg?.let { seg -> Uri.parse(seg.uriString) } ?: it.selectedVideoUri,
                trimStartMs = firstSeg?.trimStartMs ?: it.trimStartMs,
                trimEndMs = firstSeg?.trimEndMs ?: it.trimEndMs,
                overlays = snap.overlays,
                captions = snap.captions,
                selectedFilter = snap.selectedFilter,
                filterIntensity = snap.filterIntensity,
                colorCorrection = snap.colorCorrection,
                speedMultiplier = snap.speedMultiplier,
                reversePlayback = snap.reversePlayback,
                rotationDegrees = snap.rotationDegrees,
                aspectRatioMode = snap.aspectRatioMode,
                zoomScale = snap.zoomScale,
                chromaKeyEnabled = snap.chromaKeyEnabled,
                chromaKeyColorHex = snap.chromaKeyColorHex,
                chromaKeyTolerance = snap.chromaKeyTolerance,
                pipVideoUri = snap.pipVideoUriString?.let { p -> Uri.parse(p) },
                audioMix = snap.audioMix,
                selectedSound = snap.selectedSound,
                selectedSoundDurationMs = snap.selectedSoundDurationMs,
                soundTrimStartMs = snap.soundTrimStartMs,
                soundTrimEndMs = snap.soundTrimEndMs,
                isDeviceAudio = snap.isDeviceAudio,
                coverFrameMs = snap.coverFrameMs,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(currentAsSnapshot())
        restoreSnapshot(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(currentAsSnapshot())
        restoreSnapshot(next)
    }

    // --- Camera & Capture Tools ---
    fun setCaptureMode(mode: CaptureMode) = _uiState.update { it.copy(captureMode = mode) }

    fun toggleFrontCamera() = _uiState.update { state ->
        val nextFront = !state.useFrontCamera
        state.copy(
            useFrontCamera = nextFront,
            flashEnabled = if (nextFront) false else state.flashEnabled
        )
    }

    fun toggleFrontFillLight() = _uiState.update { it.copy(frontFillLightEnabled = !it.frontFillLightEnabled) }

    fun toggleFlash(hasHardwareFlash: Boolean) = _uiState.update { state ->
        if (!hasHardwareFlash || state.useFrontCamera) state.copy(flashEnabled = false)
        else state.copy(flashEnabled = !state.flashEnabled)
    }

    fun toggleToolPanel(panel: CameraToolPanel) = _uiState.update { state ->
        state.copy(activeToolPanel = if (state.activeToolPanel == panel) CameraToolPanel.NONE else panel)
    }

    fun closeToolPanel() = _uiState.update { it.copy(activeToolPanel = CameraToolPanel.NONE) }
    fun setTimerSeconds(seconds: Int) = _uiState.update { it.copy(timerSeconds = seconds) }
    fun updateSceneLuma(luma: Float) = _uiState.update { it.copy(sceneLuma = luma.coerceIn(0f, 255f)) }

    // --- Filters Library ---
    fun updateFilter(name: String) {
        if (_uiState.value.step == CreateStep.EDIT_MEDIA) pushUndoSnapshot()
        _uiState.update { it.copy(selectedFilter = name) }
    }

    fun updateFilterIntensity(value: Float) =
        _uiState.update { it.copy(filterIntensity = value.coerceIn(0f, 1f)) }

    fun updateFilterSearch(query: String) = _uiState.update { it.copy(filterSearchQuery = query) }
    fun updateFilterCategory(category: FilterCategory) =
        _uiState.update { it.copy(selectedFilterCategory = category.label) }

    fun toggleFavoriteFilter(filterId: String) {
        val next = _uiState.value.favoriteFilterIds.toMutableSet().apply {
            if (!add(filterId)) remove(filterId)
        }
        _uiState.update { it.copy(favoriteFilterIds = next) }
        viewModelScope.launch { runCatching { repository.saveFavoriteFilterIds(next) } }
    }

    fun downloadFilter(filterId: String) {
        val state = _uiState.value
        if (filterId in state.downloadedFilterIds || filterId in state.downloadingFilterIds) return
        _uiState.update { it.copy(downloadingFilterIds = it.downloadingFilterIds + filterId) }
        viewModelScope.launch {
            delay(450)
            val nextDownloaded = _uiState.value.downloadedFilterIds + filterId
            _uiState.update {
                it.copy(
                    downloadingFilterIds = it.downloadingFilterIds - filterId,
                    downloadedFilterIds = nextDownloaded
                )
            }
            runCatching { repository.saveDownloadedFilterIds(nextDownloaded) }
        }
    }

    // --- Beauty & AR Face ---
    fun updateBeauty(transform: (BeautySettings) -> BeautySettings) =
        _uiState.update { it.copy(beauty = transform(it.beauty)) }

    fun resetBeauty() = _uiState.update { it.copy(beauty = BeautySettings()) }

    fun setArEffect(arEffect: ArEffectType) =
        _uiState.update { it.copy(arEffect = arEffect) }

    fun selectEffect(effect: CameraEffect?) = _uiState.update {
        it.copy(selectedEffect = effect, arEffect = effect?.arEffect ?: ArEffectType.NONE)
    }

    fun setGreenScreenPreset(preset: GreenScreenPreset?) = _uiState.update {
        it.copy(greenScreenPreset = preset, greenScreenVideoUri = null, greenScreenEnabled = preset != null)
    }

    fun setGreenScreenVideoUri(uri: Uri?) = _uiState.update {
        it.copy(greenScreenVideoUri = uri, greenScreenPreset = null, greenScreenEnabled = uri != null)
    }

    fun clearGreenScreen() = _uiState.update {
        it.copy(greenScreenPreset = null, greenScreenVideoUri = null, greenScreenEnabled = false)
    }

    // --- Sound Picker & Phone Audio Import / Trimming ---
    fun preselectSound(sound: SoundItem) = selectSound(sound)
    fun updateSoundSearch(query: String) = _uiState.update { it.copy(soundSearchQuery = query) }
    fun updateSoundCategory(cat: String) = _uiState.update { it.copy(soundCategory = cat) }

    fun selectSound(
        sound: SoundItem?,
        fullDurationMs: Long? = null,
        isLocalDevice: Boolean = false
    ) {
        if (_uiState.value.step == CreateStep.EDIT_MEDIA) pushUndoSnapshot()
        if (sound == null) {
            _uiState.update {
                it.copy(
                    selectedSound = null,
                    selectedSoundDurationMs = 30_000L,
                    soundTrimStartMs = 0L,
                    soundTrimEndMs = 30_000L,
                    isDeviceAudio = false,
                    audioImportError = null
                )
            }
            return
        }
        val rawDur = (fullDurationMs ?: (sound.durationSeconds.toLong() * 1000L)).coerceAtLeast(1_000L)
        val defaultEnd = rawDur.coerceAtMost(
            if (_uiState.value.step == CreateStep.EDIT_MEDIA) _uiState.value.totalTimelineDurationMs.coerceAtLeast(15_000L)
            else (_uiState.value.captureMode.maxDurationSec * 1000L).coerceAtLeast(15_000L)
        ).coerceAtLeast(1_000L)
        _uiState.update {
            it.copy(
                selectedSound = sound,
                selectedSoundDurationMs = rawDur,
                soundTrimStartMs = 0L,
                soundTrimEndMs = defaultEnd.coerceAtMost(rawDur),
                isDeviceAudio = isLocalDevice,
                audioImportError = null
            )
        }
    }

    fun updateSoundTrimRange(startMs: Long, endMs: Long) {
        val state = _uiState.value
        val maxDur = state.selectedSoundDurationMs.coerceAtLeast(1_000L)
        val minSpan = minOf(1_000L, maxDur)
        val clampedStart = startMs.coerceIn(0L, (maxDur - minSpan).coerceAtLeast(0L))
        val clampedEnd = endMs.coerceIn((clampedStart + minSpan).coerceAtMost(maxDur), maxDur)
        _uiState.update { it.copy(soundTrimStartMs = clampedStart, soundTrimEndMs = clampedEnd) }
    }

    fun importDeviceAudio(context: Context, uri: Uri) {
        if (_uiState.value.isImportingAudio) return
        _uiState.update {
            it.copy(
                isImportingAudio = true,
                audioImportProgress = 0.05f,
                audioImportLabel = "Starting audio import...",
                audioImportError = null
            )
        }
        viewModelScope.launch {
            val res = AudioImportManager.importAudioFromUri(context.applicationContext, uri) { pct, msg ->
                _uiState.update { it.copy(audioImportProgress = pct, audioImportLabel = msg) }
            }
            res.fold(
                onSuccess = { imported ->
                    _uiState.update {
                        it.copy(
                            isImportingAudio = false,
                            audioImportProgress = 1f,
                            audioImportLabel = "",
                            audioImportError = null
                        )
                    }
                    selectSound(imported.sound, imported.durationMs, isLocalDevice = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isImportingAudio = false,
                            audioImportProgress = 0f,
                            audioImportLabel = "",
                            audioImportError = err.message ?: "Failed to import audio file."
                        )
                    }
                }
            )
        }
    }

    fun removeSelectedSound() = selectSound(null)
    fun clearAudioImportError() = _uiState.update { it.copy(audioImportError = null) }

    // --- Video Selection & Timeline ---
    fun onVideoSelected(uri: Uri, durationMs: Long = 30_000L) {
        val safeDur = durationMs.coerceAtLeast(1500L)
        undoStack.clear()
        redoStack.clear()
        val initialSegment = VideoSegment(
            uriString = uri.toString(),
            sourceDurationMs = safeDur,
            trimStartMs = 0L,
            trimEndMs = safeDur,
            speed = _uiState.value.speedMultiplier
        )
        _uiState.update {
            it.copy(
                selectedVideoUri = uri,
                step = CreateStep.EDIT_MEDIA,
                activeToolPanel = CameraToolPanel.NONE,
                segments = listOf(initialSegment),
                selectedSegmentIndex = 0,
                playheadMs = 0L,
                videoDurationMs = safeDur,
                trimStartMs = 0L,
                trimEndMs = safeDur,
                coverFrameMs = 0L,
                canUndo = false,
                canRedo = false,
                uploadError = null,
                uploadSuccess = false,
                uploadProgress = 0f,
                uploadStatusText = "",
                pendingUploadKey = null,
                cachedExportUri = null,
                uploadedVideoUrl = null,
                uploadedThumbUrl = null,
                aiOutput = null,
                aiError = null
            )
        }
    }

    fun mergeVideoSegment(uri: Uri, durationMs: Long = 15_000L) {
        val safeDur = durationMs.coerceAtLeast(1000L)
        pushUndoSnapshot()
        val newSeg = VideoSegment(uriString = uri.toString(), sourceDurationMs = safeDur)
        _uiState.update { state ->
            val updated = state.segments + newSeg
            state.copy(segments = updated, selectedSegmentIndex = updated.lastIndex)
        }
    }

    fun selectSegment(index: Int) = _uiState.update { state ->
        if (state.segments.isEmpty()) state
        else state.copy(selectedSegmentIndex = index.coerceIn(0, state.segments.lastIndex))
    }

    fun updatePlayheadMs(ms: Long) = _uiState.update { state ->
        state.copy(playheadMs = ms.coerceIn(0L, state.totalTimelineDurationMs.coerceAtLeast(1000L)))
    }

    fun splitSegmentAtPlayhead() {
        val state = _uiState.value
        val segs = state.segments
        if (segs.isEmpty()) return
        var cursor = 0L
        var targetIdx = state.selectedSegmentIndex.coerceIn(0, segs.lastIndex)
        var offsetInSeg = (segs[targetIdx].trimEndMs - segs[targetIdx].trimStartMs) / 2L
        for (i in segs.indices) {
            val dur = segs[i].effectiveDurationMs
            if (state.playheadMs <= cursor + dur) {
                targetIdx = i
                offsetInSeg = ((state.playheadMs - cursor) * segs[i].speed).toLong()
                break
            }
            cursor += dur
        }
        val target = segs[targetIdx]
        val segSpan = target.trimEndMs - target.trimStartMs
        if (segSpan < 1000L) return
        val splitPoint = (target.trimStartMs + offsetInSeg).coerceIn(target.trimStartMs + 450L, target.trimEndMs - 450L)
        pushUndoSnapshot()
        val firstHalf = target.copy(id = UUID.randomUUID().toString(), trimEndMs = splitPoint)
        val secondHalf = target.copy(id = UUID.randomUUID().toString(), trimStartMs = splitPoint)
        val nextSegs = segs.toMutableList().apply {
            removeAt(targetIdx)
            add(targetIdx, secondHalf)
            add(targetIdx, firstHalf)
        }
        _uiState.update { it.copy(segments = nextSegs, selectedSegmentIndex = targetIdx) }
    }

    fun deleteSegment(index: Int) {
        val state = _uiState.value
        if (state.segments.size <= 1 || index !in state.segments.indices) return
        pushUndoSnapshot()
        val next = state.segments.toMutableList().apply { removeAt(index) }
        val first = next.first()
        _uiState.update {
            it.copy(
                segments = next,
                selectedSegmentIndex = index.coerceIn(0, next.lastIndex),
                selectedVideoUri = Uri.parse(first.uriString),
                trimStartMs = first.trimStartMs,
                trimEndMs = first.trimEndMs,
                videoDurationMs = first.sourceDurationMs
            )
        }
    }

    fun moveSegment(index: Int, delta: Int) {
        val state = _uiState.value
        val target = index + delta
        if (index !in state.segments.indices || target !in state.segments.indices) return
        pushUndoSnapshot()
        val mutable = state.segments.toMutableList()
        val moved = mutable.removeAt(index)
        mutable.add(target, moved)
        _uiState.update { it.copy(segments = mutable, selectedSegmentIndex = target) }
    }

    fun setSegmentTransition(index: Int, transition: VideoTransition) {
        val state = _uiState.value
        if (index !in state.segments.indices) return
        pushUndoSnapshot()
        val next = state.segments.mapIndexed { i, seg ->
            if (i == index) seg.copy(transitionAfter = transition) else seg
        }
        _uiState.update { it.copy(segments = next) }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        _uiState.update { state ->
            val segs = state.segments
            if (segs.isEmpty()) {
                val maxDur = state.videoDurationMs.coerceAtLeast(1000L)
                val safeStart = startMs.coerceIn(0L, (maxDur - 500L).coerceAtLeast(0L))
                val safeEnd = endMs.coerceIn((safeStart + 500L).coerceAtMost(maxDur), maxDur)
                return@update state.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
            }
            val idx = state.selectedSegmentIndex.coerceIn(0, segs.lastIndex)
            val seg = segs[idx]
            val maxDur = seg.sourceDurationMs.coerceAtLeast(1000L)
            val safeStart = startMs.coerceIn(0L, (maxDur - 450L).coerceAtLeast(0L))
            val safeEnd = endMs.coerceIn((safeStart + 450L).coerceAtMost(maxDur), maxDur)
            val nextSegs = segs.mapIndexed { i, s -> if (i == idx) s.copy(trimStartMs = safeStart, trimEndMs = safeEnd) else s }
            state.copy(
                segments = nextSegs,
                trimStartMs = if (idx == 0) safeStart else state.trimStartMs,
                trimEndMs = if (idx == 0) safeEnd else state.trimEndMs
            )
        }
    }

    fun setSpeedMultiplier(speed: Float) {
        val safe = speed.coerceIn(0.3f, 3f)
        if (_uiState.value.step == CreateStep.EDIT_MEDIA) pushUndoSnapshot()
        _uiState.update { state ->
            val idx = state.selectedSegmentIndex
            val updatedSegs = state.segments.mapIndexed { i, seg ->
                if (i == idx || state.segments.size == 1) seg.copy(speed = safe) else seg
            }
            state.copy(speedMultiplier = safe, segments = updatedSegs)
        }
    }

    fun toggleReversePlayback() {
        pushUndoSnapshot()
        _uiState.update { it.copy(reversePlayback = !it.reversePlayback) }
    }

    fun rotate90Degrees() {
        pushUndoSnapshot()
        _uiState.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun setAspectRatioMode(ratio: String) {
        pushUndoSnapshot()
        _uiState.update { it.copy(aspectRatioMode = ratio) }
    }

    fun setZoomScale(scale: Float) = _uiState.update { it.copy(zoomScale = scale.coerceIn(0.6f, 2f)) }

    fun updateColorCorrection(transform: (ColorCorrectionState) -> ColorCorrectionState) =
        _uiState.update { it.copy(colorCorrection = transform(it.colorCorrection)) }

    fun resetColorCorrection() {
        pushUndoSnapshot()
        _uiState.update { it.copy(colorCorrection = ColorCorrectionState()) }
    }

    fun setChromaKeyEnabled(enabled: Boolean) {
        pushUndoSnapshot()
        _uiState.update { it.copy(chromaKeyEnabled = enabled) }
    }

    fun setChromaKeyColor(colorHex: Long) = _uiState.update { it.copy(chromaKeyColorHex = colorHex) }
    fun setChromaKeyTolerance(tol: Float) = _uiState.update { it.copy(chromaKeyTolerance = tol.coerceIn(0.1f, 0.8f)) }

    fun setPipVideoUri(uri: Uri?) {
        pushUndoSnapshot()
        _uiState.update { it.copy(pipVideoUri = uri) }
    }

    fun cyclePipCorner() = _uiState.update { it.copy(pipCornerIndex = (it.pipCornerIndex + 1) % 4) }

    // --- Overlays, Captions & Audio Mix ---
    fun addOverlay(
        text: String,
        isSticker: Boolean,
        colorArgb: Long = 0xFFFFFFFFL,
        positionIndex: Int = 1,
        animation: OverlayAnimation = OverlayAnimation.STATIC
    ) {
        val clean = text.trim()
        if (clean.isBlank()) return
        pushUndoSnapshot()
        _uiState.update { state ->
            state.copy(
                overlays = state.overlays + OverlayItem(
                    text = clean,
                    isSticker = isSticker,
                    colorArgb = colorArgb,
                    positionIndex = positionIndex,
                    animation = animation,
                    endMs = state.totalTimelineDurationMs.coerceAtLeast(3000L)
                )
            )
        }
    }

    fun cycleOverlayPosition(id: String) {
        pushUndoSnapshot()
        _uiState.update { state ->
            state.copy(overlays = state.overlays.map { if (it.id == id) it.copy(positionIndex = (it.positionIndex + 1) % 3) else it })
        }
    }

    fun cycleOverlayAnimation(id: String) {
        pushUndoSnapshot()
        val anims = OverlayAnimation.entries
        _uiState.update { state ->
            state.copy(
                overlays = state.overlays.map { item ->
                    if (item.id == id) item.copy(animation = anims[(item.animation.ordinal + 1) % anims.size]) else item
                }
            )
        }
    }

    fun removeOverlay(id: String) {
        pushUndoSnapshot()
        _uiState.update { state -> state.copy(overlays = state.overlays.filterNot { it.id == id }) }
    }

    fun updateCaptionText(id: String, newText: String) = _uiState.update { state ->
        state.copy(captions = state.captions.map { if (it.id == id) it.copy(text = newText) else it })
    }

    fun removeCaption(id: String) {
        pushUndoSnapshot()
        _uiState.update { state -> state.copy(captions = state.captions.filterNot { it.id == id }) }
    }

    fun updateAudioMix(transform: (AudioMixState) -> AudioMixState) =
        _uiState.update { it.copy(audioMix = transform(it.audioMix)) }

    fun setRecordingVoiceOver(recording: Boolean) =
        _uiState.update { it.copy(isRecordingVoiceOver = recording) }

    fun updateCoverFrameMs(ms: Long) = _uiState.update { state ->
        state.copy(coverFrameMs = ms.coerceIn(0L, state.videoDurationMs))
    }

    // --- Draft Save / Recovery ---
    fun saveProjectDraft() {
        val s = _uiState.value
        val uriStr = s.selectedVideoUri?.toString() ?: return
        val dto = SavedProjectDraft(
            primaryUriString = uriStr,
            segments = s.segments.map {
                SavedSegmentDto(it.id, it.uriString, it.sourceDurationMs, it.trimStartMs, it.trimEndMs, it.speed, it.transitionAfter.name)
            },
            overlays = s.overlays.map {
                SavedOverlayDto(it.id, it.text, it.isSticker, it.colorArgb, it.positionIndex, it.animation.name, it.startMs, it.endMs)
            },
            captions = s.captions.map { SavedCaptionDto(it.id, it.startMs, it.endMs, it.text) },
            selectedFilter = s.selectedFilter,
            filterIntensity = s.filterIntensity,
            brightness = s.colorCorrection.brightness,
            contrast = s.colorCorrection.contrast,
            saturation = s.colorCorrection.saturation,
            warmth = s.colorCorrection.warmth,
            speedMultiplier = s.speedMultiplier,
            reversePlayback = s.reversePlayback,
            rotationDegrees = s.rotationDegrees,
            aspectRatioMode = s.aspectRatioMode,
            zoomScale = s.zoomScale,
            title = s.title,
            caption = s.caption,
            savedAtEpochMs = System.currentTimeMillis()
        )
        viewModelScope.launch {
            runCatching { repository.saveStudioDraftJson(json.encodeToString(dto)) }
            _uiState.update { it.copy(hasRecoverableDraft = true, draftSavedNotice = "Draft saved locally") }
        }
    }

    fun recoverSavedDraft() {
        viewModelScope.launch {
            val raw = runCatching { repository.getSavedStudioDraftJson() }.getOrNull() ?: return@launch
            val dto = runCatching { json.decodeFromString<SavedProjectDraft>(raw) }.getOrNull() ?: return@launch
            val restoredSegments = dto.segments.map { seg ->
                VideoSegment(
                    id = seg.id,
                    uriString = seg.uriString,
                    sourceDurationMs = seg.sourceDurationMs,
                    trimStartMs = seg.trimStartMs,
                    trimEndMs = seg.trimEndMs,
                    speed = seg.speed,
                    transitionAfter = VideoTransition.entries.firstOrNull { it.name == seg.transitionName } ?: VideoTransition.CUT
                )
            }.ifEmpty { listOf(VideoSegment(uriString = dto.primaryUriString, sourceDurationMs = 30_000L)) }
            val restoredOverlays = dto.overlays.map { ov ->
                OverlayItem(
                    id = ov.id,
                    text = ov.text,
                    isSticker = ov.isSticker,
                    colorArgb = ov.colorArgb,
                    positionIndex = ov.positionIndex,
                    animation = OverlayAnimation.entries.firstOrNull { it.name == ov.animationName } ?: OverlayAnimation.STATIC,
                    startMs = ov.startMs,
                    endMs = ov.endMs
                )
            }
            val restoredCaptions = dto.captions.map { AiCaptionCue(it.id, it.startMs, it.endMs, it.text) }
            val first = restoredSegments.first()
            _uiState.update {
                it.copy(
                    step = CreateStep.EDIT_MEDIA,
                    selectedVideoUri = Uri.parse(dto.primaryUriString),
                    segments = restoredSegments,
                    selectedSegmentIndex = 0,
                    videoDurationMs = first.sourceDurationMs,
                    trimStartMs = first.trimStartMs,
                    trimEndMs = first.trimEndMs,
                    overlays = restoredOverlays,
                    captions = restoredCaptions,
                    selectedFilter = dto.selectedFilter,
                    filterIntensity = dto.filterIntensity,
                    colorCorrection = ColorCorrectionState(dto.brightness, dto.contrast, dto.saturation, 0.25f, dto.warmth),
                    speedMultiplier = dto.speedMultiplier,
                    reversePlayback = dto.reversePlayback,
                    rotationDegrees = dto.rotationDegrees,
                    aspectRatioMode = dto.aspectRatioMode,
                    zoomScale = dto.zoomScale,
                    title = dto.title,
                    caption = dto.caption,
                    hasRecoverableDraft = true,
                    draftSavedNotice = "Draft restored"
                )
            }
        }
    }

    fun clearDraftNotice() = _uiState.update { it.copy(draftSavedNotice = null) }

    // --- AI-Powered Effects ---
    fun runAiEffect(contentResolver: ContentResolver, effectType: AiEffectType, stylePrompt: String = "") {
        val state = _uiState.value
        val uri = state.selectedVideoUri ?: return
        if (state.isProcessingAi) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingAi = true,
                    aiProgress = 0.08f,
                    aiProgressLabel = "Starting ${effectType.title}...",
                    aiError = null,
                    lastAiEffectType = effectType
                )
            }
            val res = aiRepository.processVideoAiEffect(
                contentResolver = contentResolver,
                videoUri = uri,
                durationMs = state.totalTimelineDurationMs,
                effectType = effectType,
                stylePrompt = stylePrompt,
                onProgress = { pct, label ->
                    _uiState.update { it.copy(aiProgress = pct, aiProgressLabel = label) }
                }
            )
            res.fold(
                onSuccess = { out ->
                    _uiState.update { it.copy(isProcessingAi = false, aiProgress = 1f, aiOutput = out, aiError = null) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessingAi = false, aiError = err.message ?: "AI job failed. Retry.") }
                }
            )
        }
    }

    fun applyAiOutputToStudio() {
        val out = _uiState.value.aiOutput ?: return
        pushUndoSnapshot()
        _uiState.update { state ->
            var next = state
            out.recommendedFilter?.let { next = next.copy(selectedFilter = it, filterIntensity = 0.9f) }
            if (out.generatedCaptions.isNotEmpty()) {
                next = next.copy(captions = out.generatedCaptions)
            }
            out.recommendedTemplate?.let { tpl ->
                val tr = VideoTransition.entries.firstOrNull { it.label.equals(tpl.transitionName, true) } ?: VideoTransition.CROSSFADE
                next = next.copy(
                    selectedFilter = tpl.filterName,
                    speedMultiplier = tpl.speedMultiplier,
                    segments = next.segments.map { it.copy(transitionAfter = tr, speed = tpl.speedMultiplier) },
                    overlays = next.overlays + OverlayItem(text = tpl.titleOverlay, animation = OverlayAnimation.POP, positionIndex = 0)
                )
            }
            if (out.detectedSceneTimestampsMs.isNotEmpty() && next.segments.size == 1) {
                val base = next.segments.first()
                val cuts = out.detectedSceneTimestampsMs.filter { it > base.trimStartMs + 500L && it < base.trimEndMs - 500L }.sorted()
                if (cuts.isNotEmpty()) {
                    val splitSegs = mutableListOf<VideoSegment>()
                    var prev = base.trimStartMs
                    cuts.forEach { cut ->
                        splitSegs.add(base.copy(id = UUID.randomUUID().toString(), trimStartMs = prev, trimEndMs = cut))
                        prev = cut
                    }
                    splitSegs.add(base.copy(id = UUID.randomUUID().toString(), trimStartMs = prev, trimEndMs = base.trimEndMs))
                    next = next.copy(segments = splitSegs, selectedSegmentIndex = 0)
                }
            }
            next.copy(draftSavedNotice = "${out.effectType.title} applied to timeline")
        }
    }

    fun clearAiError() = _uiState.update { it.copy(aiError = null) }

    // --- Navigation & Publish ---
    fun backToCapture() = _uiState.update { it.copy(step = CreateStep.CAPTURE, uploadError = null) }
    fun resumeDraftEdit() = _uiState.update { state ->
        if (state.selectedVideoUri != null) state.copy(step = CreateStep.EDIT_MEDIA, uploadError = null) else state
    }

    fun discardDraftMedia() {
        undoStack.clear()
        redoStack.clear()
        viewModelScope.launch { runCatching { repository.clearSavedStudioDraft() } }
        _uiState.update {
            it.copy(
                step = CreateStep.CAPTURE,
                selectedVideoUri = null,
                segments = emptyList(),
                overlays = emptyList(),
                captions = emptyList(),
                coverFrameMs = 0L,
                hasRecoverableDraft = false,
                canUndo = false,
                canRedo = false,
                uploadError = null,
                uploadProgress = 0f,
                uploadStatusText = "",
                pendingUploadKey = null,
                cachedExportUri = null,
                uploadedVideoUrl = null,
                uploadedThumbUrl = null
            )
        }
    }

    fun proceedToPublish() = _uiState.update { state ->
        if (state.selectedVideoUri != null) state.copy(step = CreateStep.PUBLISH_MEDIA, uploadError = null) else state
    }

    fun backToEdit() = _uiState.update { state ->
        if (state.selectedVideoUri != null) {
            state.copy(
                step = CreateStep.EDIT_MEDIA,
                uploadError = null,
                uploadProgress = 0f,
                uploadStatusText = "",
                pendingUploadKey = null,
                cachedExportUri = null,
                uploadedVideoUrl = null,
                uploadedThumbUrl = null
            )
        } else {
            state.copy(step = CreateStep.CAPTURE)
        }
    }

    fun updateTitle(v: String) = _uiState.update { it.copy(title = v) }
    fun updateCaption(v: String) = _uiState.update { it.copy(caption = v) }
    fun appendHashtag(tag: String) {
        val normalized = if (tag.startsWith("#")) tag else "#$tag"
        _uiState.update { state ->
            if (state.caption.contains(normalized, ignoreCase = true)) state
            else {
                val sp = if (state.caption.isBlank() || state.caption.endsWith(" ")) "$normalized " else " $normalized "
                state.copy(caption = state.caption + sp)
            }
        }
    }

    fun updateLocation(v: String) = _uiState.update { it.copy(location = v) }
    fun updateCategory(v: String) = _uiState.update { it.copy(category = v) }
    fun updatePrivacy(v: String) = _uiState.update { it.copy(privacy = v) }

    fun publish(context: Context, onCompleted: (VideoItem) -> Unit) {
        val state = _uiState.value
        val uri = state.selectedVideoUri ?: return
        if (state.isUploading || state.isCheckingSafety || state.isExporting) return

        val stableUploadKey = state.pendingUploadKey ?: UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                pendingUploadKey = stableUploadKey,
                uploadError = null,
                uploadStatusText = if (state.cachedExportUri == null) "Preparing video..." else "Resuming upload..."
            )
        }

        viewModelScope.launch {
            val exportedUri = state.cachedExportUri ?: run {
                _uiState.update { it.copy(isExporting = true, exportProgress = 0.05f) }
                val exportReq = VideoExportRequest(
                    primaryUri = uri,
                    segments = state.segments.map {
                        VideoSegmentExportSpec(
                            uri = Uri.parse(it.uriString),
                            trimStartMs = it.trimStartMs,
                            trimEndMs = it.trimEndMs,
                            speed = it.speed,
                            volume = state.audioMix.originalVolume,
                            muteAudio = state.audioMix.originalVolume <= 0.02f
                        )
                    },
                    filterName = state.selectedFilter,
                    filterIntensity = state.filterIntensity,
                    brightness = state.colorCorrection.brightness,
                    contrast = state.colorCorrection.contrast,
                    saturation = state.colorCorrection.saturation,
                    warmth = state.colorCorrection.warmth,
                    rotationDegrees = state.rotationDegrees,
                    aspectRatioMode = state.aspectRatioMode,
                    reversePlayback = state.reversePlayback,
                    pipUri = state.pipVideoUri,
                    voiceOverPath = state.audioMix.voiceOverPath,
                    musicUri = state.selectedSound?.audioUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                    musicTrimStartMs = state.soundTrimStartMs,
                    musicTrimEndMs = state.soundTrimEndMs,
                    musicVolume = state.audioMix.musicVolume
                )
                VideoExportEngine.exportVideo(context, exportReq) { p ->
                    _uiState.update { it.copy(exportProgress = p) }
                }.getOrDefault(uri)
            }

            _uiState.update {
                it.copy(
                    cachedExportUri = exportedUri,
                    isExporting = false,
                    isCheckingSafety = true,
                    uploadProgress = 0.02f,
                    uploadStatusText = "Checking community safety..."
                )
            }
            val contentResolver = context.contentResolver
            val moderation = VideoContentModerator.moderateVideo(
                contentResolver = contentResolver,
                videoUri = exportedUri,
                title = state.title,
                caption = state.caption,
                location = state.location,
                durationMs = state.totalTimelineDurationMs
            )
            if (moderation is ModerationResult.Blocked) {
                _uiState.update {
                    it.copy(
                        isCheckingSafety = false,
                        isUploading = false,
                        uploadStatusText = "",
                        uploadError = moderation.reason
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isCheckingSafety = false,
                    isUploading = true,
                    uploadProgress = 0.05f,
                    uploadStatusText = "Starting upload..."
                )
            }
            val overlaySummary = (state.overlays.map { it.text } + state.captions.map { it.text }).joinToString(" | ")
            val latestUrls = _uiState.value
            val result = repository.publishVideo(
                contentResolver = contentResolver,
                videoUri = exportedUri,
                title = state.title,
                caption = state.caption,
                category = state.category,
                location = state.location,
                privacy = state.privacy,
                sound = state.selectedSound,
                filterName = state.selectedFilter,
                trimStartMs = state.trimStartMs,
                trimEndMs = state.trimEndMs,
                durationMs = state.totalTimelineDurationMs,
                isPhoto = false,
                coverFrameMs = state.coverFrameMs,
                overlayText = overlaySummary,
                uploadKey = stableUploadKey,
                existingVideoUrl = latestUrls.uploadedVideoUrl,
                existingThumbUrl = latestUrls.uploadedThumbUrl,
                onStageUrlsResolved = { vidUrl, thumbUrl ->
                    _uiState.update {
                        it.copy(
                            uploadedVideoUrl = vidUrl.ifBlank { it.uploadedVideoUrl },
                            uploadedThumbUrl = thumbUrl.ifBlank { it.uploadedThumbUrl }
                        )
                    }
                },
                onStatusUpdate = { status ->
                    _uiState.update { it.copy(uploadStatusText = status) }
                },
                onProgress = { pct -> _uiState.update { it.copy(uploadProgress = pct) } }
            )
            result.fold(
                onSuccess = { publishedVideo ->
                    runCatching { repository.clearSavedStudioDraft() }
                    undoStack.clear()
                    redoStack.clear()
                    _uiState.update {
                        CreateUiState(
                            availableSounds = it.availableSounds,
                            favoriteFilterIds = it.favoriteFilterIds,
                            downloadedFilterIds = it.downloadedFilterIds,
                            uploadSuccess = true
                        )
                    }
                    onCompleted(publishedVideo)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadStatusText = "",
                            uploadError = err.message ?: "Upload failed. Check your connection and tap Retry."
                        )
                    }
                }
            )
        }
    }

    fun resetSuccess() = _uiState.update { it.copy(uploadSuccess = false, uploadError = null) }
}
