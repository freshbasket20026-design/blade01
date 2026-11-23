package com.example.core.moderation

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

sealed interface ModerationResult {
    data object Safe : ModerationResult
    data class Blocked(val reason: String) : ModerationResult
}

data class FrameSkinMetrics(
    val totalSkinRatio: Float,
    val centerTorsoSkinRatio: Float,
    val largestClusterRatio: Float
) {
    val isExplicitCandidate: Boolean
        get() = totalSkinRatio >= 0.52f &&
            centerTorsoSkinRatio >= 0.58f &&
            largestClusterRatio >= 0.45f
}

object VideoContentModerator {

    const val BLOCKED_MESSAGE =
        "Upload blocked: Pornographic, sexually explicit, or adult content is strictly prohibited on Blade."

    private val exactWordKeywords = setOf(
        "porn", "porno", "pornography", "pornographic", "pornhub", "xvideos", "xnxx", "xhamster",
        "redtube", "youporn", "brazzers", "onlyfans", "fansly", "nsfw", "xxx", "xxxx",
        "hentai", "ecchi", "erotic", "erotica", "fetish", "bdsm", "bondage", "dominatrix",
        "nude", "nudes", "nudity", "naked", "topless", "stripper", "striptease", "camgirl",
        "escort", "brothel", "prostitute", "fuck", "fucking", "fucker", "motherfucker",
        "cock", "dick", "pussy", "vagina", "penis", "boobs", "tits", "titties", "nipple",
        "nipples", "blowjob", "handjob", "footjob", "rimjob", "cumshot", "creampie",
        "orgasm", "masturbate", "masturbation", "dildo", "vibrator", "gangbang", "threesome",
        "orgy", "incest", "milf", "gilf", "anal", "buttplug", "sexvideo", "sextape",
        "sexclip", "hotsex", "sexyvideo", "bluefilm", "chuda", "chudi", "choda", "chodi",
        "magi", "khanki", "nangta", "nengta", "beshya", "noti", "randi", "bhabhi", "desi"
    )

    // Words that are only blocked when paired with adult/video context or explicit hashtags
    private val contextSensitivePhrases = listOf(
        "18+", "18 plus", "adult only", "adult video", "sex video", "sex clip",
        "sexy video", "hot video", "hot girl nude", "live sex", "cam sex",
        "blue film", "desisex", "bangla choda", "bangla sex", "indian sex",
        "only fans", "leak video", "mms video", "viral sex",
        "পর্ন", "সেক্স", "চোদা", "চুদি", "নগ্ন", "নেংটা", "ন্যুড", "ব্লু ফিল্ম"
    )

    // "desi" or "bhabhi" alone could be harmless food/family content; require explicit pairing
    private val benignAloneRequirePairing = setOf("desi", "bhabhi")

    fun checkMetadataSafety(
        title: String,
        caption: String,
        location: String,
        fileName: String? = null
    ): ModerationResult {
        val combinedRaw = listOfNotNull(title, caption, location, fileName)
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        if (combinedRaw.isBlank()) return ModerationResult.Safe

        // Check explicit phrase matches
        for (phrase in contextSensitivePhrases) {
            if (combinedRaw.contains(phrase)) {
                return ModerationResult.Blocked(BLOCKED_MESSAGE)
            }
        }

        // Tokenize words & hashtags (preserve alphanumeric + Bengali unicode block)
        val tokens = Regex("[a-z0-9_\\u0980-\\u09FF]+")
            .findAll(combinedRaw)
            .map { it.value }
            .toList()

        val strongWords = exactWordKeywords - benignAloneRequirePairing
        if (tokens.any { it in strongWords }) {
            return ModerationResult.Blocked(BLOCKED_MESSAGE)
        }

        // Compact alphanumeric normalization to catch obfuscated tokens like "p.o.r.n" or "x_x_x"
        val strippedTokens = combinedRaw
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-z0-9]"), "") }
        if (strippedTokens.any { it in strongWords }) {
            return ModerationResult.Blocked(BLOCKED_MESSAGE)
        }

