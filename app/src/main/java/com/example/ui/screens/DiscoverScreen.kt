package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.i18n.bladeStrings
import com.example.data.model.SoundItem
import com.example.data.model.UiState
import com.example.data.model.VideoItem
import com.example.ui.components.BladeAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.DiscoverViewModel

@Composable
fun DiscoverScreen(
    discoverViewModel: DiscoverViewModel,
    onVideoClick: (startIndex: Int) -> Unit,
    onCreatorClick: (String) -> Unit,
    onUseSound: (SoundItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val uiState by discoverViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Search Bar
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { discoverViewModel.onQueryChanged(it) },
            placeholder = { Text(strings.searchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.query.isNotBlank() || uiState.activeHashtag != null || uiState.activeSoundId != null) {
                    IconButton(onClick = { discoverViewModel.clearFilters() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("discover_search_input")
        )

        // Category Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.categories) { category ->
                val selected = uiState.selectedCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { discoverViewModel.selectCategory(category) },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BladeCrimson,
                        selectedLabelColor = TextWhite
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val gridColumns = if (maxWidth >= 700.dp) 3 else 2

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Trending Hashtags
                if (uiState.hashtags.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.trendingHashtags, style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.hashtags, key = { it.id }) { tag ->
                                    Surface(
                                        onClick = { discoverViewModel.filterByHashtag(tag.tag) },
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Tag,
                                                contentDescription = null,
                                                tint = BladeCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "${tag.tag} • ${formatCompactCount(tag.viewsCount.toInt())}",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Popular Creators
                if (uiState.creators.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.popularCreators, style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(uiState.creators, key = { it.id }) { creator ->
                                    Surface(
                                        onClick = { onCreatorClick(creator.id) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 2.dp,
                                        modifier = Modifier.width(126.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            BladeAvatar(
                                                avatarUrl = creator.avatarUrl,
                                                username = creator.username,
                                                size = 52.dp
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "@${creator.username}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (creator.isVerified) {
                                                    Spacer(Modifier.width(3.dp))
                                                    VerifiedBadge(size = 12.dp)
                                                }
                                            }
                                            Text(
                                                text = "${formatCompactCount(creator.followersCount)} ${strings.followers}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Trending Sounds
                if (uiState.sounds.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.trendingSounds, style = MaterialTheme.typography.titleMedium)
                            uiState.sounds.take(4).forEach { sound ->
                                Surface(
                                    onClick = { discoverViewModel.filterBySound(sound.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    Brush.linearGradient(listOf(BladeCrimson, BladeCyan))
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextWhite)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sound.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${sound.artist} • ${sound.usageCount} Blades",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = { onUseSound(sound) }) {
                                            Text(strings.useSound, color = BladeCrimson)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Videos Section Header
                item {
                    Text(strings.discoverVideos, style = MaterialTheme.typography.titleMedium)
                }

                when (val videosState = uiState.videosState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = BladeCrimson)
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(videosState.message, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { discoverViewModel.refresh() }) { Text("Retry") }
                            }
                        }
                    }
                    is UiState.Success -> {
                        if (videosState.data.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("No matching Blades found", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Try another keyword, hashtag, or upload new videos.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            val rows = videosState.data.withIndex().chunked(gridColumns)
                            items(rows) { rowVideos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowVideos.forEach { indexedVideo ->
                                        DiscoverVideoCard(
                                            video = indexedVideo.value,
                                            onClick = { onVideoClick(indexedVideo.index) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(gridColumns - rowVideos.size) {
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
}

@Composable
fun DiscoverVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 14f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141724))
            .clickable(onClick = onClick)
            .testTag("discover_video_${video.id}")
    ) {
        if (video.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E2235), Color(0xFF12141E))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TextWhite.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val cardLabel = video.title.ifBlank { video.caption }
            if (cardLabel.isNotBlank()) {
                Text(
                    text = cardLabel,
                    color = TextWhite,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (video.title.isNotBlank()) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${video.creator?.username ?: "creator"}",
                    color = TextWhite.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = BladeCrimson,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = formatCompactCount(video.likesCount),
                        color = TextWhite,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
            }
        }
    }
}
