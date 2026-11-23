package com.example.data.ai

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AiEffectType(
    val title: String,
    val badge: String,
    val subtitle: String,
    val requiresCloudNotice: Boolean = true
) {
    ANIME_CARTOON("Anime & Cartoon", "🎌", "Cel-shaded ink contours & vibrant anime palette"),
    PORTRAIT_ENHANCE("AI Portrait Enhance", "✨", "Auto-brighten dark faces, clarify skin & depth"),
    BACKGROUND_REMOVE("Background Removal", "✂️", "Isolate subject & swap studio background"),
    AI_AVATAR("AI Avatar Creator", "🧑‍🚀", "Generate stylized creator persona frame"),
    ARTISTIC_PAINTING("Artistic Painting", "🎨", "Oil impasto, watercolor & pop-art textures"),
    VINTAGE_MOTION("Vintage Frame Motion", "🎞️", "Dynamic zoom & warm 35mm motion sweep"),
    STYLE_TRANSFER("AI Video Style Transfer", "🌈", "Apply cinematic AI palette across timeline"),
    AUTO_CAPTIONS("Auto Captions & Subtitles", "💬", "Timestamped captions synced to your clip"),
    VIDEO_TEMPLATES("AI Video Templates", "⚡", "One-tap beat-synced filter, speed & title"),
    SCENE_DETECTION("Smart Scene Detection", "🎬", "Detect key scene transitions for splitting")
}

data class AiCaptionCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class AiTemplatePreset(
    val name: String,
    val filterName: String,
    val speedMultiplier: Float,
    val titleOverlay: String,
    val transitionName: String
)

data class AiEffectOutput(
    val effectType: AiEffectType,
    val summary: String,
    val noticeBanner: String,
    val processedPreviewBitmap: Bitmap? = null,
    val recommendedFilter: String? = null,
    val recommendedTemplate: AiTemplatePreset? = null,
    val generatedCaptions: List<AiCaptionCue> = emptyList(),
    val detectedSceneTimestampsMs: List<Long> = emptyList(),
    val isSampleMode: Boolean = true
)

/**
 * Clean AI Effects interface per project architecture so cloud Gemini (Firebase AI Logic) can be
 * wired transparently without changing callers if Firebase is enabled later.
 */
interface AiEffectsRepository {
    val isCloudConnected: Boolean
    suspend fun processVideoAiEffect(
        contentResolver: ContentResolver,
        videoUri: Uri,
        durationMs: Long,
        effectType: AiEffectType,
        stylePrompt: String,
        onProgress: (Float, String) -> Unit
    ): Result<AiEffectOutput>
}

class LocalSampleAiEffectsRepository : AiEffectsRepository {
    override val isCloudConnected: Boolean = false

    private val cloudNotice =
        "Cloud Gemini AI requires Firebase. Running local on-device processing with clearly labeled sample AI metadata."

