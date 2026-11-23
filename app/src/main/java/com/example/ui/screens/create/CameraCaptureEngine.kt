package com.example.ui.screens.create

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BladeApp
import com.example.core.camera.ArEffectType
import com.example.core.camera.BeautySettings
import com.example.core.camera.CameraMlAnalyzer
import com.example.core.camera.FaceBeautyAndArRenderer
import com.example.core.camera.TrackedFaceState
import com.example.ui.theme.BladeGold
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.GreenScreenPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Stable
class BladeCameraController(private val context: Context) {
    internal var camera: Camera? by mutableStateOf(null)
    internal var videoCapture: VideoCapture<Recorder>? by mutableStateOf(null)
    internal var activeRecording: Recording? by mutableStateOf(null)
    internal var cameraProvider: ProcessCameraProvider? = null

    private var recordingStartWallMs: Long = 0L
    private var stopRequestedByUser: Boolean = false
    private var cancelRequestedByUser: Boolean = false

    var hasFlashUnit by mutableStateOf(false)
        internal set
    var isStreaming by mutableStateOf(false)
        internal set
    var isPreparingRecording by mutableStateOf(false)
        internal set
    var isRecording by mutableStateOf(false)
        internal set
    var cameraError by mutableStateOf<String?>(null)
        internal set
    var zoomRatio by mutableFloatStateOf(1f)
        internal set
    var retryEpoch by mutableIntStateOf(0)
        private set

    fun retryBinding() {
        cameraError = null
        retryEpoch++
    }

    fun startVideoRecording(
        onStarted: () -> Unit,
        onError: (String) -> Unit,
        onFinalized: (Uri) -> Unit
    ) {
        if (isRecording || isPreparingRecording || activeRecording != null) return
        val capture = videoCapture ?: run {
            onError("Camera is still warming up. Please wait a moment.")
            return
        }
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        stopRequestedByUser = false
        cancelRequestedByUser = false
        startRecordingInternal(
            capture = capture,
            allowAudio = hasAudioPermission,
            retriedWithoutAudio = false,
            onStarted = onStarted,
            onError = onError,
            onFinalized = onFinalized
        )
    }

    private fun startRecordingInternal(
        capture: VideoCapture<Recorder>,
        allowAudio: Boolean,
        retriedWithoutAudio: Boolean,
        onStarted: () -> Unit,
        onError: (String) -> Unit,
        onFinalized: (Uri) -> Unit
    ) {
        val timestamp = System.currentTimeMillis()
        val outFile = File(context.cacheDir, "blade_rec_$timestamp.mp4")
        if (outFile.exists()) outFile.delete()

        var pending = capture.output.prepareRecording(
            context,
            FileOutputOptions.Builder(outFile).build()
        )
        if (allowAudio) {
            pending = pending.withAudioEnabled()
        }

        val executor = ContextCompat.getMainExecutor(context)
        isPreparingRecording = true
        recordingStartWallMs = System.currentTimeMillis()

        try {
            activeRecording = pending.start(executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isPreparingRecording = false
                        isRecording = true
                        recordingStartWallMs = System.currentTimeMillis()
                        onStarted()
                    }
                    is VideoRecordEvent.Finalize -> {
                        val elapsedSinceStart = System.currentTimeMillis() - recordingStartWallMs
                        val wasCancelled = cancelRequestedByUser
                        val wasUserStop = stopRequestedByUser
                        isPreparingRecording = false
                        isRecording = false
                        activeRecording = null
                        cancelRequestedByUser = false
                        stopRequestedByUser = false

                        if (wasCancelled) {
                            runCatching { outFile.delete() }
                            return@start
                        }

                        if (!event.hasError()) {
                            val uri = event.outputResults.outputUri
                            if (uri != Uri.EMPTY && outFile.exists() && outFile.length() > 0L) {
                                onFinalized(uri)
                            } else {
                                runCatching { outFile.delete() }
                                onError("Could not save recorded video.")
                            }
                            return@start
                        }

                        val savedUri = event.outputResults.outputUri
                        if (savedUri != Uri.EMPTY &&
                            event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED &&
                            outFile.exists() && outFile.length() > 0L
                        ) {
                            onFinalized(savedUri)
                            return@start
                        }

                        // If mic/AudioSource failed or stalled with zero data (code 8 / source inactive),
                        // automatically retry once video-only instead of aborting the recording.
                        val isAudioInitOrNoDataError =
                            event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ||
                                event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ||
                                event.error == VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR ||
                                event.error == VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED

                        if (allowAudio && !retriedWithoutAudio && !wasUserStop && isAudioInitOrNoDataError) {
                            runCatching { outFile.delete() }
                            val currentCapture = videoCapture ?: capture
                            startRecordingInternal(
                                capture = currentCapture,
                                allowAudio = false,
                                retriedWithoutAudio = true,
                                onStarted = onStarted,
                                onError = onError,
                                onFinalized = onFinalized
                            )
                            return@start
                        }

                        runCatching { outFile.delete() }
                        val friendlyMessage = when {
                            event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA && wasUserStop && elapsedSinceStart < 1_000L ->
                                "Video too short. Record at least 1 second."
                            event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ->
                                "Camera stream interrupted. Tap Retry or try recording again."
                            else -> "Recording interrupted (code ${event.error}). Please try again."
                        }
                        onError(friendlyMessage)
                    }
                }
            }
        } catch (t: Throwable) {
            isPreparingRecording = false
            isRecording = false
            activeRecording = null
            runCatching { outFile.delete() }
            if (allowAudio && !retriedWithoutAudio) {
                startRecordingInternal(
                    capture = capture,
                    allowAudio = false,
                    retriedWithoutAudio = true,
                    onStarted = onStarted,
                    onError = onError,
                    onFinalized = onFinalized
                )
            } else {
                onError(t.message ?: "Unable to start video recording.")
            }
        }
    }

    fun stopVideoRecording() {
        stopRequestedByUser = true
        runCatching { activeRecording?.stop() }
        isPreparingRecording = false
        isRecording = false
    }

    fun cancelVideoRecording() {
        cancelRequestedByUser = true
        runCatching { activeRecording?.stop() }
        activeRecording = null
        isPreparingRecording = false
        isRecording = false
    }

    fun release() {
        cancelRequestedByUser = true
        runCatching { activeRecording?.stop() }
        activeRecording = null
        isPreparingRecording = false
        isRecording = false
        isStreaming = false
        camera = null
        videoCapture = null
        hasFlashUnit = false
        zoomRatio = 1f
        runCatching { cameraProvider?.unbindAll() }
    }
}

