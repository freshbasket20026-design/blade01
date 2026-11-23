package com.example.ui.screens

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.i18n.bladeStrings
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.ui.components.BladeAvatar
import com.example.ui.components.ProfilePhotoCropDialog
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.FollowListType
import com.example.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    targetUserId: String?,
    currentUser: UserProfile?,
    profileViewModel: ProfileViewModel,
    isOtherUserScreen: Boolean,
    onBack: (() -> Unit)? = null,
    onNavigateToAuth: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onMessageCreator: (String) -> Unit,
    onVideoClick: (tabIndex: Int, startIndex: Int) -> Unit,
    onVideoDeleted: (String) -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val context = LocalContext.current
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val effectiveId = targetUserId ?: currentUser?.id
    val isOwnProfile = effectiveId != null && effectiveId == currentUser?.id

    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingDeleteVideo by remember { mutableStateOf<VideoItem?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingCropUri = uri
        }
    }

    pendingCropUri?.let { rawUri ->
        ProfilePhotoCropDialog(
            imageUri = rawUri,
            onDismiss = { pendingCropUri = null },
            onCropped = { croppedUri ->
                pendingCropUri = null
                profileViewModel.uploadProfilePhoto(
                    photoUri = croppedUri,
                    contentResolver = context.contentResolver,
                    onUploaded = onProfileUpdated
                )
            }
        )
    }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            onShowMessage(msg)
            profileViewModel.clearFeedback()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(effectiveId) {
        if (effectiveId != null) {
            profileViewModel.loadProfile(effectiveId)
        }
    }

    DisposableEffect(lifecycleOwner, effectiveId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && effectiveId != null) {
                profileViewModel.loadProfile(effectiveId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (effectiveId == null) {
        LaunchedEffect(Unit) {
            onNavigateToAuth()
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val pState = uiState.profileState) {
            is UiState.Loading -> {
                CircularProgressIndicator(
                    color = BladeCrimson,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(pState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { profileViewModel.loadProfile(effectiveId) }) { Text("Retry") }
                }
            }
            is UiState.Success -> {
                val profile = pState.data
                val displayList = when (selectedTab) {
                    0 -> uiState.uploadedVideos
                    1 -> uiState.likedVideos
                    else -> uiState.savedVideos
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = if (maxWidth >= 680.dp) 4 else 3

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Cover & Header
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(145.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                BladeCrimson.copy(alpha = 0.65f),
                                                Color(0xFF151829),
                                                BladeCyan.copy(alpha = 0.45f)
                                            )
                                        )
                                    )
                            ) {
                                if (profile.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = profile.coverUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isOtherUserScreen && onBack != null) {
                                        IconButton(onClick = onBack) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                                        }
                                    } else {
                                        Spacer(Modifier.width(12.dp))
                                    }

                                    if (isOwnProfile) {
                                        IconButton(
                                            onClick = onNavigateToSettings,
                                            modifier = Modifier.testTag("profile_settings_button")
                                        ) {
                                            Icon(Icons.Filled.Settings, contentDescription = strings.settings, tint = TextWhite)
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (isOwnProfile && !uiState.isUploadingPhoto) {
                                                Modifier
                                                    .clickable {
                                                        photoPicker.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    }
                                                    .testTag("profile_avatar_upload")
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    BladeAvatar(
                                        avatarUrl = profile.avatarUrl,
                                        username = profile.username,
                                        size = 90.dp
                                    )
                                    if (uiState.isUploadingPhoto) {
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.55f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = TextWhite,
                                                strokeWidth = 2.5.dp,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    } else if (isOwnProfile) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(BladeCrimson)
                                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PhotoCamera,
                                                contentDescription = "Upload profile photo",
                                                tint = TextWhite,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.displayName.ifBlank { "@${profile.username}" },
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    if (profile.isVerified) {
                                        Spacer(Modifier.width(6.dp))
                                        VerifiedBadge(size = 18.dp)
                                    }
                                    if (profile.isPrivate) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.Lock, contentDescription = "Private", modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = "@${profile.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (profile.bio.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = profile.bio,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatBlock(
                                        value = formatCompactCount(profile.followingCount),
                                        label = strings.following,
                                        onClick = {
                                            profileViewModel.openFollowList(profile.id, FollowListType.FOLLOWING)
                                        },
                                        modifier = Modifier.testTag("stat_following")
                                    )
                                    StatBlock(
                                        value = formatCompactCount(profile.followersCount),
                                        label = strings.followers,
                                        onClick = {
                                            profileViewModel.openFollowList(profile.id, FollowListType.FOLLOWERS)
                                        },
                                        modifier = Modifier.testTag("stat_followers")
                                    )
                                    StatBlock(
                                        value = formatCompactCount(profile.totalLikes),
                                        label = strings.likes
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                if (isOwnProfile) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = onNavigateToEditProfile,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("edit_profile_button")
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.editProfile, maxLines = 1)
                                        }
                                        Button(
                                            onClick = {
                                                photoPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            enabled = !uiState.isUploadingPhoto,
                                            colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson, contentColor = TextWhite),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("upload_profile_photo_button")
                                        ) {
                                            if (uiState.isUploadingPhoto) {
                                                CircularProgressIndicator(Modifier.size(16.dp), color = TextWhite, strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (uiState.isUploadingPhoto) "Uploading..." else "Upload Photo", maxLines = 1)
                                        }
                                    }
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { profileViewModel.toggleFollow(profile.id) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (uiState.isFollowing) MaterialTheme.colorScheme.surfaceVariant else BladeCrimson
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(if (uiState.isFollowing) strings.following else strings.follow)
                                        }
                                        OutlinedButton(
                                            onClick = { onMessageCreator(profile.id) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Mail, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Message")
                                        }
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        // Tabs: Videos | Liked | Saved
                        item {
                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Filled.GridOn, contentDescription = strings.videosTab) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Filled.Favorite, contentDescription = strings.likedTab) }
                                )
                                if (isOwnProfile) {
                                    Tab(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        icon = { Icon(Icons.Filled.Bookmark, contentDescription = strings.savedTab) }
                                    )
                                }
                            }
                        }

                        if (displayList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No videos in this tab yet",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            val rows = displayList.withIndex().chunked(columns)
                            items(rows) { rowVideos ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    rowVideos.forEach { indexedVideo ->
                                        val video = indexedVideo.value
                                        ProfileVideoGridTile(
                                            video = video,
                                            showDeleteAction = isOwnProfile && selectedTab == 0,
                                            isDeleting = uiState.deletingVideoId == video.id,
                                            onDeleteClick = { pendingDeleteVideo = video },
                                            onClick = { onVideoClick(selectedTab, indexedVideo.index) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(columns - rowVideos.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val activeFollowListType = uiState.followListType
    val loadedProfile = (uiState.profileState as? UiState.Success)?.data
    if (activeFollowListType != null && loadedProfile != null) {
        ModalBottomSheet(
            onDismissRequest = { profileViewModel.closeFollowList() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = if (activeFollowListType == FollowListType.FOLLOWERS) strings.followers else strings.following,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                when (val fState = uiState.followListState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BladeCrimson)
                        }
                    }
                    is UiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(fState.message, color = MaterialTheme.colorScheme.error)
                            Button(
                                onClick = { profileViewModel.openFollowList(loadedProfile.id, activeFollowListType) },
                                colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    is UiState.Success -> {
                        if (fState.data.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (activeFollowListType == FollowListType.FOLLOWERS) {
                                        "No followers yet"
                                    } else {
                                        "Not following anyone yet"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(380.dp)
                            ) {
                                items(fState.data, key = { it.id }) { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                profileViewModel.closeFollowList()
                                                onOpenUserProfile(user.id)
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        BladeAvatar(
                                            avatarUrl = user.avatarUrl,
                                            username = user.username,
                                            size = 46.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = user.displayName.ifBlank { "@${user.username}" },
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (user.isVerified) {
                                                    Spacer(Modifier.width(4.dp))
                                                    VerifiedBadge(size = 14.dp)
                                                }
                                            }
                                            Text(
                                                text = "@${user.username}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteVideo?.let { target ->
        val isDeleting = uiState.deletingVideoId == target.id
        val label = target.title.ifBlank { target.caption }.ifBlank { "this video" }
        AlertDialog(
            onDismissRequest = { if (!isDeleting) pendingDeleteVideo = null },
            title = { Text("Delete Video?") },
            text = {
                Text("Are you sure you want to permanently delete \"$label\"? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        profileViewModel.deleteUploadedVideo(target.id) {
                            onVideoDeleted(target.id)
                            pendingDeleteVideo = null
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson, contentColor = TextWhite),
                    modifier = Modifier.testTag("profile_confirm_delete_${target.id}")
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = TextWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (isDeleting) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteVideo = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileVideoGridTile(
    video: VideoItem,
    showDeleteAction: Boolean,
    isDeleting: Boolean,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fallbackFrame by produceState<Bitmap?>(initialValue = null, video.videoUrl, video.thumbnailUrl) {
        if (video.thumbnailUrl.isBlank() && video.videoUrl.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(video.videoUrl, emptyMap())
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(9f / 14f)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1D2234), Color(0xFF131622))
                )
            )
            .clickable(enabled = !isDeleting, onClick = onClick)
            .testTag("profile_video_tile_${video.id}")
    ) {
        if (video.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title.ifBlank { video.caption },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (fallbackFrame != null) {
            Image(
                bitmap = fallbackFrame!!.asImageBitmap(),
                contentDescription = video.title.ifBlank { video.caption },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (showDeleteAction) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(enabled = !isDeleting, onClick = onDeleteClick)
                    .padding(6.dp)
                    .testTag("profile_grid_delete_${video.id}"),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f))
                        .border(1.dp, TextWhite.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete video",
                        tint = TextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (isDeleting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BladeCrimson,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val label = video.title.ifBlank { video.caption }
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    color = TextWhite,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = formatCompactCount(video.viewsCount),
                    color = TextWhite,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
