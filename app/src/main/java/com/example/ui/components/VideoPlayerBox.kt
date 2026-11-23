package com.example.ui.components

import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.BladeApp
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeRed
import com.example.ui.theme.BladeRedAccent
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerBox(
    videoUri: String,
    isActive: Boolean,
    filterName: String = "Normal",
    trimStartMs: Long = 0L,
    trimEndMs: Long = 0L,
    durationMs: Long = 0L,
    showSeekBar: Boolean = true,
    playbackSpeed: Float = 1f,
    playbackVolume: Float = 1f,
    filterIntensity: Float = 1f,
    extraBrightness: Float = 0f,
    extraWarmth: Float = 0f,
    isPhoto: Boolean = false,
    onDoubleTapLike: () -> Unit = {},
    onStartedPlayback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val resolvedIsPhoto = remember(videoUri, isPhoto) {
        if (isPhoto) {
            true
        } else {
            val clean = videoUri.lowercase().substringBefore('?')
            clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") || clean.endsWith(".webp")
        }
    }

    val currentIsActive by rememberUpdatedState(isActive)
    var userPaused by remember(videoUri) { mutableStateOf(false) }
    val currentUserPaused by rememberUpdatedState(userPaused)
    var isBuffering by remember(videoUri) { mutableStateOf(true) }
    var hasError by remember(videoUri) { mutableStateOf(false) }
    var audioRecoveryAttempts by remember(videoUri) { mutableIntStateOf(0) }
    var tapHeartOffset by remember { mutableStateOf<Offset?>(null) }
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }

    var isScrubbing by remember(videoUri) { mutableStateOf(false) }
    var scrubProgress by remember(videoUri) { mutableFloatStateOf(0f) }
    var playbackProgress by remember(videoUri) { mutableFloatStateOf(0f) }
    var resolvedDurationMs by remember(videoUri) {
        val initialDur = if (trimEndMs > trimStartMs) trimEndMs - trimStartMs else durationMs
        mutableLongStateOf(initialDur.coerceAtLeast(0L))
    }
    var seekHoldUntilElapsedMs by remember(videoUri) { mutableLongStateOf(0L) }
    var lastLiveSeekAtMs by remember(videoUri) { mutableLongStateOf(0L) }

    val exoPlayer = remember(videoUri, trimStartMs, trimEndMs, durationMs) {
        val app = context.applicationContext as? BladeApp
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)

        val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

        val mediaSourceFactory = if (app != null && videoUri.startsWith("http")) {
            val cacheFactory = CacheDataSource.Factory()
                .setCache(app.mediaCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            DefaultMediaSourceFactory(cacheFactory, extractorsFactory)
        } else {
            DefaultMediaSourceFactory(upstreamFactory, extractorsFactory)
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 15000, 800, 1500)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                val hasCustomTrim = trimStartMs > 0L ||
                    (trimEndMs > trimStartMs && durationMs > 0L && trimEndMs < durationMs - 250L)
                val itemBuilder = MediaItem.Builder().setUri(Uri.parse(videoUri))
                if (hasCustomTrim && trimEndMs > trimStartMs) {
                    itemBuilder.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(trimStartMs)
                            .setEndPositionMs(trimEndMs)
                            .build()
                    )
                }
                setMediaItem(itemBuilder.build())
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    hasError = false
                    val playerDur = exoPlayer.duration
                    if (playerDur != C.TIME_UNSET && playerDur > 0L) {
                        resolvedDurationMs = playerDur
                    }
                    if (currentIsActive) onStartedPlayback()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.isAudioSinkDiscontinuityError() && audioRecoveryAttempts < 2) {
                    val attempt = audioRecoveryAttempts
                    audioRecoveryAttempts = attempt + 1
                    hasError = false
                    isBuffering = true
                    if (attempt == 1) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .build()
                    }
                    val resumePosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                    exoPlayer.seekTo(resumePosition)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = currentIsActive && !currentUserPaused
                } else {
                    isBuffering = false
                    hasError = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(isActive, userPaused, resolvedIsPhoto, playbackSpeed, playbackVolume) {
        if (resolvedIsPhoto) {
            exoPlayer.playWhenReady = false
            exoPlayer.volume = 0f
            isBuffering = false
            if (isActive) onStartedPlayback()
            return@LaunchedEffect
        }
        exoPlayer.setPlaybackSpeed(playbackSpeed.coerceIn(0.25f, 4f))
        exoPlayer.volume = if (isActive) playbackVolume.coerceIn(0f, 1f) else 0f
        if (isActive && !userPaused) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    // Poll current playback position cleanly when not scrubbing
    LaunchedEffect(exoPlayer, isActive, showSeekBar) {
        if (!showSeekBar) return@LaunchedEffect
        while (isActive) {
            if (currentIsActive && !isScrubbing) {
                val playerDur = exoPlayer.duration
                if (playerDur != C.TIME_UNSET && playerDur > 0L) {
                    resolvedDurationMs = playerDur
                }
                val dur = resolvedDurationMs
                if (dur > 0L && SystemClock.elapsedRealtime() >= seekHoldUntilElapsedMs) {
                    val pos = exoPlayer.currentPosition.coerceIn(0L, dur)
                    playbackProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(50L)
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> if (currentIsActive && !currentUserPaused) exoPlayer.playWhenReady = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(videoUri) {
                detectTapGestures(
                    onTap = {
                        userPaused = !userPaused
                    },
                    onDoubleTap = { offset ->
                        tapHeartOffset = offset
                        onDoubleTapLike()
                        scope.launch {
                            heartScale.snapTo(0.4f)
                            heartAlpha.snapTo(1f)
                            heartScale.animateTo(1.45f, tween(220))
                            heartScale.animateTo(1.1f, tween(120))
                            heartAlpha.animateTo(0f, tween(350))
                            tapHeartOffset = null
                        }
                    }
                )
            }
    ) {
        if (resolvedIsPhoto) {
            AsyncImage(
                model = videoUri,
                contentDescription = "Photo post",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        val tint = filterOverlayColor(filterName)
        if (tint != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tint.copy(alpha = (tint.alpha * filterIntensity.coerceIn(0f, 1f)).coerceIn(0f, 0.65f)))
            )
        }
        if (kotlin.math.abs(extraBrightness) > 0.02f) {
            val liftColor = if (extraBrightness > 0f) {
                Color.White.copy(alpha = (extraBrightness * 0.25f).coerceIn(0f, 0.28f))
            } else {
                Color.Black.copy(alpha = (-extraBrightness * 0.35f).coerceIn(0f, 0.38f))
            }
            Box(modifier = Modifier.fillMaxSize().background(liftColor))
        }
        if (kotlin.math.abs(extraWarmth) > 0.02f) {
            val warmColor = if (extraWarmth > 0f) {
                Color(0xFFFF9800).copy(alpha = (extraWarmth * 0.24f).coerceIn(0f, 0.22f))
            } else {
                Color(0xFF38BDF8).copy(alpha = (-extraWarmth * 0.24f).coerceIn(0f, 0.22f))
            }
            Box(modifier = Modifier.fillMaxSize().background(warmColor))
        }

        if (!resolvedIsPhoto && isBuffering && !hasError) {
            CircularProgressIndicator(
                color = TextWhite.copy(alpha = 0.75f),
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.Center)
            )
        }

        if (hasError) {
            IconButton(
                onClick = {
                    hasError = false
                    isBuffering = true
                    audioRecoveryAttempts = 0
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = currentIsActive && !currentUserPaused
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry video",
                    tint = TextWhite,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = userPaused,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Paused",
                    tint = TextWhite.copy(alpha = 0.9f),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        tapHeartOffset?.let { pos ->
            Icon(
                imageVector = Icons.Rounded.ThumbUp,
                contentDescription = null,
                tint = BladeCrimson,
                modifier = Modifier
                    .offset {
                        IntOffset((pos.x - 90).roundToInt(), (pos.y - 90).roundToInt())
                    }
                    .size(96.dp)
                    .scale(heartScale.value)
                    .alpha(heartAlpha.value)
            )
        }

        if (showSeekBar && !resolvedIsPhoto) {
            val displayedProgress = if (isScrubbing) scrubProgress else playbackProgress
            VideoSeekProgressBar(
                progress = displayedProgress,
                durationMs = resolvedDurationMs,
                isScrubbing = isScrubbing,
                seekKey = videoUri,
                onScrubStart = { fraction ->
                    val dur = resolvedDurationMs
                    isScrubbing = true
                    scrubProgress = fraction
                    playbackProgress = fraction
                    if (dur > 0L) {
                        val targetMs = (fraction * dur).toLong().coerceIn(0L, dur)
                        exoPlayer.seekTo(targetMs)
                        lastLiveSeekAtMs = SystemClock.elapsedRealtime()
                    }
                },
                onScrubMove = { fraction ->
                    val dur = resolvedDurationMs
                    scrubProgress = fraction
                    playbackProgress = fraction
                    if (dur > 0L) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastLiveSeekAtMs >= 90L) {
                            val targetMs = (fraction * dur).toLong().coerceIn(0L, dur)
                            exoPlayer.seekTo(targetMs)
                            lastLiveSeekAtMs = now
                        }
                    }
                },
                onScrubEnd = { fraction ->
                    val dur = resolvedDurationMs
                    playbackProgress = fraction
                    scrubProgress = fraction
                    if (dur > 0L) {
                        val finalMs = (fraction * dur).toLong().coerceIn(0L, dur)
                        exoPlayer.seekTo(finalMs)
                        seekHoldUntilElapsedMs = SystemClock.elapsedRealtime() + 350L
                    }
                    isScrubbing = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun VideoSeekProgressBar(
    progress: Float,
    durationMs: Long,
    isScrubbing: Boolean,
    seekKey: String,
    onScrubStart: (Float) -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnStart by rememberUpdatedState(onScrubStart)
    val latestOnMove by rememberUpdatedState(onScrubMove)
    val latestOnEnd by rememberUpdatedState(onScrubEnd)

    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 4.dp else 2.dp,
        animationSpec = tween(120),
        label = "seekTrackHeight"
    )
    val clampedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Floating timestamp badge while dragging backward/forward
        AnimatedVisibility(
            visible = isScrubbing && durationMs > 0L,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
        ) {
            val currentMs = (clampedProgress * durationMs).toLong().coerceIn(0L, durationMs)
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.78f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BladeRed.copy(alpha = 0.55f))
            ) {
                Text(
                    text = "${formatDurationMs(currentMs)} / ${formatDurationMs(durationMs)}",
                    color = TextWhite,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // 48dp invisible touch/drag hit area flush at the very top edge of the navigation bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
                .testTag("video_seek_bar")
                .pointerInput(seekKey) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val w = size.width.coerceAtLeast(1).toFloat()
                        var latestFraction = (down.position.x / w).coerceIn(0f, 1f)
                        latestOnStart(latestFraction)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                change?.consume()
                                latestOnEnd(latestFraction)
                                break
                            }
                            latestFraction = (change.position.x / w).coerceIn(0f, 1f)
                            change.consume()
                            latestOnMove(latestFraction)
                        }
                    }
                },
            contentAlignment = Alignment.BottomStart
        ) {
            // Razor-thin edge-to-edge track flush with the top of the navigation bar (no gap, no circle handle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .background(TextWhite.copy(alpha = 0.24f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clampedProgress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(BladeRed, BladeRedAccent))
                        )
                )
            }
        }
    }
}

private fun PlaybackException.isAudioSinkDiscontinuityError(): Boolean {
    if (errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
    ) {
        return true
    }
    var cur: Throwable? = this
    while (cur != null) {
        if (cur is AudioSink.UnexpectedDiscontinuityException) return true
        if (cur.message?.contains("discontinuity", ignoreCase = true) == true) return true
        cur = cur.cause
    }
    return false
}
