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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.bladeStrings
import com.example.data.model.AdminAppSettings
import com.example.data.model.AdminDashboardStats
import com.example.data.model.UiState
import com.example.data.model.VideoItem
import com.example.ui.components.BladeAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeEmerald
import com.example.ui.theme.BladeGold
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.AdminTab
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun AdminScreen(
    adminViewModel: AdminViewModel,
    isDarkMode: Boolean,
    languageCode: String,
    onToggleDarkMode: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onVideoDeleted: (String) -> Unit,
    onFeedMutated: () -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val uiState by adminViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        adminViewModel.loadDashboard()
    }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            onShowMessage(msg)
            adminViewModel.clearFeedback()
        }
    }

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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("admin_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = BladeRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = strings.adminPanel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                if (uiState.adminId.isNotBlank()) {
                    Text(
                        text = "Admin: ${uiState.adminId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(
                    onClick = { adminViewModel.loadDashboard() },
                    modifier = Modifier.testTag("admin_refresh_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh dashboard")
                }
                IconButton(
                    onClick = { adminViewModel.logoutAdmin(onLoggedOut) },
                    modifier = Modifier.testTag("admin_topbar_logout_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Sign out Admin",
                        tint = BladeRed
                    )
                }
            }
        }

        if (uiState.isPerformingAction) {
            LinearProgressIndicator(color = BladeRed, modifier = Modifier.fillMaxWidth())
        }

        val dashSuccess = (uiState.dashboardState as? UiState.Success)?.data
        val pendingCount = dashSuccess?.pendingReportsCount ?: 0
        val pendingVerifyCount = dashSuccess?.pendingVerificationUsersCount ?: 0

        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTab.ordinal,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AdminTab.entries.forEach { tab ->
                val title = when (tab) {
                    AdminTab.OVERVIEW -> "Overview"
                    AdminTab.USERS -> "Users"
                    AdminTab.VIDEOS -> "Videos"
                    AdminTab.MODERATION -> "Moderation"
                    AdminTab.SETTINGS -> "Settings"
                }
                val badgeNum = when (tab) {
                    AdminTab.USERS -> pendingVerifyCount
                    AdminTab.MODERATION -> pendingCount
                    else -> 0
                }
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { adminViewModel.selectTab(tab) },
                    text = {
                        if (badgeNum > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = if (tab == AdminTab.USERS) BladeGold else BladeRed,
                                        contentColor = TextWhite
                                    ) {
                                        Text(if (badgeNum > 99) "99+" else badgeNum.toString())
                                    }
                                }
                            ) {
                                Text(title)
                            }
                        } else {
                            Text(title)
                        }
                    },
                    modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}")
                )
            }
        }

        when (val dash = uiState.dashboardState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BladeCrimson)
                }
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(dash.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { adminViewModel.loadDashboard() },
                        colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite)
                    ) {
                        Text("Retry")
                    }
                }
            }
            is UiState.Success -> {
                val stats = dash.data
                when (uiState.selectedTab) {
                    AdminTab.OVERVIEW -> AdminOverviewTab(
                        stats = stats,
                        onSelectTab = adminViewModel::selectTab,
                        onPlayVideo = onPlayVideo
                    )
                    AdminTab.USERS -> AdminUsersTab(
                        stats = stats,
                        uiState = uiState,
                        adminViewModel = adminViewModel,
                        onPlayVideo = onPlayVideo,
                        onFeedMutated = onFeedMutated
                    )
                    AdminTab.VIDEOS -> AdminVideosTab(
                        stats = stats,
                        uiState = uiState,
                        adminViewModel = adminViewModel,
                        onPlayVideo = onPlayVideo,
                        onVideoDeleted = onVideoDeleted,
                        onFeedMutated = onFeedMutated
                    )
                    AdminTab.MODERATION -> AdminModerationTab(
                        stats = stats,
                        uiState = uiState,
                        adminViewModel = adminViewModel,
                        onPlayVideo = onPlayVideo,
                        onVideoDeleted = onVideoDeleted,
                        onFeedMutated = onFeedMutated
                    )
                    AdminTab.SETTINGS -> AdminSettingsTab(
                        adminId = uiState.adminId,
                        appSettings = stats.appSettings,
                        isDarkMode = isDarkMode,
                        languageCode = languageCode,
                        onToggleDarkMode = onToggleDarkMode,
                        onSelectLanguage = onSelectLanguage,
                        onSaveAppSettings = adminViewModel::saveAppSettings,
                        onUpdateAdminCredentials = { cur, newId, newPass ->
                            adminViewModel.updateAdminCredentials(cur, newId, newPass)
                        },
                        onLogout = { adminViewModel.logoutAdmin(onLoggedOut) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminOverviewTab(
    stats: AdminDashboardStats,
    onSelectTab: (AdminTab) -> Unit,
    onPlayVideo: (VideoItem) -> Unit
) {
    val topCreators = remember(stats.recentUsers) {
        stats.recentUsers.sortedByDescending { it.followersCount }.take(5)
    }
    val topVideos = remember(stats.recentVideos) {
        stats.recentVideos.sortedByDescending { it.viewsCount + it.likesCount }.take(5)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard("Total Users", formatCompactCount(stats.totalUsers), Modifier.weight(1f))
                AdminMetricCard("Total Videos", formatCompactCount(stats.totalVideos), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard("Total Views", formatCompactCount(stats.totalViews.toInt()), Modifier.weight(1f))
                AdminMetricCard("Total Likes", formatCompactCount(stats.totalLikes.toInt()), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard("Verified Users", formatCompactCount(stats.verifiedUsersCount), Modifier.weight(1f))
                AdminMetricCard("Pending Verify", formatCompactCount(stats.pendingVerificationUsersCount), Modifier.weight(1f))
                AdminMetricCard("Blocked Users", formatCompactCount(stats.blockedUsersCount), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard("Comments", formatCompactCount(stats.totalComments), Modifier.weight(1f))
                AdminMetricCard("Pending Reports", formatCompactCount(stats.pendingReportsCount), Modifier.weight(1f))
            }
        }

        item {
            Text(
                text = "Quick Management",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSelectTab(AdminTab.VIDEOS) },
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_quick_upload")
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Videos", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onSelectTab(AdminTab.USERS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_quick_users")
                ) {
                    Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Users", maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSelectTab(AdminTab.MODERATION) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_quick_moderation")
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Moderation", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onSelectTab(AdminTab.SETTINGS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_quick_settings")
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Controls", maxLines = 1)
                }
            }
        }

        item {
            Text(
                text = "Top Creators",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        items(topCreators, key = { "top_user_${it.id}" }) { user ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTab(AdminTab.USERS) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BladeAvatar(avatarUrl = user.avatarUrl, username = user.username, size = 40.dp)
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
                            text = "@${user.username} • ${formatCompactCount(user.followersCount)} followers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (user.isBlocked) "BLOCKED" else user.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (user.isBlocked) BladeGold else BladeEmerald
                    )
                }
            }
        }

        item {
            Text(
                text = "Top Videos",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        items(topVideos, key = { "top_video_${it.id}" }) { video ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayVideo(video) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = BladeRed)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title.ifBlank { video.caption }.ifBlank { "Untitled Video" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "By @${video.creator?.username ?: "user"} • ${formatCompactCount(video.viewsCount)} views • ${formatCompactCount(video.likesCount)} likes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSettingsTab(
    adminId: String,
    appSettings: AdminAppSettings,
    isDarkMode: Boolean,
    languageCode: String,
    onToggleDarkMode: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSaveAppSettings: (AdminAppSettings) -> Unit,
    onUpdateAdminCredentials: (currentPassword: String, newAdminId: String, newPassword: String) -> Unit,
    onLogout: () -> Unit
) {
    var allowRegistrations by remember(appSettings) { mutableStateOf(appSettings.allowRegistrations) }
    var allowVideoUploads by remember(appSettings) { mutableStateOf(appSettings.allowVideoUploads) }
    var allowComments by remember(appSettings) { mutableStateOf(appSettings.allowComments) }
    var maintenanceMode by remember(appSettings) { mutableStateOf(appSettings.maintenanceMode) }
    var announcementBanner by remember(appSettings) { mutableStateOf(appSettings.announcementBanner) }

    var currentAdminPassword by remember { mutableStateOf("") }
    var newAdminId by remember(adminId) { mutableStateOf(adminId) }
    var newAdminPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Application Controls",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    AdminSwitchRow("Allow New Account Sign-Ups", allowRegistrations) { allowRegistrations = it }
                    AdminSwitchRow("Allow Video Uploads", allowVideoUploads) { allowVideoUploads = it }
                    AdminSwitchRow("Allow Comments", allowComments) { allowComments = it }
                    AdminSwitchRow("Maintenance Mode", maintenanceMode) { maintenanceMode = it }
                    OutlinedTextField(
                        value = announcementBanner,
                        onValueChange = { announcementBanner = it },
                        label = { Text("Global Announcement Banner (optional)") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_announcement_input")
                    )
                    Button(
                        onClick = {
                            onSaveAppSettings(
                                AdminAppSettings(
                                    allowRegistrations = allowRegistrations,
                                    allowVideoUploads = allowVideoUploads,
                                    allowComments = allowComments,
                                    maintenanceMode = maintenanceMode,
                                    announcementBanner = announcementBanner.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_save_app_settings_button")
                    ) {
                        Text("Save App Controls", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Appearance & Language",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    AdminSwitchRow("Dark Mode", isDarkMode, onToggleDarkMode)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = languageCode == "en",
                            onClick = { onSelectLanguage("en") },
                            label = { Text("English") }
                        )
                        FilterChip(
                            selected = languageCode == "bn",
                            onClick = { onSelectLanguage("bn") },
                            label = { Text("বাংলা") }
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Admin Credentials",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = currentAdminPassword,
                        onValueChange = { currentAdminPassword = it },
                        label = { Text("Current Admin Password") },
                        visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        trailingIcon = {
                            IconButton(onClick = { showPasswords = !showPasswords }) {
                                Icon(
                                    imageVector = if (showPasswords) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAdminId,
                        onValueChange = { newAdminId = it },
                        label = { Text("Admin User ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAdminPassword,
                        onValueChange = { newAdminPassword = it },
                        label = { Text("New Admin Password (6+ chars)") },
                        visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            onUpdateAdminCredentials(currentAdminPassword, newAdminId, newAdminPassword)
                            currentAdminPassword = ""
                            newAdminPassword = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_update_credentials_button")
                    ) {
                        Text("Update Admin Login Credentials")
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = TextWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_logout_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log Out of Admin Panel", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AdminSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AdminMetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}
