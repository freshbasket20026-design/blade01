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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.camera.ArEffectType
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeSurfaceDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.CameraEffect
import com.example.ui.viewmodel.CameraToolPanel
import com.example.ui.viewmodel.CreateUiState
import com.example.ui.viewmodel.CreateViewModel

@Composable
fun ActiveCameraToolTray(
    uiState: CreateUiState,
    createViewModel: CreateViewModel,
    onPickGreenScreenVideo: () -> Unit,
    onOpenFilterLibrary: () -> Unit,
    onOpenBeautyStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState.activeToolPanel) {
        CameraToolPanel.NONE -> Unit
        CameraToolPanel.TIMER -> {
            ToolTrayContainer(title = "Countdown Timer", onClose = { createViewModel.closeToolPanel() }, modifier = modifier) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    createViewModel.timerOptions.forEach { sec ->
                        val selected = uiState.timerSeconds == sec
                        Surface(
                            onClick = { createViewModel.setTimerSeconds(sec) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) TextWhite else Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("timer_option_$sec")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    text = if (sec == 0) "Off" else "${sec}s",
                                    color = if (selected) Color.Black else TextWhite,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
        CameraToolPanel.SPEED -> {
            ToolTrayContainer(title = "Recording Speed", onClose = { createViewModel.closeToolPanel() }, modifier = modifier) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    createViewModel.speedOptions.forEach { spd ->
                        val selected = uiState.speedMultiplier == spd
                        val label = if (spd == 1f) "1x" else "${spd}x"
                        Surface(
                            onClick = { createViewModel.setSpeedMultiplier(spd) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) TextWhite else Color.Transparent,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("speed_option_$label")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.Black else TextWhite.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
        CameraToolPanel.FILTERS -> {
            ToolTrayContainer(
                title = "Live Filters (${uiState.selectedFilter})",
                onClose = { createViewModel.closeToolPanel() },
                trailingAction = {
                    TextButton(onClick = onOpenFilterLibrary, modifier = Modifier.testTag("open_filter_library")) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = BladeCyan, modifier = Modifier.size(16.dp))
                        Text(" All 29", color = BladeCyan, fontWeight = FontWeight.Bold)
                    }
                },
                modifier = modifier
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(createViewModel.filterPresets.take(12), key = { it.id }) { preset ->
                        val selected = uiState.selectedFilter.equals(preset.name, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { createViewModel.updateFilter(preset.name) }
                                .padding(4.dp)
                                .testTag("filter_option_${preset.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(preset.swatchStart), Color(preset.swatchEnd))))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) TextWhite else Color.White.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = TextWhite, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = preset.name,
                                color = if (selected) TextWhite else TextWhite.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            )
                        }
                    }
                }
                Slider(
                    value = uiState.filterIntensity,
                    onValueChange = { createViewModel.updateFilterIntensity(it) },
                    colors = SliderDefaults.colors(thumbColor = TextWhite, activeTrackColor = BladeCrimson)
                )
            }
        }
        CameraToolPanel.BEAUTIFY -> {
            ToolTrayContainer(
                title = "Beautify & Bright",
                onClose = { createViewModel.closeToolPanel() },
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onOpenBeautyStudio, modifier = Modifier.testTag("open_beauty_studio")) {
                            Text("Studio", color = BladeCyan, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = uiState.beauty.enabled,
                            onCheckedChange = { on -> createViewModel.updateBeauty { it.copy(enabled = on) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = TextWhite, checkedTrackColor = BladeCrimson)
                        )
                    }
                },
                modifier = modifier
            ) {
                Text(
                    "Natural Smoothing: ${(uiState.beauty.skinSmoothing * 100).toInt()}%",
                    color = TextWhite.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = uiState.beauty.skinSmoothing,
                    enabled = uiState.beauty.enabled,
                    onValueChange = { v -> createViewModel.updateBeauty { it.copy(skinSmoothing = v) } },
                    colors = SliderDefaults.colors(thumbColor = TextWhite, activeTrackColor = BladeCyan)
                )
                Text(
                    "Front Brightness Boost: ${(uiState.beauty.brightnessBoost * 100).toInt()}%",
                    color = TextWhite.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = uiState.beauty.brightnessBoost,
                    onValueChange = { v -> createViewModel.updateBeauty { it.copy(brightnessBoost = v) } },
                    colors = SliderDefaults.colors(thumbColor = TextWhite, activeTrackColor = BladeCrimson)
                )
            }
        }
        CameraToolPanel.AR_FACE -> {
            ToolTrayContainer(title = "AR Face Masks & Stickers", onClose = { createViewModel.closeToolPanel() }, modifier = modifier) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ArEffectType.entries, key = { it.name }) { ar ->
                        val selected = uiState.arEffect == ar
                        Surface(
                            onClick = { createViewModel.setArEffect(ar) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) BladeCrimson else Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, if (selected) TextWhite else Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.testTag("ar_option_${ar.id}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(ar.badge, fontSize = 20.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(ar.label, color = TextWhite, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        CameraToolPanel.GREEN_SCREEN -> {
            ToolTrayContainer(title = "Green Screen Studio Backdrop", onClose = { createViewModel.closeToolPanel() }, modifier = modifier) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Surface(
                            onClick = { createViewModel.clearGreenScreen() },
                            shape = RoundedCornerShape(12.dp),
                            color = if (!uiState.greenScreenEnabled) TextWhite else Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.size(width = 74.dp, height = 64.dp).testTag("green_screen_none")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.Block, contentDescription = "No backdrop", tint = if (!uiState.greenScreenEnabled) Color.Black else TextWhite, modifier = Modifier.size(20.dp))
                                Text("None", color = if (!uiState.greenScreenEnabled) Color.Black else TextWhite, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick = onPickGreenScreenVideo,
                            shape = RoundedCornerShape(12.dp),
                            color = BladeCrimson.copy(alpha = 0.85f),
                            modifier = Modifier.size(width = 86.dp, height = 64.dp).testTag("green_screen_pick_video")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = "Pick video backdrop", tint = TextWhite, modifier = Modifier.size(20.dp))
                                Text("Video Clip", color = TextWhite, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(createViewModel.greenScreenPresets, key = { it.id }) { preset ->
                        val selected = uiState.greenScreenPreset?.id == preset.id
                        Box(
                            modifier = Modifier
                                .size(width = 82.dp, height = 64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(Color(preset.startArgb), Color(preset.endArgb))))
                                .border(if (selected) 2.5.dp else 1.dp, if (selected) TextWhite else Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .clickable { createViewModel.setGreenScreenPreset(preset) }
                                .padding(6.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(preset.name, color = TextWhite, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolTrayContainer(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, color = TextWhite, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingAction?.invoke()
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close panel", tint = TextWhite, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsGallerySheet(
    uiState: CreateUiState,
    createViewModel: CreateViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeCategory by remember { mutableStateOf("Trending") }
    val filteredEffects = remember(activeCategory, createViewModel.effectsCatalog) {
        createViewModel.effectsCatalog.filter { it.category == activeCategory }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BladeSurfaceDark, contentColor = TextWhite) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AR & Face Effects", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextWhite)
                if (uiState.selectedEffect != null || uiState.arEffect != ArEffectType.NONE) {
                    OutlinedButton(onClick = { createViewModel.selectEffect(null); onDismiss() }, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))) {
                        Text("Clear AR", color = TextWhite)
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(createViewModel.effectCategories) { cat ->
                    FilterChip(
                        selected = activeCategory == cat,
                        onClick = { activeCategory = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BladeCrimson, selectedLabelColor = TextWhite)
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                items(filteredEffects, key = { it.id }) { effect: CameraEffect ->
                    val selected = uiState.selectedEffect?.id == effect.id
                    Surface(
                        onClick = { createViewModel.selectEffect(if (selected) null else effect); onDismiss() },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) BladeCrimson.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) TextWhite else Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.testTag("effect_card_${effect.id}")
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(effect.tintArgb).copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) { Text(effect.badge, fontSize = 22.sp) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(effect.name, color = TextWhite, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                Text(effect.description, color = TextMutedDark, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}
