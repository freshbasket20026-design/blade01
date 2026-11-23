package com.example.ui.screens

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.ui.screens.create.CameraCaptureStep
import com.example.ui.screens.create.CreatePublishStep
import com.example.ui.screens.create.VideoEditorStudio
import com.example.ui.viewmodel.CreateStep
import com.example.ui.viewmodel.CreateViewModel

private enum class VideoPickTarget {
    PRIMARY,
    MERGE_CLIP,
    PIP_LAYER,
    GREEN_SCREEN_VIDEO
}

@Composable
fun CreateScreen(
    createViewModel: CreateViewModel,
    currentUser: UserProfile?,
    onClose: () -> Unit,
    onRequireAuth: () -> Unit,
    onUploadSuccess: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by createViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickTarget by remember { mutableStateOf(VideoPickTarget.PRIMARY) }

    // Video-only Android Photo Picker (photo selection completely removed)
    val videoOnlyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val durMs = resolveVideoDurationMs(context, uri)
            when (pickTarget) {
                VideoPickTarget.PRIMARY -> createViewModel.onVideoSelected(uri, durMs)
                VideoPickTarget.MERGE_CLIP -> createViewModel.mergeVideoSegment(uri, durMs)
                VideoPickTarget.PIP_LAYER -> createViewModel.setPipVideoUri(uri)
                VideoPickTarget.GREEN_SCREEN_VIDEO -> createViewModel.setGreenScreenVideoUri(uri)
            }
        }
    }

    fun launchVideoPicker(target: VideoPickTarget) {
        pickTarget = target
        videoOnlyPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    when {
        uiState.step == CreateStep.PUBLISH_MEDIA && uiState.selectedVideoUri != null -> {
            BackHandler { createViewModel.backToEdit() }
            CreatePublishStep(
                createViewModel = createViewModel,
                uiState = uiState,
                currentUser = currentUser,
                onBackToEdit = { createViewModel.backToEdit() },
                onRequireAuth = onRequireAuth,
                onUploadSuccess = onUploadSuccess,
                modifier = modifier
            )
        }
        uiState.step == CreateStep.EDIT_MEDIA && uiState.selectedVideoUri != null -> {
            BackHandler { createViewModel.backToCapture() }
            VideoEditorStudio(
                createViewModel = createViewModel,
                uiState = uiState,
                onBackToCamera = { createViewModel.backToCapture() },
                onDiscardDraft = { createViewModel.discardDraftMedia() },
                onPickMergeVideo = { launchVideoPicker(VideoPickTarget.MERGE_CLIP) },
                onPickPipVideo = { launchVideoPicker(VideoPickTarget.PIP_LAYER) },
                onNextToPublish = { createViewModel.proceedToPublish() },
                modifier = modifier
            )
        }
        else -> {
            CameraCaptureStep(
                createViewModel = createViewModel,
                uiState = uiState,
                onPickVideoGallery = { launchVideoPicker(VideoPickTarget.PRIMARY) },
                onPickGreenScreenVideo = { launchVideoPicker(VideoPickTarget.GREEN_SCREEN_VIDEO) },
                onClose = onClose,
                modifier = modifier
            )
        }
    }
}

private fun resolveVideoDurationMs(context: Context, uri: Uri): Long = runCatching {
    val mmr = MediaMetadataRetriever()
    mmr.setDataSource(context, uri)
    val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 30_000L
    mmr.release()
    d
}.getOrDefault(30_000L)
