package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.bladeStrings
import com.example.data.model.UserProfile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.example.ui.components.BladeAvatar
import com.example.ui.components.ProfilePhotoCropDialog
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeEmerald
import com.example.ui.theme.BladeGold
import com.example.ui.viewmodel.ProfileViewModel

@Composable
fun EditProfileScreen(
    currentUser: UserProfile,
    profileViewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSaved: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val context = LocalContext.current
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()

    var displayName by remember { mutableStateOf(currentUser.displayName) }
    var username by remember { mutableStateOf(currentUser.username) }
    var bio by remember { mutableStateOf(currentUser.bio) }
    var isPrivate by remember { mutableStateOf(currentUser.isPrivate) }
    var pickedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    var errorBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            if (!msg.contains("updated", ignoreCase = true)) {
                errorBanner = msg
            }
            profileViewModel.clearFeedback()
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingCropUri = uri
            errorBanner = null
        }
    }

    pendingCropUri?.let { rawUri ->
        ProfilePhotoCropDialog(
            imageUri = rawUri,
            onDismiss = { pendingCropUri = null },
            onCropped = { croppedUri ->
                pendingCropUri = null
                pickedAvatarUri = croppedUri
                errorBanner = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(strings.editProfile, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.clickable {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentAlignment = Alignment.BottomEnd
            ) {
                BladeAvatar(
                    avatarUrl = pickedAvatarUri?.toString() ?: currentUser.avatarUrl,
                    username = username,
                    size = 96.dp
                )
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Change Photo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(28.dp)
                        .background(BladeCrimson, androidx.compose.foundation.shape.CircleShape)
                        .padding(5.dp)
                )
            }
            TextButton(
                onClick = {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Text("Upload Profile Photo", color = BladeCrimson)
            }
        }

        errorBanner?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Bio") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Private Account", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Only approved followers can watch private posts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
        }

        val vStatus = currentUser.resolvedVerificationStatus
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = when (vStatus) {
                            "verified" -> BladeEmerald
                            "rejected" -> MaterialTheme.colorScheme.error
                            else -> BladeGold
                        }
                    )
                    Column {
                        Text(
                            text = when (vStatus) {
                                "verified" -> "Verified Creator"
                                "rejected" -> "Verification Rejected"
                                else -> "Verification Pending"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = when (vStatus) {
                                "verified" -> "Your account displays the verified badge"
                                "rejected" -> "Submit a fresh verification review request"
                                else -> "Awaiting admin review"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (vStatus == "rejected") {
                    OutlinedButton(
                        onClick = { profileViewModel.requestVerification(onSaved) },
                        enabled = !uiState.isSavingProfile
                    ) {
                        Text("Re-apply")
                    }
                }
            }
        }

        Button(
            onClick = {
                profileViewModel.saveProfileEdits(
                    displayName = displayName,
                    username = currentUser.username,
                    bio = bio,
                    isPrivate = isPrivate,
                    newAvatarUri = pickedAvatarUri,
                    contentResolver = context.contentResolver,
                    onSaved = { updated ->
                        onSaved(updated)
                        onBack()
                    }
                )
            },
            enabled = !uiState.isSavingProfile,
            colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (uiState.isSavingProfile) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Save Changes")
        }
    }
}
