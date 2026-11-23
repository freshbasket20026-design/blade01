package com.example.ui.screens.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.camera.FilterCategory
import com.example.core.camera.VideoFilterPreset
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeGold
import com.example.ui.theme.BladeSurfaceDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.CreateUiState
import com.example.ui.viewmodel.CreateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterLibraryModalSheet(
    uiState: CreateUiState,
    createViewModel: CreateViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var favoritesOnly by remember { mutableStateOf(false) }

    val filtered = remember(
        uiState.filterSearchQuery,
        uiState.selectedFilterCategory,
        uiState.favoriteFilterIds,
        favoritesOnly
    ) {
        val q = uiState.filterSearchQuery.trim().lowercase()
        createViewModel.filterPresets.filter { preset ->
            val matchSearch = q.isBlank() ||
                preset.name.lowercase().contains(q) ||
                preset.description.lowercase().contains(q)
            val matchCat = when {
                favoritesOnly -> preset.id in uiState.favoriteFilterIds
                uiState.selectedFilterCategory == FilterCategory.ALL.label -> true
                uiState.selectedFilterCategory == FilterCategory.TRENDING.label -> preset.isTrending
                else -> preset.category.label == uiState.selectedFilterCategory
            }
            matchSearch && matchCat
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BladeSurfaceDark,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Filter Library", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "${uiState.selectedFilter} • ${(uiState.filterIntensity * 100).toInt()}% intensity",
                        color = TextMutedDark,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Favorites") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BladeGold.copy(alpha = 0.25f),
                        selectedLabelColor = BladeGold,
                        selectedLeadingIconColor = BladeGold
                    )
                )
            }

            // Intensity slider for live selected filter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Intensity", style = MaterialTheme.typography.labelMedium, color = TextWhite)
                Slider(
                    value = uiState.filterIntensity,
                    onValueChange = { createViewModel.updateFilterIntensity(it) },
                    colors = SliderDefaults.colors(thumbColor = TextWhite, activeTrackColor = BladeCrimson),
                    modifier = Modifier.weight(1f).testTag("filter_intensity_slider")
                )
            }

            OutlinedTextField(
                value = uiState.filterSearchQuery,
                onValueChange = { createViewModel.updateFilterSearch(it) },
                placeholder = { Text("Search 29 filters (Sunset, Vintage, Portrait...)", color = TextMutedDark) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMutedDark) },
                trailingIcon = {
                    if (uiState.filterSearchQuery.isNotBlank()) {
                        IconButton(onClick = { createViewModel.updateFilterSearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextWhite)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = BladeCrimson,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("filter_search_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(createViewModel.filterCategories, key = { it.name }) { cat ->
                    FilterChip(
                        selected = !favoritesOnly && uiState.selectedFilterCategory == cat.label,
                        onClick = {
                            favoritesOnly = false
                            createViewModel.updateFilterCategory(cat)
                        },
                        label = { Text(cat.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BladeCrimson,
                            selectedLabelColor = TextWhite
                        )
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(filtered, key = { it.id }) { preset: VideoFilterPreset ->
                    val selected = uiState.selectedFilter.equals(preset.name, ignoreCase = true)
                    val isFav = preset.id in uiState.favoriteFilterIds
                    val isDownloaded = preset.id in uiState.downloadedFilterIds
                    val isDownloading = preset.id in uiState.downloadingFilterIds

                    Surface(
                        onClick = {
                            if (!isDownloaded && !isDownloading) createViewModel.downloadFilter(preset.id)
                            createViewModel.updateFilter(preset.name)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) BladeCrimson.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) TextWhite else Color.White.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier.testTag("filter_card_${preset.id}")
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(Color(preset.swatchStart), Color(preset.swatchEnd)))
                                    )
                            ) {
                                IconButton(
                                    onClick = { createViewModel.toggleFavoriteFilter(preset.id) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "Favorite ${preset.name}",
                                        tint = if (isFav) BladeGold else TextWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = TextWhite, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${preset.sizeKb}KB",
                                    color = TextMutedDark,
                                    fontSize = 10.sp
                                )
                                when {
                                    isDownloading -> CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = BladeCyan
                                    )
                                    isDownloaded -> Icon(
                                        Icons.Filled.DownloadDone,
                                        contentDescription = "Downloaded",
                                        tint = BladeCyan,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    else -> Icon(
                                        Icons.Filled.Download,
                                        contentDescription = "Download ${preset.name}",
                                        tint = TextWhite.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable { createViewModel.downloadFilter(preset.id) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeautyStudioModalSheet(
    uiState: CreateUiState,
    createViewModel: CreateViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val b = uiState.beauty

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BladeSurfaceDark,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Beauty & Makeup Studio", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Natural real-time ML face tracking", color = TextMutedDark, style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { createViewModel.resetBeauty() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset beauty", tint = TextWhite)
                    }
                    Switch(
                        checked = b.enabled,
                        onCheckedChange = { on -> createViewModel.updateBeauty { it.copy(enabled = on) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = TextWhite, checkedTrackColor = BladeCrimson),
                        modifier = Modifier.testTag("beauty_master_switch")
                    )
                }
            }

            BeautySliderRow("Overall Beauty Intensity", b.intensity, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(intensity = v) }
            }
            BeautySliderRow("Skin Smoothing", b.skinSmoothing, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(skinSmoothing = v) }
            }
            BeautySliderRow("Blemish Reduction", b.blemishReduction, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(blemishReduction = v) }
            }
            BeautySliderRow("Face Lighting (Low-Light Lift)", b.faceLighting, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(faceLighting = v) }
            }
            BeautySliderRow("Camera Brightness Boost", b.brightnessBoost, true) { v ->
                createViewModel.updateBeauty { it.copy(brightnessBoost = v) }
            }
            BeautySliderRow("Eye Enhancement", b.eyeEnhancement, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(eyeEnhancement = v) }
            }
            BeautySliderRow("Teeth Whitening", b.teethWhitening, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(teethWhitening = v) }
            }
            BeautySliderRow("Blush", b.blushIntensity, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(blushIntensity = v) }
            }
            BeautySliderRow("Eyeshadow", b.eyeshadowIntensity, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(eyeshadowIntensity = v) }
            }
            BeautySliderRow("Eyebrow Definition", b.eyebrowIntensity, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(eyebrowIntensity = v) }
            }
            BeautySliderRow("Face Contouring", b.faceContourIntensity, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(faceContourIntensity = v) }
            }
            BeautySliderRow("Lipstick Tint", b.lipstickIntensity, b.enabled) { v ->
                createViewModel.updateBeauty { it.copy(lipstickIntensity = v) }
            }

            Text("Lipstick Shade", style = MaterialTheme.typography.labelMedium, color = TextWhite)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                createViewModel.lipstickShades.forEach { shade ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(shade))
                            .border(
                                width = if (b.lipstickColorArgb == shade) 2.5.dp else 1.dp,
                                color = if (b.lipstickColorArgb == shade) TextWhite else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { createViewModel.updateBeauty { it.copy(lipstickColorArgb = shade) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun BeautySliderRow(
    label: String,
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = if (enabled) TextWhite else TextMutedDark, style = MaterialTheme.typography.labelMedium)
            Text("${(value * 100).toInt()}%", color = BladeCyan, style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            enabled = enabled,
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = TextWhite, activeTrackColor = BladeCyan)
        )
    }
}