@Composable
fun rememberBladeCameraController(): BladeCameraController {
    val context = LocalContext.current
    return remember(context) { BladeCameraController(context.applicationContext) }
}

private fun bindSmoothCameraPipeline(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    controller: BladeCameraController,
    previewView: PreviewView,
    useFrontCamera: Boolean,
    attachAnalyzer: Boolean,
    analysisExecutor: ExecutorService,
    analyzer: CameraMlAnalyzer
) {
    controller.cameraProvider = cameraProvider
    controller.cameraError = null
    // Always invalidate old VideoCapture & Camera references before unbindAll()
    controller.camera = null
    controller.videoCapture = null
    controller.hasFlashUnit = false

    val desired = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    val fallback = if (useFrontCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    val resolvedSelector = when {
        runCatching { cameraProvider.hasCamera(desired) }.getOrDefault(false) -> desired
        runCatching { cameraProvider.hasCamera(fallback) }.getOrDefault(false) -> fallback
        else -> {
            controller.cameraError = "No camera hardware detected on this device."
            return
        }
    }

    val previewResSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(Size(1080, 1920), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
        )
        .build()

    val preview = Preview.Builder()
        .setDynamicRange(DynamicRange.SDR)
        .setResolutionSelector(previewResSelector)
        .build()
        .also { it.surfaceProvider = previewView.surfaceProvider }

    val qualitySelector = QualitySelector.fromOrderedList(
        listOf(Quality.FHD, Quality.HD, Quality.SD),
        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
    )
    val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
    val primaryVideoCapture = VideoCapture.Builder(recorder)
        .setDynamicRange(DynamicRange.SDR)
        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
        .build()

    cameraProvider.unbindAll()

    var boundVideoCapture: VideoCapture<Recorder> = primaryVideoCapture
    val boundCamera = if (attachAnalyzer) {
        val analysisSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(Size(240, 320), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisExecutor, analyzer) }

        runCatching {
            cameraProvider.bindToLifecycle(lifecycleOwner, resolvedSelector, preview, primaryVideoCapture, analysis)
        }.getOrNull()
    } else {
        null
    } ?: runCatching {
        cameraProvider.bindToLifecycle(lifecycleOwner, resolvedSelector, preview, primaryVideoCapture)
    }.getOrElse {
        val safePreview = Preview.Builder().setDynamicRange(DynamicRange.SDR).build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val safeRec = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        val safeCap = VideoCapture.Builder(safeRec)
            .setDynamicRange(DynamicRange.SDR)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
        boundVideoCapture = safeCap
        cameraProvider.unbindAll()
        runCatching {
            cameraProvider.bindToLifecycle(lifecycleOwner, resolvedSelector, safePreview, safeCap)
        }.getOrNull()
    }

    if (boundCamera != null) {
        controller.camera = boundCamera
        // Always assign the freshly bound VideoCapture instance after every bind/flip
        controller.videoCapture = boundVideoCapture
        controller.hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
        if (boundCamera.cameraInfo.isLowLightBoostSupported) {
            runCatching { boundCamera.cameraControl.enableLowLightBoostAsync(true) }
        }
    } else {
        controller.cameraError = "Unable to open camera preview. Tap Retry."
    }
}