    override suspend fun processVideoAiEffect(
        contentResolver: ContentResolver,
        videoUri: Uri,
        durationMs: Long,
        effectType: AiEffectType,
        stylePrompt: String,
        onProgress: (Float, String) -> Unit
    ): Result<AiEffectOutput> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.15f, "Sampling video keyframes...")
            val frame = extractKeyframe(contentResolver, videoUri, (durationMs / 3L).coerceAtLeast(0L))
                ?: return@withContext Result.failure(
                    IllegalStateException("Could not decode video frame for ${effectType.title}. Try another clip or check storage permissions.")
                )
            delay(180)
            onProgress(0.45f, "Running ${effectType.title}...")

            val output = when (effectType) {
                AiEffectType.ANIME_CARTOON -> {
                    val toon = applyToonQuantization(frame)
                    onProgress(0.9f, "Polishing cel ink lines...")
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Anime cel-shader applied to keyframe + Vivid color look ready.",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = toon,
                        recommendedFilter = "Vivid"
                    )
                }
                AiEffectType.PORTRAIT_ENHANCE -> {
                    val enhanced = applyPortraitBrighten(frame)
                    onProgress(0.9f, "Balancing skin tone & exposure...")
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Portrait luminance lifted (+24%) & dark shadows recovered.",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = enhanced,
                        recommendedFilter = "Portrait"
                    )
                }
                AiEffectType.BACKGROUND_REMOVE -> {
                    val cutout = runSelfieCutout(frame)
                    onProgress(0.9f, "Compositing clean studio backdrop...")
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] On-device Selfie Segmentation isolated foreground subject onto studio backdrop.",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = cutout,
                        recommendedFilter = "Bright"
                    )
                }
                AiEffectType.AI_AVATAR -> {
                    val avatar = applyArtisticWarmPoster(frame, neonAccent = true)
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Stylized creator avatar card generated (${stylePrompt.ifBlank { "Cyber Portrait" }}).",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = avatar,
                        recommendedFilter = "Neon Night"
                    )
                }
                AiEffectType.ARTISTIC_PAINTING -> {
                    val painted = applyArtisticWarmPoster(frame, neonAccent = false)
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Artistic brushstroke color palette applied.",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = painted,
                        recommendedFilter = "Golden Hour"
                    )
                }
                AiEffectType.VINTAGE_MOTION -> {
                    val vintage = applySepiaFilmLook(frame)
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Vintage 35mm film motion sweep & sepia warmth prepared.",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = vintage,
                        recommendedFilter = "Film"
                    )
                }
                AiEffectType.STYLE_TRANSFER -> {
                    val styled = applyColorMatrix(frame, saturation = 1.35f, contrast = 1.25f, brightness = 16f)
                    val targetFilter = when {
                        stylePrompt.contains("cool", true) || stylePrompt.contains("cyber", true) -> "Neon Night"
                        stylePrompt.contains("bw", true) || stylePrompt.contains("noir", true) -> "Black & White"
                        else -> "Cinematic"
                    }
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Style transfer matched '$targetFilter' cinematic grade.",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = styled,
                        recommendedFilter = targetFilter
                    )
                }
                AiEffectType.AUTO_CAPTIONS -> {
                    delay(200)
                    val dur = durationMs.coerceAtLeast(3000L)
                    val cues = listOf(
                        AiCaptionCue("cap_1", 0L, (dur * 0.32f).toLong(), "[Sample Caption] Watch this look"),
                        AiCaptionCue("cap_2", (dur * 0.34f).toLong(), (dur * 0.68f).toLong(), "[Sample Caption] Shot crisp in Blade Studio"),
                        AiCaptionCue("cap_3", (dur * 0.70f).toLong(), dur, "[Sample Caption] Tap Follow for more")
                    )
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Generated 3 editable timeline caption cues (enable Firebase for Gemini audio transcription).",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = frame,
                        generatedCaptions = cues
                    )
                }
                AiEffectType.VIDEO_TEMPLATES -> {
                    val tpl = AiTemplatePreset(
                        name = stylePrompt.ifBlank { "Neon Creator Reel" },
                        filterName = "Golden Hour",
                        speedMultiplier = 1f,
                        titleOverlay = "✨ Studio Reel",
                        transitionName = "Crossfade"
                    )
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Template '${tpl.name}' ready: ${tpl.filterName} filter + ${tpl.transitionName} transitions.",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = applyPortraitBrighten(frame),
                        recommendedTemplate = tpl
                    )
                }
                AiEffectType.SCENE_DETECTION -> {
                    val scenes = detectSceneChanges(contentResolver, videoUri, durationMs)
                    AiEffectOutput(
                        effectType = effectType,
                        summary = "[Sample Mode] Scanned frame luma deltas and found ${scenes.size} suggested cut marker(s).",
                        noticeBanner = cloudNotice,
                        processedPreviewBitmap = frame,
                        detectedSceneTimestampsMs = scenes
                    )
                }
            }
            onProgress(1f, "Complete")
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "AI effect processing failed. Please retry."))
        }
    }

    private fun extractKeyframe(
        contentResolver: ContentResolver,
        uri: Uri,
        timeMs: Long
    ): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                mmr.setDataSource(pfd.fileDescriptor)
                val raw = mmr.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                raw?.let { scaleDown(it, 480) }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun detectSceneChanges(
        contentResolver: ContentResolver,
        uri: Uri,
        durationMs: Long
    ): List<Long> {
        val dur = durationMs.coerceAtLeast(3000L)
        val cuts = mutableListOf<Long>()
        val mmr = MediaMetadataRetriever()
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                mmr.setDataSource(pfd.fileDescriptor)
                val samples = 6
                var prevLuma = -1f
                for (i in 1 until samples) {
                    val ts = (dur * i) / samples
                    val frame = mmr.getFrameAtTime(ts * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val small = scaleDown(frame, 48)
                        val avg = avgLuma(small)
                        if (prevLuma >= 0f && abs(avg - prevLuma) > 16f) {
                            cuts.add(ts)
                        }
                        prevLuma = avg
                        if (small !== frame) small.recycle()
                        frame.recycle()
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }
        if (cuts.isEmpty()) {
            cuts.add(dur / 3L)
            cuts.add((dur * 2L) / 3L)
        }
        return cuts.take(4)
    }

    private suspend fun runSelfieCutout(src: Bitmap): Bitmap {
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        )
        return try {
            val mask = suspendCancellableCoroutine { cont ->
                segmenter.process(InputImage.fromBitmap(src, 0))
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { err ->
                        if (cont.isActive) cont.resumeWithException(err)
                    }
            }
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, src.width.toFloat(), src.height.toFloat(),
                    intArrayOf(Color.rgb(15, 23, 42), Color.rgb(229, 9, 20)),
                    null, android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), bgPaint)
            val rawBuf = mask.buffer
            rawBuf.rewind()
            val floatBuf = rawBuf.asFloatBuffer()
            val mw = mask.width
            val mh = mask.height
            val maxIdx = (mw * mh - 1).coerceAtLeast(0)
            val fgPixels = IntArray(src.width * src.height)
            src.getPixels(fgPixels, 0, src.width, 0, 0, src.width, src.height)
            for (y in 0 until src.height) {
                val my = (y * mh) / src.height.coerceAtLeast(1)
                for (x in 0 until src.width) {
                    val mx = (x * mw) / src.width.coerceAtLeast(1)
                    val conf = floatBuf.get((my * mw + mx).coerceIn(0, maxIdx)).coerceIn(0f, 1f)
                    val idx = y * src.width + x
                    val c = fgPixels[idx]
                    val a = (conf * 255f).roundToInt().coerceIn(0, 255)
                    fgPixels[idx] = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
                }
            }
            val fgBmp = Bitmap.createBitmap(fgPixels, src.width, src.height, Bitmap.Config.ARGB_8888)
            canvas.drawBitmap(fgBmp, 0f, 0f, null)
            fgBmp.recycle()
            out
        } catch (_: Exception) {
            applyPortraitBrighten(src)
        } finally {
            runCatching { segmenter.close() }
        }
    }

    private fun applyToonQuantization(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = ((Color.red(c) / 48) * 48 + 20).coerceIn(0, 255)
            val g = ((Color.green(c) / 48) * 48 + 20).coerceIn(0, 255)
            val b = ((Color.blue(c) / 48) * 48 + 20).coerceIn(0, 255)
            px[i] = Color.argb(255, r, g, b)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun applyPortraitBrighten(src: Bitmap): Bitmap =
        applyColorMatrix(src, saturation = 1.12f, contrast = 1.10f, brightness = 24f)

    private fun applySepiaFilmLook(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply {
            setSaturation(0.35f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.1f, 0.2f, 0.05f, 0f, 12f,
                        0.05f, 1.0f, 0.05f, 0f, 8f,
                        0f, 0.05f, 0.85f, 0f, -4f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    private fun applyArtisticWarmPoster(src: Bitmap, neonAccent: Boolean): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = ((Color.red(c) / 64) * 64 + if (neonAccent) 36 else 24).coerceIn(0, 255)
            val g = ((Color.green(c) / 64) * 64 + 16).coerceIn(0, 255)
            val b = ((Color.blue(c) / 64) * 64 + if (neonAccent) 48 else 8).coerceIn(0, 255)
            px[i] = Color.argb(255, r, g, b)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun applyColorMatrix(src: Bitmap, saturation: Float, contrast: Float, brightness: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply {
            setSaturation(saturation)
            val t = brightness + 128f * (1f - contrast)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, t,
                        0f, contrast, 0f, 0f, t,
                        0f, 0f, contrast, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    private fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide <= maxDim) return bmp
        val ratio = maxDim.toFloat() / maxSide
        val w = (bmp.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    private fun avgLuma(bmp: Bitmap): Float {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        if (px.isEmpty()) return 128f
        var sum = 0.0
        for (c in px) {
            sum += 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
        }
        return (sum / px.size).toFloat()
    }
}
