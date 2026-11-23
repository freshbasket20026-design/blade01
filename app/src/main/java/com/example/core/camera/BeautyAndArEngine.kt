package com.example.core.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

data class BeautySettings(
    val enabled: Boolean = true,
    val intensity: Float = 0.65f,
    val skinSmoothing: Float = 0.50f,
    val blemishReduction: Float = 0.40f,
    val eyeEnhancement: Float = 0.35f,
    val teethWhitening: Float = 0.30f,
    val faceLighting: Float = 0.55f,
    val brightnessBoost: Float = 0.38f, // Auto-lifts dark front camera exposures naturally
    val skinToneWarmth: Float = 0.16f,
    val lipstickIntensity: Float = 0f,
    val lipstickColorArgb: Long = 0xFFD94A64L,
    val blushIntensity: Float = 0f,
    val eyeshadowIntensity: Float = 0f,
    val eyebrowIntensity: Float = 0f,
    val faceContourIntensity: Float = 0f
) {
    val activeFactor: Float get() = if (enabled) intensity.coerceIn(0f, 1f) else 0f
    val needsFaceLandmarks: Boolean
        get() = enabled && activeFactor > 0.02f && (
            lipstickIntensity > 0.02f ||
                blushIntensity > 0.02f ||
                eyeshadowIntensity > 0.02f ||
                eyebrowIntensity > 0.02f ||
                faceContourIntensity > 0.02f
            )
}

enum class ArEffectType(
    val id: String,
    val label: String,
    val category: String,
    val badge: String,
    val description: String
) {
    NONE("ar_none", "None", "Trending", "🚫", "No AR face filter"),
    CAT_EARS("ar_cat", "Cat Ears", "Animal", "🐱", "Face-tracked kitty ears & whiskers"),
    PUPPY("ar_puppy", "Puppy", "Animal", "🐶", "Floppy dog ears & playful snout"),
    BUNNY("ar_bunny", "Bunny", "Animal", "🐰", "Soft bunny ears & pink nose"),
    BEAR("ar_bear", "Bear", "Animal", "🐻", "Cozy bear ears & warm cheeks"),
    BIG_EYES("ar_big_eyes", "Big Eyes", "Funny", "👀", "Expressive eye highlight & sparkle"),
    FUNNY_MORPH("ar_morph", "Funny Morph", "Funny", "🤪", "Comic cheeks & smile reaction meter"),
    NEON_SHADES("ar_shades", "Neon Shades", "Accessories", "🕶️", "Head-tilted glowing studio sunglasses"),
    CROWN("ar_crown", "Royal Crown", "Accessories", "👑", "Golden floating crown above head"),
    CYBER_MASK("ar_cyber", "Cyber Visor", "Accessories", "🤖", "Futuristic neon HUD face visor"),
    SPARKLE_HALO("ar_3d_halo", "3D Halo", "Trending", "✨", "Orbiting 3D stars & smile sparkles"),
    EMOJI_BURST("ar_emoji", "Emoji Pop", "Funny", "🥳", "Animated emoji aura tracking face")
}

data class TrackedFaceState(
    val detected: Boolean = false,
    val boundingBox: RectF = RectF(), // normalized 0..1
    val leftEye: PointF? = null,
    val rightEye: PointF? = null,
    val noseBase: PointF? = null,
    val mouthLeft: PointF? = null,
    val mouthRight: PointF? = null,
    val mouthBottom: PointF? = null,
    val leftCheek: PointF? = null,
    val rightCheek: PointF? = null,
    val headEulerAngleZ: Float = 0f,
    val smilingProbability: Float = 0f,
    val leftEyeOpenProbability: Float = 1f,
    val rightEyeOpenProbability: Float = 1f,
    val sourceAspectRatio: Float = 0.75f
)