@Composable
fun CameraPreviewViewport(
    controller: BladeCameraController,
    hasCameraPermission: Boolean,
    useFrontCamera: Boolean,
    flashEnabled: Boolean,
    beauty: BeautySettings,
    arEffect: ArEffectType,
    greenScreenEnabled: Boolean,
    greenScreenPreset: GreenScreenPreset?,
    onLumaSampled: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // COMPATIBLE mode (TextureView) composites cleanly inside Jetpack Compose without SurfaceView hole-punch dark shading
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var trackedFace by remember { mutableStateOf(TrackedFaceState()) }
    var segMaskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var animPhase by remember { mutableFloatStateOf(0f) }
    var showSlowLoadingSpinner by remember { mutableStateOf(false) }

    val latestFront by rememberUpdatedState(useFrontCamera)
    val latestBeauty by rememberUpdatedState(beauty)
    val latestAr by rememberUpdatedState(arEffect)
    val latestGreenEnabled by rememberUpdatedState(greenScreenEnabled)

    // Bind ImageAnalysis ML stream only when AR masks, Green Screen, or facial makeup landmarks are active
    val needsMlAnalyzer = arEffect != ArEffectType.NONE || greenScreenEnabled || beauty.needsFaceLandmarks

    val analyzer = remember {
        CameraMlAnalyzer(
            isFrontCameraProvider = { latestFront },
            isPreviewStreamingProvider = { controller.isStreaming },
            isRecordingProvider = { controller.isRecording },
            needsFaceTrackingProvider = { latestAr != ArEffectType.NONE || latestBeauty.needsFaceLandmarks },
            needsSegmentationProvider = { latestGreenEnabled },
            onFaceUpdated = { trackedFace = it },
            onLumaUpdated = onLumaSampled,
            onMaskUpdated = { bmp -> segMaskBitmap = bmp }
        )
    }

    LaunchedEffect(arEffect) {
        if (arEffect == ArEffectType.SPARKLE_HALO || arEffect == ArEffectType.EMOJI_BURST) {
            while (true) {
                delay(32L)
                animPhase = (animPhase + 0.025f) % 1f
            }
        }
    }

    LaunchedEffect(controller.isStreaming, hasCameraPermission) {
        if (controller.isStreaming || !hasCameraPermission) {
            showSlowLoadingSpinner = false
        } else {
            delay(280L)
            if (!controller.isStreaming) {
                showSlowLoadingSpinner = true
            }
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(950)
            focusPoint = null
        }
    }

    DisposableEffect(previewView) {
        val observer = Observer<PreviewView.StreamState> { state ->
            controller.isStreaming = state == PreviewView.StreamState.STREAMING
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose {
            previewView.previewStreamState.removeObserver(observer)
            analyzer.close()
            analysisExecutor.shutdown()
            controller.release()
        }
    }

    LaunchedEffect(hasCameraPermission, useFrontCamera, needsMlAnalyzer, controller.retryEpoch) {
        if (!hasCameraPermission || controller.isRecording || controller.isPreparingRecording) return@LaunchedEffect
        val app = context.applicationContext as? BladeApp
        val readyProvider = app?.prewarmedCameraProvider
        if (readyProvider != null) {
            bindSmoothCameraPipeline(
                cameraProvider = readyProvider,
                lifecycleOwner = lifecycleOwner,
                controller = controller,
                previewView = previewView,
                useFrontCamera = useFrontCamera,
                attachAnalyzer = needsMlAnalyzer,
                analysisExecutor = analysisExecutor,
                analyzer = analyzer
            )
        } else {
            val providerFuture = app?.getOrWarmCameraProviderFuture()
                ?: ProcessCameraProvider.getInstance(context)
            val activeJob = coroutineContext[Job]
            providerFuture.addListener({
                if (activeJob?.isActive != true) return@addListener
                val cameraProvider = runCatching { providerFuture.get() }.getOrNull() ?: run {
                    controller.cameraError = "Camera service unavailable on this device."
                    return@addListener
                }
                bindSmoothCameraPipeline(
                    cameraProvider = cameraProvider,
                    lifecycleOwner = lifecycleOwner,
                    controller = controller,
                    previewView = previewView,
                    useFrontCamera = useFrontCamera,
                    attachAnalyzer = needsMlAnalyzer,
                    analysisExecutor = analysisExecutor,
                    analyzer = analyzer
                )
            }, ContextCompat.getMainExecutor(context))
        }
    }

    LaunchedEffect(controller.camera, controller.isStreaming, flashEnabled, useFrontCamera, beauty.enabled, beauty.brightnessBoost) {
        val cam = controller.camera ?: return@LaunchedEffect
        val info = cam.cameraInfo
        val control = cam.cameraControl

        val torchActive = flashEnabled && !useFrontCamera && info.hasFlashUnit()
        runCatching { control.enableTorch(torchActive) }

        val expState = info.exposureState
        if (expState.isExposureCompensationSupported) {
            val range = expState.exposureCompensationRange
            val boost = if (beauty.enabled) beauty.brightnessBoost else 0.10f
            val normalized = (boost + if (useFrontCamera) 0.16f else 0f).coerceIn(-1f, 1f)
            val index = if (normalized >= 0f) {
                (normalized * range.upper).roundToInt()
            } else {
                (-normalized * range.lower).roundToInt()
            }.coerceIn(range.lower, range.upper)
            runCatching { control.setExposureCompensationIndex(index) }
        }

        // Meter center-face auto-exposure and auto-white-balance once preview starts streaming
        if (controller.isStreaming && previewView.width > 0 && previewView.height > 0) {
            val point = previewView.meteringPointFactory.createPoint(
                previewView.width * 0.5f,
                previewView.height * 0.42f
            )
            val aeAction = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AWB
            ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
            runCatching { control.startFocusAndMetering(aeAction) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(controller.camera, useFrontCamera) {
                detectTapGestures { offset ->
                    val cam = controller.camera ?: return@detectTapGestures
                    focusPoint = offset
                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    runCatching { cam.cameraControl.startFocusAndMetering(action) }
                }
            }
            .pointerInput(controller.camera) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    val cam = controller.camera ?: return@detectTransformGestures
                    val zoomState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                    val nextZoom = (controller.zoomRatio * zoomChange)
                        .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    controller.zoomRatio = nextZoom
                    runCatching { cam.cameraControl.setZoomRatio(nextZoom) }
                }
            }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (greenScreenEnabled && greenScreenPreset != null && segMaskBitmap != null) {
            val mask = segMaskBitmap!!
            val preset = greenScreenPreset
            Canvas(modifier = Modifier.fillMaxSize()) {
                val img = mask.asImageBitmap()
                drawImage(
                    image = img,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    alpha = 0.72f
                )
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color(preset.startArgb).copy(alpha = 0.55f),
                            Color(preset.endArgb).copy(alpha = 0.45f)
                        )
                    )
                )
            }
        }

        // Only invoke Android Canvas face renderer when AR masks or makeup landmarks are active
        if (arEffect != ArEffectType.NONE || beauty.needsFaceLandmarks) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                FaceBeautyAndArRenderer.drawAndroidCanvas(
                    canvas = drawContext.canvas.nativeCanvas,
                    width = size.width,
                    height = size.height,
                    face = trackedFace,
                    beauty = beauty,
                    arEffect = arEffect,
                    animPhase = animPhase
                )
            }
        }

        if (showSlowLoadingSpinner && !controller.isStreaming && controller.cameraError == null && hasCameraPermission) {
            CircularProgressIndicator(
                color = TextWhite.copy(alpha = 0.7f),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(30.dp).align(Alignment.Center)
            )
        }

        AnimatedVisibility(visible = focusPoint != null, enter = fadeIn(), exit = fadeOut()) {
            focusPoint?.let { pt ->
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset((pt.x - 36).roundToInt(), (pt.y - 36).roundToInt()) }
                        .size(72.dp)
                ) {
                    drawCircle(color = BladeGold, style = Stroke(width = 2.dp.toPx()))
                    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 4.dp.toPx())
                }
            }
        }
    }
}