        return ModerationResult.Safe
    }

    suspend fun moderateVideo(
        contentResolver: ContentResolver,
        videoUri: Uri,
        title: String,
        caption: String,
        location: String,
        durationMs: Long
    ): ModerationResult = withContext(Dispatchers.IO) {
        val fileName = resolveDisplayName(contentResolver, videoUri)
        val metaResult = checkMetadataSafety(
            title = title,
            caption = caption,
            location = location,
            fileName = fileName
        )
        if (metaResult is ModerationResult.Blocked) {
            return@withContext metaResult
        }

        val frameMetrics = sampleAndAnalyzeFrames(contentResolver, videoUri, durationMs)
        if (frameMetrics.isNotEmpty()) {
            val flaggedFrames = frameMetrics.count { it.isExplicitCandidate }
            val avgTotalSkin = frameMetrics.map { it.totalSkinRatio }.average().toFloat()
            val avgTorsoSkin = frameMetrics.map { it.centerTorsoSkinRatio }.average().toFloat()

            if (flaggedFrames >= 3 ||
                (flaggedFrames >= 2 && avgTotalSkin >= 0.58f && avgTorsoSkin >= 0.64f)
            ) {
                return@withContext ModerationResult.Blocked(BLOCKED_MESSAGE)
            }
        }

        ModerationResult.Safe
    }

    private fun resolveDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment
        }.getOrNull()
    }

    private fun sampleAndAnalyzeFrames(
        contentResolver: ContentResolver,
        videoUri: Uri,
        fallbackDurationMs: Long
    ): List<FrameSkinMetrics> {
        val retriever = MediaMetadataRetriever()
        val results = mutableListOf<FrameSkinMetrics>()
        try {
            contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val actualDurationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: fallbackDurationMs.coerceAtLeast(1000L)

                val fractions = listOf(0.08f, 0.28f, 0.50f, 0.72f, 0.90f)
                for (fraction in fractions) {
                    val timeUs = (actualDurationMs * fraction).toLong().coerceAtLeast(0L) * 1000L
                    val rawBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs)
                        ?: continue
                    val scaled = Bitmap.createScaledBitmap(rawBitmap, 96, 96, false)
                    if (scaled !== rawBitmap) {
                        rawBitmap.recycle()
                    }
                    results.add(analyzeFrameBitmap(scaled))
                    scaled.recycle()
                }
            }
        } catch (_: Exception) {
            // If codec/URI cannot be decoded on device, fallback cleanly to already-run metadata safety check
        } finally {
            runCatching { retriever.release() }
        }
        return results
    }

    fun analyzeFrameBitmap(bitmap: Bitmap): FrameSkinMetrics {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        if (totalPixels <= 0) return FrameSkinMetrics(0f, 0f, 0f)

        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val skinMask = BooleanArray(totalPixels)
        var skinCount = 0
        var torsoTotal = 0
        var torsoSkin = 0

        val rowMin = (height * 0.25f).toInt()
        val rowMax = (height * 0.88f).toInt()
        val colMin = (width * 0.15f).toInt()
        val colMax = (width * 0.85f).toInt()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val px = pixels[idx]
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)

                val isSkin = isSkinPixel(r, g, b)
                skinMask[idx] = isSkin
                if (isSkin) skinCount++

                if (y in rowMin..rowMax && x in colMin..colMax) {
                    torsoTotal++
                    if (isSkin) torsoSkin++
                }
            }
        }

        val largestCluster = findLargestConnectedSkinRegion(skinMask, width, height)
        return FrameSkinMetrics(
            totalSkinRatio = skinCount.toFloat() / totalPixels.toFloat(),
            centerTorsoSkinRatio = if (torsoTotal > 0) torsoSkin.toFloat() / torsoTotal.toFloat() else 0f,
            largestClusterRatio = largestCluster.toFloat() / totalPixels.toFloat()
        )
    }

    private fun isSkinPixel(r: Int, g: Int, b: Int): Boolean {
        if (r <= g || r <= b || abs(r - g) <= 12) return false
        val y = (0.299 * r + 0.587 * g + 0.114 * b)
        val cb = (128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b)
        val cr = (128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b)
        return y > 45.0 && cb in 77.0..132.0 && cr in 133.0..178.0
    }

    private fun findLargestConnectedSkinRegion(mask: BooleanArray, width: Int, height: Int): Int {
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var maxRegion = 0

        for (i in mask.indices) {
            if (!mask[i] || visited[i]) continue
            var head = 0
            var tail = 0
            queue[tail++] = i
            visited[i] = true
            var count = 0

            while (head < tail) {
                val cur = queue[head++]
                count++
                val cx = cur % width
                val cy = cur / width

                if (cx > 0) {
                    val left = cur - 1
                    if (mask[left] && !visited[left]) {
                        visited[left] = true
                        queue[tail++] = left
                    }
                }
                if (cx + 1 < width) {
                    val right = cur + 1
                    if (mask[right] && !visited[right]) {
                        visited[right] = true
                        queue[tail++] = right
                    }
                }
                if (cy > 0) {
                    val up = cur - width
                    if (mask[up] && !visited[up]) {
                        visited[up] = true
                        queue[tail++] = up
                    }
                }
                if (cy + 1 < height) {
                    val down = cur + width
                    if (mask[down] && !visited[down]) {
                        visited[down] = true
                        queue[tail++] = down
                    }
                }
            }
            if (count > maxRegion) maxRegion = count
        }
        return maxRegion
    }
}
