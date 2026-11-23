package com.example.core.camera

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.SoundItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImportedAudioResult(
    val sound: SoundItem,
    val localFile: File,
    val durationMs: Long,
    val mimeType: String
)

object AudioImportManager {

    private val supportedExtensions = setOf(
        "mp3", "m4a", "aac", "wav", "ogg", "oga", "flac", "opus", "3gp", "amr"
    )

    fun isSupportedAudio(mimeType: String?, fileName: String?): Boolean {
        val cleanMime = mimeType?.lowercase().orEmpty()
        if (cleanMime.startsWith("audio/")) return true
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in supportedExtensions
    }

    suspend fun importAudioFromUri(
        context: Context,
        sourceUri: Uri,
        onProgress: (Float, String) -> Unit
    ): Result<ImportedAudioResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val (displayName, sizeBytes) = queryFileMeta(resolver, sourceUri)
            val mimeType = resolver.getType(sourceUri) ?: guessMimeFromName(displayName)

            if (!isSupportedAudio(mimeType, displayName)) {
                error("Unsupported audio format. Choose MP3, M4A, AAC, WAV, or OGG.")
            }

            onProgress(0.08f, "Preparing $displayName...")

            val targetDir = File(context.filesDir, "imported_audio").apply { mkdirs() }
            // Keep imported cache clean of stale unused files older than 24h
            val cutoff = System.currentTimeMillis() - 86_400_000L
            targetDir.listFiles()?.forEach { old ->
                if (old.lastModified() < cutoff) runCatching { old.delete() }
            }

            val ext = resolveExtension(displayName, mimeType)
            val destFile = File(targetDir, "blade_audio_${System.currentTimeMillis()}.$ext")

            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var totalRead = 0L
                    var lastProgress = 0.08f
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        val nextProgress = if (sizeBytes > 0L) {
                            (0.10f + (totalRead.toFloat() / sizeBytes.toFloat()) * 0.78f).coerceIn(0.10f, 0.88f)
                        } else {
                            (lastProgress + 0.04f).coerceAtMost(0.85f)
                        }
                        if (nextProgress - lastProgress >= 0.05f) {
                            lastProgress = nextProgress
                            onProgress(nextProgress, "Importing $displayName (${(nextProgress * 100).toInt()}%)...")
                        }
                    }
                    output.flush()
                }
            } ?: error("Unable to open the selected audio file. Check file access.")

            if (!destFile.exists() || destFile.length() <= 0L) {
                runCatching { destFile.delete() }
                error("Selected audio file appears empty.")
            }

            onProgress(0.92f, "Reading audio duration & metadata...")
            val (durationMs, metaTitle, metaArtist) = extractAudioMetadata(destFile)
            val cleanTitle = metaTitle?.takeIf { it.isNotBlank() }
                ?: displayName.substringBeforeLast('.').ifBlank { "Phone Audio" }
            val cleanArtist = metaArtist?.takeIf { it.isNotBlank() } ?: "Phone Storage"
            val durationSec = ((durationMs / 1000L).toInt()).coerceAtLeast(1)

            val sound = SoundItem(
                id = "local_${destFile.nameWithoutExtension}",
                title = cleanTitle,
                artist = cleanArtist,
                audioUrl = Uri.fromFile(destFile).toString(),
                coverUrl = "",
                durationSeconds = durationSec,
                usageCount = 1
            )

            onProgress(1f, "Ready: $cleanTitle")
            ImportedAudioResult(
                sound = sound,
                localFile = destFile,
                durationMs = durationMs.coerceAtLeast(1_000L),
                mimeType = mimeType
            )
        }
    }

    private fun queryFileMeta(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio_track"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return name to size
    }

    private fun extractAudioMetadata(file: File): Triple<Long, String?, String?> {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 30_000L
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            Triple(dur.coerceAtLeast(1_000L), title, artist)
        } catch (_: Throwable) {
            Triple(30_000L, null, null)
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun resolveExtension(displayName: String, mimeType: String): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName in supportedExtensions) return fromName
        return when (mimeType.lowercase()) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
            "audio/aac", "audio/aac-adts" -> "aac"
            "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "wav"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/opus" -> "opus"
            else -> "m4a"
        }
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "audio/*"
        }
    }
}