class CameraMlAnalyzer(
    private val isFrontCameraProvider: () -> Boolean,
    private val isPreviewStreamingProvider: () -> Boolean,
    private val isRecordingProvider: () -> Boolean = { false },
    private val needsFaceTrackingProvider: () -> Boolean,
    private val needsSegmentationProvider: () -> Boolean,
    private val onFaceUpdated: (TrackedFaceState) -> Unit,
    private val onLumaUpdated: (Float) -> Unit,
    private val onMaskUpdated: (Bitmap?) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    private var faceDetectorRef: FaceDetector? = null

    @Volatile
    private var segmenterRef: Segmenter? = null

    private fun getOrCreateFaceDetector(): FaceDetector {
        faceDetectorRef?.let { return it }
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.20f)
            .enableTracking()
            .build()
        return FaceDetection.getClient(opts).also { faceDetectorRef = it }
    }

    private fun getOrCreateSegmenter(): Segmenter {
        segmenterRef?.let { return it }
        val opts = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
        return Segmentation.getClient(opts).also { segmenterRef = it }
    }

    private val processing = AtomicBoolean(false)
    private var frameCounter = 0

    @Volatile
    private var firstStreamingAtMs = 0L

    @Volatile
    private var lastMlProcessedAtMs = 0L

    @Volatile
    private var lastLumaProcessedAtMs = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !isPreviewStreamingProvider() || !processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (firstStreamingAtMs == 0L) {
            firstStreamingAtMs = now
        }

        frameCounter++
        if (now - lastLumaProcessedAtMs >= 1_200L) {
            lastLumaProcessedAtMs = now
            onLumaUpdated(estimateAverageLuma(imageProxy))
        }

        // Wait 320ms after first frame streams before warming ML Kit native detectors
        if (now - firstStreamingAtMs < 320L) {
            processing.set(false)
            imageProxy.close()
            return
        }

        // Strict throttle (160ms idle / 260ms recording) prevents ML Kit from starving preview & VideoCapture
        val minIntervalMs = if (isRecordingProvider()) 260L else 160L
        if (now - lastMlProcessedAtMs < minIntervalMs) {
            processing.set(false)
            imageProxy.close()
            return
        }

        val runFace = needsFaceTrackingProvider()
        val runSegmentation = needsSegmentationProvider() && (frameCounter % 2 == 0)
        if (!runFace && !runSegmentation) {
            if (!needsSegmentationProvider()) onMaskUpdated(null)
            processing.set(false)
            imageProxy.close()
            return
        }
        lastMlProcessedAtMs = now

        val rotation = imageProxy.imageInfo.rotationDegrees
        val isFront = isFrontCameraProvider()
        val rotatedW = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val rotatedH = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        val safeW = rotatedW.coerceAtLeast(1).toFloat()
        val safeH = rotatedH.coerceAtLeast(1).toFloat()
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        if (runFace) {
            getOrCreateFaceDetector().process(inputImage)
                .addOnSuccessListener { faces ->
                    val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    onFaceUpdated(toTrackedState(primary, safeW, safeH, isFront))
                }
                .addOnFailureListener {
                    onFaceUpdated(TrackedFaceState(detected = false))
                }
                .addOnCompleteListener {
                    if (runSegmentation) {
                        getOrCreateSegmenter().process(inputImage)
                            .addOnSuccessListener { mask ->
                                onMaskUpdated(maskToAlphaBitmap(mask.buffer, mask.width, mask.height, isFront))
                            }
                            .addOnCompleteListener {
                                processing.set(false)
                                imageProxy.close()
                            }
                    } else {
                        if (!needsSegmentationProvider()) onMaskUpdated(null)
                        processing.set(false)
                        imageProxy.close()
                    }
                }
        } else {
            getOrCreateSegmenter().process(inputImage)
                .addOnSuccessListener { mask ->
                    onMaskUpdated(maskToAlphaBitmap(mask.buffer, mask.width, mask.height, isFront))
                }
                .addOnCompleteListener {
                    processing.set(false)
                    imageProxy.close()
                }
        }
    }

    private fun estimateAverageLuma(image: ImageProxy): Float {
        return runCatching {
            val plane = image.planes.firstOrNull() ?: return 128f
            val buffer = plane.buffer
            buffer.rewind()
            val total = buffer.remaining()
            if (total <= 0) return 128f
            val step = (total / 96).coerceAtLeast(1)
            var sum = 0L
            var count = 0
            var i = 0
            while (i < total) {
                sum += (buffer.get(i).toInt() and 0xFF)
                count++
                i += step
            }
            if (count > 0) sum.toFloat() / count else 128f
        }.getOrDefault(128f)
    }

    private fun toTrackedState(
        face: Face?,
        w: Float,
        h: Float,
        mirrorX: Boolean
    ): TrackedFaceState {
        if (face == null) return TrackedFaceState(detected = false, sourceAspectRatio = w / h)

        fun normX(x: Float): Float {
            val n = (x / w).coerceIn(0f, 1f)
            return if (mirrorX) 1f - n else n
        }

        fun normY(y: Float): Float = (y / h).coerceIn(0f, 1f)

        fun landmarkPt(type: Int): PointF? = face.getLandmark(type)?.position?.let {
            PointF(normX(it.x), normY(it.y))
        }

        val leftX = normX(face.boundingBox.left.toFloat())
        val rightX = normX(face.boundingBox.right.toFloat())
        val box = RectF(
            minOf(leftX, rightX),
            normY(face.boundingBox.top.toFloat()),
            maxOf(leftX, rightX),
            normY(face.boundingBox.bottom.toFloat())
        )

        return TrackedFaceState(
            detected = true,
            boundingBox = box,
            leftEye = landmarkPt(FaceLandmark.LEFT_EYE),
            rightEye = landmarkPt(FaceLandmark.RIGHT_EYE),
            noseBase = landmarkPt(FaceLandmark.NOSE_BASE),
            mouthLeft = landmarkPt(FaceLandmark.MOUTH_LEFT),
            mouthRight = landmarkPt(FaceLandmark.MOUTH_RIGHT),
            mouthBottom = landmarkPt(FaceLandmark.MOUTH_BOTTOM),
            leftCheek = landmarkPt(FaceLandmark.LEFT_CHEEK),
            rightCheek = landmarkPt(FaceLandmark.RIGHT_CHEEK),
            headEulerAngleZ = if (mirrorX) -face.headEulerAngleZ else face.headEulerAngleZ,
            smilingProbability = face.smilingProbability?.takeIf { it >= 0f } ?: 0f,
            leftEyeOpenProbability = face.leftEyeOpenProbability?.takeIf { it >= 0f } ?: 1f,
            rightEyeOpenProbability = face.rightEyeOpenProbability?.takeIf { it >= 0f } ?: 1f,
            sourceAspectRatio = w / h
        )
    }

    private fun maskToAlphaBitmap(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        mirrorX: Boolean
    ): Bitmap {
        buffer.rewind()
        val floatBuf = buffer.asFloatBuffer()
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val conf = floatBuf.get().coerceIn(0f, 1f)
                // Alpha = 0 for foreground person, opaque for background replacement compositing
                val bgAlpha = ((1f - conf) * 235f).roundToInt().coerceIn(0, 235)
                val targetX = if (mirrorX) (width - 1 - x) else x
                pixels[y * width + targetX] = Color.argb(bgAlpha, 255, 255, 255)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun close() {
        runCatching { faceDetectorRef?.close() }
        faceDetectorRef = null
        runCatching { segmenterRef?.close() }
        segmenterRef = null
    }
}

