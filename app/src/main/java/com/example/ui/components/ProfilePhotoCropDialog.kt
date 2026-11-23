package com.example.ui.components

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

@Composable
fun ProfilePhotoCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var workingBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(imageUri) { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(imageUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUri) { mutableFloatStateOf(0f) }

    LaunchedEffect(imageUri) {
        val loaded = withContext(Dispatchers.IO) {
            decodeOrientedBitmap(context.contentResolver, imageUri, maxDimension = 1600)
        }
        if (loaded != null) {
            workingBitmap = loaded
            loadError = null
        } else {
            loadError = "Unable to open selected photo"
        }
    }

    val cropDiameterPx = min(viewportSize.width, viewportSize.height) * 0.84f
    val bmp = workingBitmap
    val baseScale = remember(bmp, cropDiameterPx) {
        if (bmp != null && cropDiameterPx > 0f && bmp.width > 0 && bmp.height > 0) {
            max(cropDiameterPx / bmp.width.toFloat(), cropDiameterPx / bmp.height.toFloat())
        } else 1f
    }

    fun clampOffsets(rawX: Float, rawY: Float, currentZoom: Float): Pair<Float, Float> {
        if (bmp == null || cropDiameterPx <= 0f) return rawX to rawY
        val totalScale = baseScale * currentZoom
        val drawnW = bmp.width * totalScale
        val drawnH = bmp.height * totalScale
        val maxX = max(0f, (drawnW - cropDiameterPx) / 2f)
        val maxY = max(0f, (drawnH - cropDiameterPx) / 2f)
        return rawX.coerceIn(-maxX, maxX) to rawY.coerceIn(-maxY, maxY)
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier.testTag("crop_cancel_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel crop", tint = TextWhite)
                }

                Text(
                    text = "Crop Profile Photo",
                    color = TextWhite,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                IconButton(
                    onClick = {
                        val current = workingBitmap ?: return@IconButton
                        scope.launch {
                            val rotated = withContext(Dispatchers.Default) {
                                val matrix = Matrix().apply { postRotate(90f) }
                                Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                            }
                            workingBitmap = rotated
                            offsetX = 0f
                            offsetY = 0f
                            zoom = 1f
                        }
                    },
                    enabled = bmp != null && !isSaving,
                    modifier = Modifier.testTag("crop_rotate_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 degrees", tint = TextWhite)
                }
            }

            // Interactive crop canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(bmp, cropDiameterPx) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            val (clampedX, clampedY) = clampOffsets(
                                offsetX + pan.x,
                                offsetY + pan.y,
                                newZoom
                            )
                            zoom = newZoom
                            offsetX = clampedX
                            offsetY = clampedY
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    loadError != null -> {
                        Text(
                            text = loadError ?: "Failed to load image",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    bmp == null -> {
                        CircularProgressIndicator(color = BladeCrimson)
                    }
                    else -> {
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                        val totalScale = baseScale * zoom

                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Selected photo to crop",
                            contentScale = ContentScale.None,
                            modifier = Modifier.graphicsLayer {
                                translationX = offsetX
                                translationY = offsetY
                                scaleX = totalScale
                                scaleY = totalScale
                            }
                        )

                        // Circular crop scrim overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (cropDiameterPx > 0f) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val radius = cropDiameterPx / 2f
                                val maskPath = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                    addOval(Rect(Offset(cx - radius, cy - radius), androidx.compose.ui.geometry.Size(cropDiameterPx, cropDiameterPx)))
                                }
                                drawPath(path = maskPath, color = Color.Black.copy(alpha = 0.65f))
                                drawCircle(
                                    color = TextWhite,
                                    radius = radius,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // Bottom zoom controls + Apply button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101014))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pinch or drag to position your photo",
                    color = TextWhite.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newZoom = (zoom - 0.25f).coerceIn(1f, 4f)
                            val (cx, cy) = clampOffsets(offsetX, offsetY, newZoom)
                            zoom = newZoom
                            offsetX = cx
                            offsetY = cy
                        },
                        enabled = bmp != null && !isSaving
                    ) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out", tint = TextWhite)
                    }

                    Slider(
                        value = zoom,
                        onValueChange = { newZoom ->
                            val clampedZoom = newZoom.coerceIn(1f, 4f)
                            val (cx, cy) = clampOffsets(offsetX, offsetY, clampedZoom)
                            zoom = clampedZoom
                            offsetX = cx
                            offsetY = cy
                        },
                        valueRange = 1f..4f,
                        enabled = bmp != null && !isSaving,
                        colors = SliderDefaults.colors(
                            thumbColor = BladeCrimson,
                            activeTrackColor = BladeCrimson,
                            inactiveTrackColor = TextWhite.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            val newZoom = (zoom + 0.25f).coerceIn(1f, 4f)
                            val (cx, cy) = clampOffsets(offsetX, offsetY, newZoom)
                            zoom = newZoom
                            offsetX = cx
                            offsetY = cy
                        },
                        enabled = bmp != null && !isSaving
                    ) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in", tint = TextWhite)
                    }
                }

                Button(
                    onClick = {
                        val source = workingBitmap ?: return@Button
                        if (cropDiameterPx <= 0f || isSaving) return@Button
                        isSaving = true
                        scope.launch {
                            val croppedUri = withContext(Dispatchers.IO) {
                                saveCroppedSquareBitmap(
                                    cacheDir = context.cacheDir,
                                    source = source,
                                    cropDiameterPx = cropDiameterPx,
                                    totalScale = baseScale * zoom,
                                    offsetX = offsetX,
                                    offsetY = offsetY
                                )
                            }
                            isSaving = false
                            if (croppedUri != null) {
                                onCropped(croppedUri)
                            } else {
                                loadError = "Failed to crop photo"
                            }
                        }
                    },
                    enabled = bmp != null && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson, contentColor = TextWhite),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("crop_confirm_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = TextWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Crop & Use Photo", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

private fun decodeOrientedBitmap(
    contentResolver: ContentResolver,
    uri: Uri,
    maxDimension: Int
): Bitmap? {
    return runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return@runCatching null

        var sampleSize = 1
        while (origW / sampleSize > maxDimension || origH / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return@runCatching null

        val orientation = contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return@runCatching decoded
        }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }.getOrNull()
}

private fun saveCroppedSquareBitmap(
    cacheDir: File,
    source: Bitmap,
    cropDiameterPx: Float,
    totalScale: Float,
    offsetX: Float,
    offsetY: Float,
    outputSize: Int = 512
): Uri? {
    return runCatching {
        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)

        val factor = outputSize.toFloat() / cropDiameterPx
        val matrix = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postScale(totalScale * factor, totalScale * factor)
            postTranslate(outputSize / 2f + offsetX * factor, outputSize / 2f + offsetY * factor)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)

        val outFile = File(cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { fos ->
            output.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        }
        output.recycle()
        Uri.fromFile(outFile)
    }.getOrNull()
}
