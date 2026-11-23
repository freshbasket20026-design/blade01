package com.example.ui.screens.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.AiEffectType
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
fun AiEffectsModalSheet(
    uiState: CreateUiState,
    createViewModel: CreateViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var stylePrompt by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BladeSurfaceDark,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = BladeCyan)
                Text("AI Video Studio", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BladeCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, BladeCyan.copy(alpha = 0.45f))
            ) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = BladeCyan, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Sample Mode: Running on-device frame analysis & ML segmentation. Cloud Gemini AI requires Firebase.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextWhite.copy(alpha = 0.9f)
                    )
                }
            }

            OutlinedTextField(
                value = stylePrompt,
                onValueChange = { stylePrompt = it },
                placeholder = { Text("Optional style prompt (e.g. Cyberpunk, Oil Painting, Warm Reels)", color = TextMutedDark) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = BladeCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("ai_style_prompt_input")
            )

            if (uiState.isProcessingAi) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.08f)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(uiState.aiProgressLabel.ifBlank { "Processing..." }, color = BladeCyan, style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(progress = { uiState.aiProgress }, color = BladeCyan, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            uiState.aiError?.let { err ->
                Surface(shape = RoundedCornerShape(12.dp), color = BladeCrimson.copy(alpha = 0.2f), border = BorderStroke(1.dp, BladeCrimson)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(err, color = TextWhite, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            uiState.lastAiEffectType?.let { createViewModel.runAiEffect(context.contentResolver, it, stylePrompt) }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            uiState.aiOutput?.let { out ->
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.08f), border = BorderStroke(1.dp, BladeGold.copy(alpha = 0.55f))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(out.summary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = BladeGold)
                        out.processedPreviewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "AI processed keyframe",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                        if (out.detectedSceneTimestampsMs.isNotEmpty()) {
                            Text("Suggested cut points: ${out.detectedSceneTimestampsMs.joinToString { "${it / 1000}s" }}", style = MaterialTheme.typography.labelSmall, color = TextMutedDark)
                        }
                        Button(
                            onClick = { createViewModel.applyAiOutputToStudio(); onDismiss() },
                            colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson),
                            modifier = Modifier.fillMaxWidth().testTag("ai_apply_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply to Studio Timeline", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(AiEffectType.entries, key = { it.name }) { effect ->
                    val active = uiState.lastAiEffectType == effect
                    Surface(
                        onClick = {
                            if (!uiState.isProcessingAi) {
                                createViewModel.runAiEffect(context.contentResolver, effect, stylePrompt)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) BladeCyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, if (active) BladeCyan else Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.testTag("ai_effect_${effect.name}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(effect.badge, fontSize = 22.sp)
                            Text(effect.title, color = TextWhite, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(effect.subtitle, color = TextMutedDark, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
