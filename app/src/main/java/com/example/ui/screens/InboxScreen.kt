package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.bladeStrings
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.BladeAvatar
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeGold
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.InboxViewModel

@Composable
fun InboxScreen(
    inboxViewModel: InboxViewModel,
    currentUser: UserProfile?,
    onNavigateToAuth: () -> Unit,
    onOpenChat: (conversationId: String) -> Unit,
    onOpenCreator: (userId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val uiState by inboxViewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentUser?.id) {
        if (currentUser != null) {
            inboxViewModel.loadInbox()
        }
    }

    DisposableEffect(currentUser?.id) {
        if (currentUser != null) {
            inboxViewModel.startInboxRealtime()
        }
        onDispose {
            inboxViewModel.stopInboxRealtime()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Text(
            text = strings.navInbox,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (currentUser == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = BladeCrimson,
                        modifier = Modifier.size(52.dp)
                    )
                    Text("Sign in to view notifications & messages", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = onNavigateToAuth,
                        colors = ButtonDefaults.buttonColors(containerColor = BladeCrimson)
                    ) {
                        Text(strings.signIn)
                    }
                }
            }
            return
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.notificationsTitle) },
                modifier = Modifier.testTag("inbox_activity_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(strings.messagesTitle) },
                modifier = Modifier.testTag("inbox_messages_tab")
            )
        }

        if (selectedTab == 0) {
            val filters = listOf("All", "like", "comment", "follow", "gift")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { f ->
                    FilterChip(
                        selected = uiState.selectedFilter == f,
                        onClick = { inboxViewModel.setFilter(f) },
                        label = { Text(f.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BladeCrimson,
                            selectedLabelColor = TextWhite
                        )
                    )
                }
            }

            when (val state = uiState.notificationsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BladeCrimson)
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    val filtered = if (uiState.selectedFilter == "All") state.data
                    else state.data.filter { it.type == uiState.selectedFilter }

                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No notifications yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { notif ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.clickable {
                                        notif.actorId?.let(onOpenCreator)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BladeAvatar(
                                            avatarUrl = notif.actor?.avatarUrl ?: "",
                                            username = notif.actor?.username ?: notif.title,
                                            size = 42.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@${notif.actor?.username ?: "blade"} ${notif.body}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                            Text(
                                                text = notif.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val icon = when (notif.type) {
                                            "like" -> Icons.Filled.Favorite to BladeCrimson
                                            "follow" -> Icons.Filled.PersonAdd to BladeCyan
                                            "gift" -> Icons.Filled.CardGiftcard to BladeGold
                                            else -> Icons.Filled.ChatBubble to MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Icon(icon.first, contentDescription = null, tint = icon.second, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            when (val convState = uiState.conversationsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BladeCrimson)
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(convState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    if (convState.data.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.MailOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("No direct messages yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Visit any creator's profile and tap Message to start chatting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(convState.data, key = { it.id }) { conv ->
                                val other = conv.otherUser
                                Surface(
                                    onClick = { onOpenChat(conv.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BladeAvatar(
                                            avatarUrl = other?.avatarUrl ?: "",
                                            username = other?.username ?: "User",
                                            size = 46.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                         Column(modifier = Modifier.weight(1f)) {
                                             Text(
                                                 text = other?.displayName?.ifBlank { "@${other.username}" } ?: "Creator",
                                                 style = MaterialTheme.typography.titleSmall
                                             )
                                             Text(
                                                 text = conv.lastMessage.ifBlank { "Tap to chat" },
                                                 style = MaterialTheme.typography.bodySmall,
                                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                         }
                                         if (conv.unreadCount > 0) {
                                             Spacer(Modifier.width(8.dp))
                                             Badge(
                                                 containerColor = BladeCrimson,
                                                 contentColor = TextWhite
                                             ) {
                                                 Text(if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString())
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
    }
}