object FaceBeautyAndArRenderer {

    /**
     * Maps normalized 0..1 coordinates from ML analysis frame onto FILL_CENTER canvas coordinates.
     */
    fun mapPoint(pt: PointF, canvasW: Float, canvasH: Float, srcAspect: Float): PointF {
        val dstAspect = canvasW / canvasH.coerceAtLeast(1f)
        val (scaledW, scaledH) = if (srcAspect > dstAspect) {
            canvasH * srcAspect to canvasH
        } else {
            canvasW to canvasW / srcAspect.coerceAtLeast(0.1f)
        }
        val offsetX = (scaledW - canvasW) / 2f
        val offsetY = (scaledH - canvasH) / 2f
        return PointF(pt.x * scaledW - offsetX, pt.y * scaledH - offsetY)
    }

    fun mapRect(rect: RectF, canvasW: Float, canvasH: Float, srcAspect: Float): RectF {
        val tl = mapPoint(PointF(rect.left, rect.top), canvasW, canvasH, srcAspect)
        val br = mapPoint(PointF(rect.right, rect.bottom), canvasW, canvasH, srcAspect)
        return RectF(tl.x, tl.y, br.x, br.y)
    }

    /**
     * Renders subtle natural beauty retouching & tracked AR effects on either Jetpack Compose Canvas
     * or CameraX OverlayEffect Canvas so both preview and recorded MP4 stay synchronized.
     */
    fun drawAndroidCanvas(
        canvas: android.graphics.Canvas,
        width: Float,
        height: Float,
        face: TrackedFaceState,
        beauty: BeautySettings,
        arEffect: ArEffectType,
        animPhase: Float
    ) {
        val hasFace = face.detected && face.boundingBox.width() > 0.04f
        val faceBox = if (hasFace) {
            mapRect(face.boundingBox, width, height, face.sourceAspectRatio)
        } else {
            // Fallback studio portrait center box if no face detected yet
            RectF(width * 0.25f, height * 0.20f, width * 0.75f, height * 0.62f)
        }
        val faceW = faceBox.width().coerceAtLeast(width * 0.22f)
        val faceH = faceBox.height().coerceAtLeast(height * 0.18f)
        val factor = beauty.activeFactor

        // 1. Face Lighting & Natural Skin Smoothing / Blemish Soft Glow
        if (beauty.enabled && factor > 0.02f) {
            val lightStrength = (beauty.faceLighting * factor).coerceIn(0f, 1f)
            if (lightStrength > 0.03f) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        faceBox.centerX(),
                        faceBox.centerY(),
                        faceW * 0.95f,
                        intArrayOf(
                            Color.argb((lightStrength * 44).roundToInt(), 255, 246, 236),
                            Color.argb((lightStrength * 16).roundToInt(), 255, 235, 220),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(faceBox.centerX(), faceBox.centerY(), faceW * 0.95f, glowPaint)
            }

            if (hasFace) {
                // Soft Cheek Blush
                val blushAlpha = (beauty.blushIntensity * factor * 75).roundToInt().coerceIn(0, 80)
                if (blushAlpha > 2) {
                    val leftCheek = face.leftCheek?.let { mapPoint(it, width, height, face.sourceAspectRatio) }
                        ?: PointF(faceBox.left + faceW * 0.25f, faceBox.top + faceH * 0.58f)
                    val rightCheek = face.rightCheek?.let { mapPoint(it, width, height, face.sourceAspectRatio) }
                        ?: PointF(faceBox.left + faceW * 0.75f, faceBox.top + faceH * 0.58f)
                    listOf(leftCheek, rightCheek).forEach { cheek ->
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            shader = RadialGradient(
                                cheek.x, cheek.y, faceW * 0.16f,
                                intArrayOf(Color.argb(blushAlpha, 244, 114, 146), Color.TRANSPARENT),
                                null, Shader.TileMode.CLAMP
                            )
                        }
                        canvas.drawCircle(cheek.x, cheek.y, faceW * 0.16f, p)
                    }
                }

                // Natural Lipstick Tint aligned to mouth landmarks
                val lipAlpha = (beauty.lipstickIntensity * factor * 115).roundToInt().coerceIn(0, 120)
                if (lipAlpha > 3 && face.mouthLeft != null && face.mouthRight != null) {
                    val ml = mapPoint(face.mouthLeft, width, height, face.sourceAspectRatio)
                    val mr = mapPoint(face.mouthRight, width, height, face.sourceAspectRatio)
                    val mb = face.mouthBottom?.let { mapPoint(it, width, height, face.sourceAspectRatio) }
                        ?: PointF((ml.x + mr.x) / 2f, (ml.y + mr.y) / 2f + faceH * 0.05f)
                    val lipColor = beauty.lipstickColorArgb.toInt()
                    val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(lipAlpha, Color.red(lipColor), Color.green(lipColor), Color.blue(lipColor))
                        style = Paint.Style.FILL
                    }
                    val path = Path().apply {
                        moveTo(ml.x, ml.y)
                        quadTo((ml.x + mr.x) / 2f, minOf(ml.y, mr.y) - faceH * 0.03f, mr.x, mr.y)
                        quadTo(mb.x, mb.y + faceH * 0.02f, ml.x, ml.y)
                        close()
                    }
                    canvas.drawPath(path, lipPaint)

                    // Subtle teeth brightening inside smile
                    val teethAlpha = (beauty.teethWhitening * factor * 48).roundToInt().coerceIn(0, 55)
                    if (teethAlpha > 2 && face.smilingProbability > 0.35f) {
                        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(teethAlpha, 255, 255, 255)
                        }
                        canvas.drawOval(
                            ml.x + (mr.x - ml.x) * 0.2f,
                            (ml.y + mr.y) / 2f - faceH * 0.01f,
                            mr.x - (mr.x - ml.x) * 0.2f,
                            mb.y - faceH * 0.005f,
                            tp
                        )
                    }
                }

