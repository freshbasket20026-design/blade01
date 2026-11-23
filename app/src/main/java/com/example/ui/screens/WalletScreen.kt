package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.bladeStrings
import com.example.data.model.UserProfile
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeGold
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.ProfileViewModel

@Composable
fun WalletScreen(
    currentUser: UserProfile,
    profileViewModel: ProfileViewModel,
    onCoinsUpdated: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(strings.walletAndCoins, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(48.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF1E2235), Color(0xFF141624)))
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(strings.bladeCoins, color = TextWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Stars, contentDescription = null, tint = BladeGold)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${currentUser.coinsBalance}",
                                        color = TextWhite,
                                        style = MaterialTheme.typography.displaySmall
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(strings.creatorEarnings, color = TextWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = "$${String.format("%.2f", currentUser.earningsUsd)}",
                                    color = BladeGold,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }

                        Button(
                            onClick = { profileViewModel.claimRewardedAdCoins(onCoinsUpdated) },
                            colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.claimRewardCoins)
                        }
                    }
                }
            }

            item {
                Text("Recent Transactions", style = MaterialTheme.typography.titleMedium)
            }

            if (uiState.transactions.isEmpty()) {
                item {
                    Text(
                        text = "No coin or gift transactions yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.transactions, key = { it.id }) { tx ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.description.ifBlank { tx.type }, style = MaterialTheme.typography.titleSmall)
                                Text(tx.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "${if (tx.type == "reward_ad") "+" else ""}${tx.coinsAmount} Coins",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = BladeGold
                            )
                        }
                    }
                }
            }
        }
    }
}
