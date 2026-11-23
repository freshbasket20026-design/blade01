package com.example.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class VoiceAudioUiState(
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val recordedFile: File? = null,
    val recordedDurationMs: Long = 0L,
    val activePlaybackId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val audioError: String? = null
)

class VoiceNoteController(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(VoiceAudioUiState())
    val state: StateFlow<VoiceAudioUiState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var activeRecordFile: File? = null
    private var recordingStartMs: Long = 0L
    private var recordingTicker: Job? = null

    private var player: MediaPlayer? = null
    private var playbackTicker: Job? = null

    fun startRecording(): Boolean {
        if (_state.value.isRecording) return false
        stopPlayback()
        clearRecordedPreview()

        return try {
            val outDir = File(appContext.cacheDir, "voice_notes").apply { mkdirs() }
            val outFile = File(outDir, "voice_${System.currentTimeMillis()}.m4a")

            @Suppress("DEPRECATION")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setMaxDuration(180_000)
                setOutputFile(outFile.absolutePath)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording()
                    }
                }
                prepare()
                start()
            }

            recorder = mediaRecorder
            activeRecordFile = outFile
            recordingStartMs = System.currentTimeMillis()

            _state.update {
                it.copy(
                    isRecording = true,
                    recordingDurationMs = 0L,
                    recordedFile = null,
                    recordedDurationMs = 0L,
                    audioError = null
                )
            }

            recordingTicker?.cancel()
            recordingTicker = scope.launch {
                while (isActive && _state.value.isRecording) {
                    val elapsed = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(0L)
                    _state.update { it.copy(recordingDurationMs = elapsed) }
                    delay(100L)
                }
            }
            true
        } catch (e: Exception) {
            cleanupRecorder()
            _state.update {
                it.copy(
                    isRecording = false,
                    audioError = "Microphone unavailable: ${e.localizedMessage ?: "error"}"
                )
            }
            false
        }
    }

    fun stopRecording(): File? {
        if (!_state.value.isRecording) return _state.value.recordedFile
        recordingTicker?.cancel()
        recordingTicker = null

        val elapsed = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(0L)
        val file = activeRecordFile
        val stoppedOk = runCatching {
            recorder?.stop()
            true
        }.getOrDefault(false)
        cleanupRecorder()

        if (!stoppedOk || file == null || !file.exists() || file.length() <= 0L || elapsed < 350L) {
            runCatching { file?.delete() }
            _state.update {
                it.copy(
                    isRecording = false,
                    recordingDurationMs = 0L,
                    recordedFile = null,
                    recordedDurationMs = 0L,
                    audioError = if (elapsed < 350L) "Hold or record a little longer to capture voice" else "Recording failed"
                )
            }
            return null
        }

        _state.update {
            it.copy(
                isRecording = false,
                recordingDurationMs = 0L,
                recordedFile = file,
                recordedDurationMs = elapsed,
                audioError = null
            )
        }
        return file
    }

    fun cancelRecording() {
        recordingTicker?.cancel()
        recordingTicker = null
        runCatching { recorder?.stop() }
        cleanupRecorder()
        runCatching { activeRecordFile?.delete() }
        activeRecordFile = null
        clearRecordedPreview()
        _state.update {
            it.copy(isRecording = false, recordingDurationMs = 0L, audioError = null)
        }
    }

    fun clearRecordedPreview(deleteFile: Boolean = true) {
        if (_state.value.activePlaybackId == "preview_draft") {
            stopPlayback()
        }
        val prev = _state.value.recordedFile
        if (deleteFile && prev != null) {
            runCatching { prev.delete() }
        }
        _state.update {
            it.copy(recordedFile = null, recordedDurationMs = 0L)
        }
    }

    fun consumeRecordedFileForSend(): Pair<File, Long>? {
        val current = _state.value
        val file = if (current.isRecording) stopRecording() else current.recordedFile
        val duration = _state.value.recordedDurationMs
        if (file == null || !file.exists()) return null
        if (_state.value.activePlaybackId == "preview_draft") {
            stopPlayback()
        }
        _state.update { it.copy(recordedFile = null, recordedDurationMs = 0L) }
        return file to duration
    }

    fun togglePlayback(messageId: String, source: String, knownDurationMs: Long = 0L) {
        if (source.isBlank()) return
        val cur = _state.value
        if (cur.activePlaybackId == messageId && player != null) {
            if (cur.isPlaying) {
                runCatching { player?.pause() }
                playbackTicker?.cancel()
                _state.update { it.copy(isPlaying = false) }
            } else {
                runCatching { player?.start() }
                _state.update { it.copy(isPlaying = true) }
                startPlaybackTicker()
            }
            return
        }

        stopPlayback()
        _state.update {
            it.copy(
                activePlaybackId = messageId,
                isPlaying = false,
                isBuffering = true,
                playbackPositionMs = 0L,
                playbackDurationMs = knownDurationMs.coerceAtLeast(0L),
                audioError = null
            )
        }

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(source)
                setOnPreparedListener { readyPlayer ->
                    val realDuration = readyPlayer.duration.takeIf { d -> d > 0 }?.toLong() ?: knownDurationMs
                    readyPlayer.start()
                    _state.update {
                        it.copy(
                            isBuffering = false,
                            isPlaying = true,
                            playbackDurationMs = realDuration.coerceAtLeast(1L),
                            playbackPositionMs = 0L
                        )
                    }
                    startPlaybackTicker()
                }
                setOnCompletionListener {
                    playbackTicker?.cancel()
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            playbackPositionMs = 0L
                        )
                    }
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    _state.update { it.copy(audioError = "Unable to play voice message") }
                    true
                }
                prepareAsync()
            }
            player = mp
        } catch (e: Exception) {
            stopPlayback()
            _state.update {
                it.copy(audioError = "Audio playback failed: ${e.localizedMessage ?: "error"}")
            }
        }
    }

    fun seekTo(fraction: Float) {
        val mp = player ?: return
        val dur = _state.value.playbackDurationMs
        if (dur <= 0L) return
        val targetMs = (fraction.coerceIn(0f, 1f) * dur).toInt()
        runCatching {
            mp.seekTo(targetMs)
            _state.update { it.copy(playbackPositionMs = targetMs.toLong()) }
        }
    }

    fun stopPlayback() {
        playbackTicker?.cancel()
        playbackTicker = null
        runCatching {
            player?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        }
        player = null
        _state.update {
            it.copy(
                activePlaybackId = null,
                isPlaying = false,
                isBuffering = false,
                playbackPositionMs = 0L,
                playbackDurationMs = 0L
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(audioError = null) }
    }

    fun release() {
        cancelRecording()
        stopPlayback()
    }

    private fun startPlaybackTicker() {
        playbackTicker?.cancel()
        playbackTicker = scope.launch {
            while (isActive && _state.value.isPlaying) {
                val pos = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L
                val dur = runCatching { player?.duration?.toLong() }.getOrNull()
                    ?.takeIf { it > 0L } ?: _state.value.playbackDurationMs
                _state.update {
                    it.copy(
                        playbackPositionMs = pos.coerceIn(0L, dur.coerceAtLeast(1L)),
                        playbackDurationMs = dur
                    )
                }
                delay(80L)
            }
        }
    }

    private fun cleanupRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }
}