                // Subtle Eye Enhancement / Eyeshadow / Eyebrow Accent
                val eyeStrength = (beauty.eyeEnhancement * factor)
                val shadowAlpha = (beauty.eyeshadowIntensity * factor * 70).roundToInt().coerceIn(0, 75)
                val browAlpha = (beauty.eyebrowIntensity * factor * 85).roundToInt().coerceIn(0, 90)
                listOfNotNull(face.leftEye, face.rightEye).forEach { eyeNorm ->
                    val eye = mapPoint(eyeNorm, width, height, face.sourceAspectRatio)
                    if (eyeStrength > 0.05f) {
                        val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            shader = RadialGradient(
                                eye.x, eye.y, faceW * 0.11f,
                                intArrayOf(
                                    Color.argb((eyeStrength * 48).roundToInt(), 255, 255, 255),
                                    Color.TRANSPARENT
                                ),
                                null, Shader.TileMode.CLAMP
                            )
                        }
                        canvas.drawCircle(eye.x, eye.y, faceW * 0.11f, sparklePaint)
                    }
                    if (shadowAlpha > 2) {
                        val esPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(shadowAlpha, 168, 85, 130)
                        }
                        canvas.drawOval(
                            eye.x - faceW * 0.09f, eye.y - faceH * 0.045f,
                            eye.x + faceW * 0.09f, eye.y - faceH * 0.005f, esPaint
                        )
                    }
                    if (browAlpha > 2) {
                        val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(browAlpha, 48, 32, 24)
                            strokeWidth = (faceW * 0.022f).coerceAtLeast(3f)
                            strokeCap = Paint.Cap.ROUND
                            style = Paint.Style.STROKE
                        }
                        canvas.drawArc(
                            eye.x - faceW * 0.10f, eye.y - faceH * 0.095f,
                            eye.x + faceW * 0.10f, eye.y - faceH * 0.045f,
                            195f, 150f, false, browPaint
                        )
                    }
                }

                // Subtle Face Contouring along jawline edges
                val contourAlpha = (beauty.faceContourIntensity * factor * 55).roundToInt().coerceIn(0, 60)
                if (contourAlpha > 2) {
                    val cPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(contourAlpha, 110, 68, 48)
                        style = Paint.Style.STROKE
                        strokeWidth = faceW * 0.035f
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawArc(faceBox, 25f, 130f, false, cPaint)
                }
            }
        }

        // 2. AR Face Filters & Tracked Stickers
        if (arEffect != ArEffectType.NONE) {
            drawArOverlay(canvas, width, height, faceBox, face, arEffect, animPhase)
        }
    }

    private fun drawArOverlay(
        canvas: android.graphics.Canvas,
        width: Float,
        height: Float,
        faceBox: RectF,
        face: TrackedFaceState,
        arEffect: ArEffectType,
        animPhase: Float
    ) {
        val faceW = faceBox.width()
        val faceH = faceBox.height()
        val cx = faceBox.centerX()
        val topY = faceBox.top

        canvas.save()
        canvas.rotate(face.headEulerAngleZ, cx, faceBox.centerY())

        when (arEffect) {
            ArEffectType.CAT_EARS, ArEffectType.PUPPY, ArEffectType.BUNNY, ArEffectType.BEAR -> {
                val emoji = when (arEffect) {
                    ArEffectType.CAT_EARS -> "🐱"
                    ArEffectType.PUPPY -> "🐶"
                    ArEffectType.BUNNY -> "🐰"
                    else -> "🐻"
                }
                val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when (arEffect) {
                        ArEffectType.CAT_EARS -> Color.argb(235, 255, 182, 193)
                        ArEffectType.PUPPY -> Color.argb(235, 180, 120, 72)
                        ArEffectType.BUNNY -> Color.argb(240, 255, 240, 245)
                        else -> Color.argb(235, 139, 90, 43)
                    }
                    style = Paint.Style.FILL
                }
                val earBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                }
                val earH = if (arEffect == ArEffectType.BUNNY) faceH * 0.42f else faceH * 0.26f
                val leftEar = RectF(cx - faceW * 0.44f, topY - earH * 0.72f, cx - faceW * 0.14f, topY + earH * 0.18f)
                val rightEar = RectF(cx + faceW * 0.14f, topY - earH * 0.72f, cx + faceW * 0.44f, topY + earH * 0.18f)
                canvas.drawOval(leftEar, earPaint)
                canvas.drawOval(leftEar, earBorder)
                canvas.drawOval(rightEar, earPaint)
                canvas.drawOval(rightEar, earBorder)

                // Whiskers / Cute Nose on tracked noseBase
                val nose = face.noseBase?.let { mapPoint(it, width, height, face.sourceAspectRatio) }
                    ?: PointF(cx, faceBox.centerY() + faceH * 0.08f)
                val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (arEffect == ArEffectType.PUPPY || arEffect == ArEffectType.BEAR) {
                        Color.rgb(45, 32, 24)
                    } else {
                        Color.rgb(255, 110, 150)
                    }
                }
                canvas.drawOval(nose.x - faceW * 0.07f, nose.y - faceW * 0.045f, nose.x + faceW * 0.07f, nose.y + faceW * 0.045f, nosePaint)
                if (arEffect == ArEffectType.CAT_EARS || arEffect == ArEffectType.BUNNY) {
                    val wPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(210, 255, 255, 255)
                        strokeWidth = 4f
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(nose.x - faceW * 0.14f, nose.y, nose.x - faceW * 0.36f, nose.y - 14f, wPaint)
                    canvas.drawLine(nose.x - faceW * 0.14f, nose.y + 10f, nose.x - faceW * 0.36f, nose.y + 14f, wPaint)
                    canvas.drawLine(nose.x + faceW * 0.14f, nose.y, nose.x + faceW * 0.36f, nose.y - 14f, wPaint)
                    canvas.drawLine(nose.x + faceW * 0.14f, nose.y + 10f, nose.x + faceW * 0.36f, nose.y + 14f, wPaint)
                }
                val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = faceW * 0.14f; textAlign = Paint.Align.CENTER }
                canvas.drawText(emoji, cx, topY - earH * 0.78f, tagPaint)
            }
            ArEffectType.NEON_SHADES, ArEffectType.CYBER_MASK -> {
                val leftE = face.leftEye?.let { mapPoint(it, width, height, face.sourceAspectRatio) }
                    ?: PointF(cx - faceW * 0.18f, faceBox.top + faceH * 0.38f)
                val rightE = face.rightEye?.let { mapPoint(it, width, height, face.sourceAspectRatio) }
                    ?: PointF(cx + faceW * 0.18f, faceBox.top + faceH * 0.38f)
                val eyeSpan = hypot(rightE.x - leftE.x, rightE.y - leftE.y).coerceAtLeast(faceW * 0.25f)
                val eyeAngle = Math.toDegrees(atan2((rightE.y - leftE.y).toDouble(), (rightE.x - leftE.x).toDouble())).toFloat()
                val midX = (leftE.x + rightE.x) / 2f
                val midY = (leftE.y + rightE.y) / 2f

                canvas.restore()
                canvas.save()
                canvas.rotate(eyeAngle, midX, midY)

                val lensW = eyeSpan * 0.52f
                val lensH = lensW * 0.62f
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (arEffect == ArEffectType.CYBER_MASK) Color.argb(175, 6, 182, 212) else Color.argb(215, 15, 23, 42)
                }
                val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (arEffect == ArEffectType.CYBER_MASK) Color.rgb(34, 211, 238) else Color.rgb(229, 9, 20)
                    style = Paint.Style.STROKE
                    strokeWidth = 7f
                }
                val leftLens = RectF(midX - eyeSpan * 0.65f, midY - lensH / 2f, midX - eyeSpan * 0.08f, midY + lensH / 2f)
                val rightLens = RectF(midX + eyeSpan * 0.08f, midY - lensH / 2f, midX + eyeSpan * 0.65f, midY + lensH / 2f)
                canvas.drawRoundRect(leftLens, 22f, 22f, fill)
                canvas.drawRoundRect(leftLens, 22f, 22f, frame)
                canvas.drawRoundRect(rightLens, 22f, 22f, fill)
                canvas.drawRoundRect(rightLens, 22f, 22f, frame)
                canvas.drawLine(leftLens.right, midY, rightLens.left, midY, frame)
            }
            ArEffectType.CROWN -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = (faceW * 0.42f).coerceIn(64f, 180f)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("👑", cx, topY - faceH * 0.06f, p)
            }
            ArEffectType.BIG_EYES -> {
                listOfNotNull(face.leftEye, face.rightEye).forEach { norm ->
                    val e = mapPoint(norm, width, height, face.sourceAspectRatio)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            e.x, e.y, faceW * 0.14f,
                            intArrayOf(Color.argb(125, 255, 255, 255), Color.argb(40, 56, 189, 248), Color.TRANSPARENT),
                            null, Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawCircle(e.x, e.y, faceW * 0.14f, p)
                }
            }
            ArEffectType.FUNNY_MORPH -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(130, 255, 110, 140)
                }
                val lc = face.leftCheek?.let { mapPoint(it, width, height, face.sourceAspectRatio) }
                    ?: PointF(cx - faceW * 0.25f, faceBox.centerY() + faceH * 0.1f)
                val rc = face.rightCheek?.let { mapPoint(it, width, height, face.sourceAspectRatio) }
                    ?: PointF(cx + faceW * 0.25f, faceBox.centerY() + faceH * 0.1f)
                canvas.drawCircle(lc.x, lc.y, faceW * 0.12f, p)
                canvas.drawCircle(rc.x, rc.y, faceW * 0.12f, p)
                val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = faceW * 0.22f
                    textAlign = Paint.Align.CENTER
                }
                val reaction = if (face.smilingProbability > 0.55f) "😂" else "🤪"
                canvas.drawText(reaction, cx, topY - faceH * 0.05f, txt)
            }
            ArEffectType.SPARKLE_HALO, ArEffectType.EMOJI_BURST -> {
                val items = if (arEffect == ArEffectType.SPARKLE_HALO) listOf("✨", "⭐", "💫", "✨") else listOf("🥳", "🔥", "❤️", "⚡")
                val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = (faceW * 0.18f).coerceIn(36f, 96f)
                    textAlign = Paint.Align.CENTER
                }
                items.forEachIndexed { idx, sym ->
                    val angle = animPhase * 6.28318f + idx * (6.28318f / items.size)
                    val ox = cx + kotlin.math.cos(angle) * faceW * 0.55f
                    val oy = topY - faceH * 0.08f + kotlin.math.sin(angle) * faceH * 0.12f
                    canvas.drawText(sym, ox, oy, txt)
                }
            }
            ArEffectType.NONE -> Unit
        }
        canvas.restore()
    }
}
