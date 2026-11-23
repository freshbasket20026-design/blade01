package com.example.ui.screens.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.bladeStrings
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.ui.components.VideoPlayerBox
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.CreateUiState
import com.example.ui.viewmodel.CreateViewModel

@Composable
fun CreatePublishStep(
    createViewModel: CreateViewModel,
    uiState: CreateUiState,
    currentUser: UserProfile?,
    onBackToEdit: () -> Unit,
    onRequireAuth: () -> Unit,
    onUploadSuccess: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val context = LocalContext.current
    val mediaUri = uiState.selectedVideoUri ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackToEdit, modifier = Modifier.testTag("publish_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to editing")
            }
            Text("Post Video", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.size(48.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 128.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .clickable(onClick = onBackToEdit)
            ) {
                VideoPlayerBox(
                    videoUri = mediaUri.toString(),
                    isActive = false,
                    filterName = uiState.selectedFilter,
                    filterIntensity = uiState.filterIntensity,
                    showSeekBar = false,
                    isPhoto = false,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.62f)).padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Edit Cover", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { createViewModel.updateTitle(it) },
                    label = { Text(strings.videoTitleLabel) },
                    placeholder = { Text(strings.videoTitlePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("upload_title_input")
                )
                OutlinedTextField(
                    value = uiState.caption,
                    onValueChange = { createViewModel.updateCaption(it) },
                    label = { Text(strings.captionPlaceholder) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("upload_caption_input")
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("#foryou", "#blade", "#trending", "#viral", "#music", "#dhaka")) { tag ->
                Surface(onClick = { createViewModel.appendHashtag(tag) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(tag, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }

        OutlinedTextField(
            value = uiState.location,
            onValueChange = { createViewModel.updateLocation(it) },
            label = { Text(strings.locationPlaceholder) },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Category", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(createViewModel.categories) { cat ->
                FilterChip(selected = uiState.category == cat, onClick = { createViewModel.updateCategory(cat) }, label = { Text(cat) })
            }
        }

        Text(strings.privacyLabel, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            createViewModel.privacies.forEach { priv ->
                FilterChip(
                    selected = uiState.privacy == priv,
                    onClick = { createViewModel.updatePrivacy(priv) },
                    label = { Text(priv) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BladeCyan.copy(alpha = 0.22f))
                )
            }
        }

        uiState.uploadError?.let { err ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().testTag("upload_error_banner")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                    if (!uiState.isExporting && !uiState.isCheckingSafety && !uiState.isUploading) {
                        OutlinedButton(
                            onClick = {
                                if (currentUser == null) onRequireAuth()
                                else createViewModel.publish(context, onUploadSuccess)
                            },
                            modifier = Modifier.testTag("retry_upload_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Retry Upload")
                        }
                    }
                }
            }
        }

        when {
            uiState.isExporting -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Preparing Studio Video... ${(uiState.exportProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
                LinearProgressIndicator(progress = { uiState.exportProgress }, color = BladeCyan, modifier = Modifier.fillMaxWidth())
            }
            uiState.isCheckingSafety -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Scanning video for community safety...", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(color = BladeCyan, modifier = Modifier.fillMaxWidth())
            }
            uiState.isUploading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val pct = (uiState.uploadProgress * 100).toInt().coerceIn(0, 100)
                val statusLabel = uiState.uploadStatusText.ifBlank { "Uploading Video ($pct%)..." }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(statusLabel, style = MaterialTheme.typography.labelMedium)
                    Text("$pct%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
                LinearProgressIndicator(
                    progress = { uiState.uploadProgress.coerceIn(0f, 1f) },
                    color = BladeCrimson,
                    modifier = Modifier.fillMaxWidth().testTag("upload_progress_bar")
                )
            }
        }

        Button(
            onClick = {
                if (currentUser == null) onRequireAuth()
                else createViewModel.publish(context, onUploadSuccess)
            },
            enabled = !uiState.isExporting && !uiState.isUploading && !uiState.isCheckingSafety,
            colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("publish_blade_button")
        ) {
            Text(
                text = when {
                    currentUser == null -> "Sign In to Publish"
                    uiState.isExporting -> "Preparing Video..."
                    uiState.isCheckingSafety -> "Checking Safety..."
                    uiState.isUploading -> "Uploading (${(uiState.uploadProgress * 100).toInt()}%)..."
                    uiState.uploadError != null -> "Retry Upload"
                    else -> strings.publishBlade
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Text(
            text = "Adult, sexually explicit, or pornographic media is strictly prohibited and blocked before upload.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
