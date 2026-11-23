package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.i18n.BengaliBladeStrings
import com.example.core.i18n.EnglishBladeStrings
import com.example.core.i18n.LocalBladeStrings
import com.example.core.i18n.bladeStrings
import com.example.data.model.VideoItem
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.DiscoverVideoFeedScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProfileVideoFeedScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.VideoDetailPlayerScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.BladeObsidian
import com.example.ui.theme.BladeRed
import com.example.ui.theme.BladeRedAccent
import com.example.ui.theme.BladeRedDark
import com.example.ui.theme.BladeTheme
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthMode
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.BladeViewModelFactory
import com.example.ui.viewmodel.CreateViewModel
import com.example.ui.viewmodel.DiscoverViewModel
import com.example.ui.viewmodel.FeedTab
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.InboxViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ProfileViewModel
import kotlinx.serialization.Serializable

@Serializable object SplashRoute
@Serializable object HomeRoute
@Serializable object DiscoverRoute
@Serializable object CreateRoute
@Serializable object InboxRoute
@Serializable object ProfileRoute
@Serializable data class AuthRoute(
    val startWithSignUp: Boolean = true,
    val openProfileAfterAuth: Boolean = false
)
@Serializable object EditProfileRoute
@Serializable object SettingsRoute
@Serializable object WalletRoute
@Serializable object AdminRoute
@Serializable data class UserProfileRoute(val userId: String)
@Serializable data class ProfileVideoFeedRoute(
    val tabIndex: Int,
    val startIndex: Int,
    val isOwnProfile: Boolean
)
@Serializable data class DiscoverVideoFeedRoute(val startIndex: Int)
@Serializable data class ChatRoute(val conversationId: String)
@Serializable data class VideoPlayerRoute(
    val videoUrl: String,
    val title: String,
    val caption: String,
    val username: String,
    val filterName: String,
    val trimStartMs: Long,
    val trimEndMs: Long
)

class MainActivity : ComponentActivity() {
    private val _pendingOAuthUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureOAuthUri(intent)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isDarkMode by mainViewModel.isDarkMode.collectAsStateWithLifecycle()
            val languageCode by mainViewModel.languageCode.collectAsStateWithLifecycle()
            val pendingOAuthUri by _pendingOAuthUri.asStateFlow().collectAsStateWithLifecycle()

            val activeStrings = if (languageCode == "bn") BengaliBladeStrings else EnglishBladeStrings

            CompositionLocalProvider(LocalBladeStrings provides activeStrings) {
                BladeTheme(darkTheme = isDarkMode) {
                    BladeRootApp(
                        mainViewModel = mainViewModel,
                        pendingOAuthUri = pendingOAuthUri,
                        onOAuthUriConsumed = { _pendingOAuthUri.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureOAuthUri(intent)
    }

    private fun captureOAuthUri(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "com.aistudio.blade.kxmqpz" && data.host == "auth-callback") {
            _pendingOAuthUri.value = data
        }
    }
}

@Composable
private fun BladeRootApp(
    mainViewModel: MainViewModel,
    pendingOAuthUri: Uri? = null,
    onOAuthUriConsumed: () -> Unit = {}
) {
    val strings = bladeStrings()
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    val factory = remember(mainViewModel.repository) { BladeViewModelFactory(mainViewModel.repository) }

    val feedViewModel: FeedViewModel = viewModel(factory = factory)
    val discoverViewModel: DiscoverViewModel = viewModel(factory = factory)
    val createViewModel: CreateViewModel = viewModel(factory = factory)
    val inboxViewModel: InboxViewModel = viewModel(factory = factory)
    val profileViewModel: ProfileViewModel = viewModel(factory = factory)
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val adminViewModel: AdminViewModel = viewModel(factory = factory)

    val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val inboxUiState by inboxViewModel.uiState.collectAsStateWithLifecycle()
    val adminUiState by adminViewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by mainViewModel.appSettings.collectAsStateWithLifecycle()
    val isDarkMode by mainViewModel.isDarkMode.collectAsStateWithLifecycle()
    val languageCode by mainViewModel.languageCode.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        mainViewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(pendingOAuthUri) {
        val uri = pendingOAuthUri ?: return@LaunchedEffect
        onOAuthUriConsumed()
        authViewModel.completeOAuthCallback(uri) { profile ->
            mainViewModel.setCurrentUser(profile)
            feedViewModel.loadFeed()
            profileViewModel.loadProfile(profile.id)
            if (currentDest?.hasRoute<AuthRoute>() == true) {
                navController.popBackStack()
            }
            mainViewModel.emitMessage("Signed in as ${profile.displayName.ifBlank { profile.username }}")
        }
    }

    LaunchedEffect(currentUser?.id) {
        if (currentUser == null) {
            inboxViewModel.clearUnreadCount()
        } else {
            while (isActive) {
                inboxViewModel.refreshUnreadCount()
                delay(20_000)
            }
        }
    }

    val showTopLevelNav = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<DiscoverRoute>() ||
            dest.hasRoute<InboxRoute>() ||
            dest.hasRoute<ProfileRoute>()
    } ?: false

    val isOnHome = currentDest?.hasRoute<HomeRoute>() == true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openAuthScreen(startWithSignUp: Boolean = true, openProfileAfterAuth: Boolean = false) {
        authViewModel.setMode(if (startWithSignUp) AuthMode.SIGN_UP else AuthMode.SIGN_IN)
        navController.navigate(
            AuthRoute(
                startWithSignUp = startWithSignUp,
                openProfileAfterAuth = openProfileAfterAuth
            )
        ) {
            launchSingleTop = true
        }
    }

    fun openVideoDetail(video: VideoItem) {
        navController.navigate(
            VideoPlayerRoute(
                videoUrl = video.videoUrl,
                title = video.title,
                caption = video.caption,
                username = video.creator?.username ?: "creator",
                filterName = video.filterName,
                trimStartMs = video.trimStartMs,
                trimEndMs = video.trimEndMs
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    val containerColor = if (isOnHome) BladeObsidian else MaterialTheme.colorScheme.surface
                    val unselectedColor = if (isOnHome) TextWhite.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant

                    Surface(
                        color = containerColor,
                        tonalElevation = if (isOnHome) 0.dp else 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            if (!isOnHome) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompactBottomNavItem(
                                    selected = currentDest?.hasRoute<HomeRoute>() == true,
                                    label = strings.navHome,
                                    selectedColor = BladeRed,
                                    unselectedColor = unselectedColor,
                                    onClick = {
                                        feedViewModel.selectTab(FeedTab.FOR_YOU)
                                        navigateTopLevel(HomeRoute)
                                    },
                                    modifier = Modifier.testTag("nav_home")
                                ) {
                                    Icon(
                                        if (currentDest?.hasRoute<HomeRoute>() == true) Icons.Rounded.Home else Icons.Outlined.Home,
                                        contentDescription = strings.navHome,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                CompactBottomNavItem(
                                    selected = currentDest?.hasRoute<DiscoverRoute>() == true,
                                    label = strings.navDiscover,
                                    selectedColor = BladeRed,
                                    unselectedColor = unselectedColor,
                                    onClick = { navigateTopLevel(DiscoverRoute) },
                                    modifier = Modifier.testTag("nav_discover")
                                ) {
                                    Icon(
                                        if (currentDest?.hasRoute<DiscoverRoute>() == true) Icons.Rounded.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
                                        contentDescription = strings.navDiscover,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                CompactBottomNavItem(
                                    selected = false,
                                    label = strings.navCreate,
                                    selectedColor = BladeRed,
                                    unselectedColor = unselectedColor,
                                    onClick = { navController.navigate(CreateRoute) },
                                    modifier = Modifier.testTag("nav_create")
                                ) {
                                    BladeCreateNavBadge()
                                }

                                CompactBottomNavItem(
                                    selected = currentDest?.hasRoute<InboxRoute>() == true,
                                    label = strings.navInbox,
                                    selectedColor = BladeRed,
                                    unselectedColor = unselectedColor,
                                    onClick = {
                                        if (currentUser != null) inboxViewModel.loadInbox()
                                        navigateTopLevel(InboxRoute)
                                    },
                                    modifier = Modifier.testTag("nav_inbox")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (inboxUiState.unreadCount > 0) {
                                                Badge(
                                                    containerColor = BladeRed,
                                                    contentColor = TextWhite
                                                ) {
                                                    Text(
                                                        text = if (inboxUiState.unreadCount > 99) "99+" else inboxUiState.unreadCount.toString()
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (currentDest?.hasRoute<InboxRoute>() == true) Icons.Rounded.Forum else Icons.Outlined.Forum,
                                            contentDescription = strings.navInbox,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                CompactBottomNavItem(
                                    selected = currentDest?.hasRoute<ProfileRoute>() == true,
                                    label = strings.navProfile,
                                    selectedColor = BladeRed,
                                    unselectedColor = unselectedColor,
                                    onClick = {
                                        val loggedInUser = currentUser
                                        if (loggedInUser == null) {
                                            openAuthScreen(startWithSignUp = true, openProfileAfterAuth = true)
                                        } else {
                                            profileViewModel.loadProfile(loggedInUser.id)
                                            navigateTopLevel(ProfileRoute)
                                        }
                                    },
                                    modifier = Modifier.testTag("nav_profile")
                                ) {
                                    Icon(
                                        if (currentDest?.hasRoute<ProfileRoute>() == true) Icons.Rounded.AccountCircle else Icons.Outlined.AccountCircle,
                                        contentDescription = strings.navProfile,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    val railColors = NavigationRailItemDefaults.colors(
                        selectedIconColor = BladeRed,
                        selectedTextColor = BladeRed,
                        unselectedIconColor = if (isOnHome) TextWhite.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = if (isOnHome) TextWhite.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = BladeRed.copy(alpha = 0.16f)
                    )
                    NavigationRail(
                        containerColor = if (isOnHome) BladeObsidian else MaterialTheme.colorScheme.surface
                    ) {
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<HomeRoute>() == true,
                            onClick = {
                                feedViewModel.selectTab(FeedTab.FOR_YOU)
                                navigateTopLevel(HomeRoute)
                            },
                            icon = { Icon(Icons.Rounded.Home, contentDescription = strings.navHome) },
                            label = { Text(strings.navHome) },
                            colors = railColors
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<DiscoverRoute>() == true,
                            onClick = { navigateTopLevel(DiscoverRoute) },
                            icon = { Icon(Icons.Rounded.LocalFireDepartment, contentDescription = strings.navDiscover) },
                            label = { Text(strings.navDiscover) },
                            colors = railColors
                        )
                        NavigationRailItem(
                            selected = false,
                            onClick = { navController.navigate(CreateRoute) },
                            icon = { BladeCreateNavBadge() },
                            label = { Text(strings.navCreate) },
                            colors = railColors
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<InboxRoute>() == true,
                            onClick = {
                                if (currentUser != null) inboxViewModel.loadInbox()
                                navigateTopLevel(InboxRoute)
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (inboxUiState.unreadCount > 0) {
                                            Badge(
                                                containerColor = BladeRed,
                                                contentColor = TextWhite
                                            ) {
                                                Text(
                                                    text = if (inboxUiState.unreadCount > 99) "99+" else inboxUiState.unreadCount.toString()
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.Forum, contentDescription = strings.navInbox)
                                }
                            },
                            label = { Text(strings.navInbox) },
                            colors = railColors
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<ProfileRoute>() == true,
                            onClick = {
                                val loggedInUser = currentUser
                                if (loggedInUser == null) {
                                    openAuthScreen(startWithSignUp = true, openProfileAfterAuth = true)
                                } else {
                                    profileViewModel.loadProfile(loggedInUser.id)
                                    navigateTopLevel(ProfileRoute)
                                }
                            },
                            icon = { Icon(Icons.Rounded.AccountCircle, contentDescription = strings.navProfile) },
                            label = { Text(strings.navProfile) },
                            colors = railColors
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = SplashRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<SplashRoute> {
                        SplashScreen(
                            onFinished = {
                                navController.navigate(HomeRoute) {
                                    popUpTo<SplashRoute> { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<HomeRoute> {
                        HomeScreen(
                            feedViewModel = feedViewModel,
                            currentUser = currentUser,
                            isSupabaseConfigured = mainViewModel.isSupabaseConfigured,
                            onNavigateToSearch = { navigateTopLevel(DiscoverRoute) },
                            onNavigateToHashtag = { tag ->
                                discoverViewModel.filterByHashtag(tag)
                                navigateTopLevel(DiscoverRoute)
                            },
                            onNavigateToSound = { soundId ->
                                discoverViewModel.filterBySound(soundId)
                                navigateTopLevel(DiscoverRoute)
                            },
                            onNavigateToCreator = { uid ->
                                if (currentUser == null) {
                                    openAuthScreen(startWithSignUp = true)
                                } else {
                                    navController.navigate(UserProfileRoute(uid))
                                }
                            },
                            onNavigateToCreate = { navController.navigate(CreateRoute) },
                            onNavigateToAuth = { openAuthScreen(startWithSignUp = true) },
                            onShowMessage = { mainViewModel.emitMessage(it) }
                        )
                    }

                    composable<DiscoverRoute> {
                        DiscoverScreen(
                            discoverViewModel = discoverViewModel,
                            onVideoClick = { startIndex ->
                                navController.navigate(DiscoverVideoFeedRoute(startIndex))
                            },
                            onCreatorClick = { uid ->
                                if (currentUser == null) {
                                    openAuthScreen(startWithSignUp = true)
                                } else {
                                    navController.navigate(UserProfileRoute(uid))
                                }
                            },
                            onUseSound = { sound ->
                                createViewModel.preselectSound(sound)
                                navController.navigate(CreateRoute)
                            }
                        )
                    }

                    composable<DiscoverVideoFeedRoute> { entry ->
                        val args = entry.toRoute<DiscoverVideoFeedRoute>()
                        DiscoverVideoFeedScreen(
                            startIndex = args.startIndex,
                            currentUser = currentUser,
                            discoverViewModel = discoverViewModel,
                            feedViewModel = feedViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToCreator = { uid ->
                                if (currentUser == null) {
                                    openAuthScreen(startWithSignUp = true)
                                } else {
                                    navController.navigate(UserProfileRoute(uid))
                                }
                            },
                            onHashtagClick = { tag ->
                                discoverViewModel.filterByHashtag(tag)
                                navController.popBackStack()
                            },
                            onSoundClick = { soundId ->
                                discoverViewModel.filterBySound(soundId)
                                navController.popBackStack()
                            },
                            onRequireAuth = { openAuthScreen(startWithSignUp = true) },
                            onShowMessage = { mainViewModel.emitMessage(it) }
                        )
                    }

                    composable<CreateRoute>(
                        enterTransition = { fadeIn(animationSpec = tween(75)) },
                        exitTransition = { fadeOut(animationSpec = tween(75)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(75)) },
                        popExitTransition = { fadeOut(animationSpec = tween(75)) }
                    ) {
                        CreateScreen(
                            createViewModel = createViewModel,
                            currentUser = currentUser,
                            onClose = { navController.popBackStack() },
                            onRequireAuth = { openAuthScreen(startWithSignUp = true) },
                            onUploadSuccess = { publishedVideo ->
                                mainViewModel.emitMessage("Blade published!")
                                feedViewModel.onNewVideoPublished(publishedVideo)
                                discoverViewModel.refresh()
                                currentUser?.id?.let { profileViewModel.loadProfile(it) }
                                navController.popBackStack()
                                navigateTopLevel(HomeRoute)
                            }
                        )
                    }

                    composable<InboxRoute> {
                        InboxScreen(
                            inboxViewModel = inboxViewModel,
                            currentUser = currentUser,
                            onNavigateToAuth = { openAuthScreen(startWithSignUp = true) },
                            onOpenChat = { convId -> navController.navigate(ChatRoute(convId)) },
                            onOpenCreator = { uid ->
                                if (currentUser == null) {
                                    openAuthScreen(startWithSignUp = true)
                                } else {
                                    navController.navigate(UserProfileRoute(uid))
                                }
                            }
                        )
                    }

                    composable<ProfileRoute> { entry ->
                        val loggedInUser = currentUser
                        if (loggedInUser == null) {
                            ProtectedAuthRedirect(
                                onRedirect = {
                                    if (navController.currentBackStackEntry?.id == entry.id) {
                                        navController.popBackStack()
                                        openAuthScreen(startWithSignUp = true, openProfileAfterAuth = true)
                                    }
                                }
                            )
                        } else {
                            ProfileScreen(
                                targetUserId = null,
                                currentUser = loggedInUser,
                                profileViewModel = profileViewModel,
                                isOtherUserScreen = false,
                                onNavigateToAuth = { openAuthScreen(startWithSignUp = true, openProfileAfterAuth = true) },
                                onNavigateToEditProfile = { navController.navigate(EditProfileRoute) },
                                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                                onProfileUpdated = { updated -> mainViewModel.setCurrentUser(updated) },
                                onShowMessage = { mainViewModel.emitMessage(it) },
                                onMessageCreator = { uid ->
                                    inboxViewModel.openConversationWithUser(uid) { convId ->
                                        navController.navigate(ChatRoute(convId))
                                    }
                                },
                                onVideoClick = { tabIndex, startIndex ->
                                    navController.navigate(
                                        ProfileVideoFeedRoute(
                                            tabIndex = tabIndex,
                                            startIndex = startIndex,
                                            isOwnProfile = true
                                        )
                                    )
                                },
                                onVideoDeleted = { videoId ->
                                    feedViewModel.removeVideo(videoId)
                                },
                                onOpenUserProfile = { uid -> navController.navigate(UserProfileRoute(uid)) }
                            )
                        }
                    }

                    composable<UserProfileRoute> { entry ->
                        val args = entry.toRoute<UserProfileRoute>()
                        val loggedInUser = currentUser
                        if (loggedInUser == null) {
                            ProtectedAuthRedirect(
                                onRedirect = {
                                    if (navController.currentBackStackEntry?.id == entry.id) {
                                        navController.popBackStack()
                                        openAuthScreen(startWithSignUp = true)
                                    }
                                }
                            )
                        } else {
                            ProfileScreen(
                                targetUserId = args.userId,
                                currentUser = loggedInUser,
                                profileViewModel = profileViewModel,
                                isOtherUserScreen = true,
                                onBack = { navController.popBackStack() },
                                onNavigateToAuth = { openAuthScreen(startWithSignUp = true) },
                                onNavigateToEditProfile = { navController.navigate(EditProfileRoute) },
                                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                                onProfileUpdated = { updated -> mainViewModel.setCurrentUser(updated) },
                                onShowMessage = { mainViewModel.emitMessage(it) },
                                onMessageCreator = { uid ->
                                    inboxViewModel.openConversationWithUser(uid) { convId ->
                                        navController.navigate(ChatRoute(convId))
                                    }
                                },
                                onVideoClick = { tabIndex, startIndex ->
                                    navController.navigate(
                                        ProfileVideoFeedRoute(
                                            tabIndex = tabIndex,
                                            startIndex = startIndex,
                                            isOwnProfile = (args.userId == loggedInUser.id)
                                        )
                                    )
                                },
                                onVideoDeleted = { videoId ->
                                    feedViewModel.removeVideo(videoId)
                                },
                                onOpenUserProfile = { uid -> navController.navigate(UserProfileRoute(uid)) }
                            )
                        }
                    }

                    composable<ProfileVideoFeedRoute> { entry ->
                        val args = entry.toRoute<ProfileVideoFeedRoute>()
                        val loggedInUser = currentUser
                        if (loggedInUser == null) {
                            ProtectedAuthRedirect(
                                onRedirect = {
                                    if (navController.currentBackStackEntry?.id == entry.id) {
                                        navController.popBackStack()
                                        openAuthScreen(startWithSignUp = true)
                                    }
                                }
                            )
                        } else {
                            ProfileVideoFeedScreen(
                                tabIndex = args.tabIndex,
                                startIndex = args.startIndex,
                                isOwnProfile = args.isOwnProfile,
                                currentUser = loggedInUser,
                                profileViewModel = profileViewModel,
                                feedViewModel = feedViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToCreator = { uid -> navController.navigate(UserProfileRoute(uid)) },
                                onVideoDeleted = { videoId ->
                                    feedViewModel.removeVideo(videoId)
                                },
                                onShowMessage = { mainViewModel.emitMessage(it) }
                            )
                        }
                    }

                    composable<ChatRoute> { entry ->
                        val args = entry.toRoute<ChatRoute>()
                        ChatScreen(
                            conversationId = args.conversationId,
                            inboxViewModel = inboxViewModel,
                            currentUser = currentUser,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AuthRoute> { entry ->
                        val args = entry.toRoute<AuthRoute>()
                        LaunchedEffect(entry.id) {
                            authViewModel.setMode(
                                if (args.startWithSignUp) AuthMode.SIGN_UP else AuthMode.SIGN_IN
                            )
                        }
                        AuthScreen(
                            authViewModel = authViewModel,
                            adminViewModel = adminViewModel,
                            onBack = { navController.popBackStack() },
                            onAuthenticated = { profile ->
                                mainViewModel.setCurrentUser(profile)
                                feedViewModel.loadFeed()
                                navController.popBackStack()
                                if (args.openProfileAfterAuth) {
                                    profileViewModel.loadProfile(profile.id)
                                    navigateTopLevel(ProfileRoute)
                                }
                            },
                            onAdminAuthenticated = {
                                navController.navigate(AdminRoute) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable<EditProfileRoute> { entry ->
                        val loggedInUser = currentUser
                        if (loggedInUser == null) {
                            ProtectedAuthRedirect(
                                onRedirect = {
                                    if (navController.currentBackStackEntry?.id == entry.id) {
                                        navController.popBackStack()
                                        openAuthScreen(startWithSignUp = true, openProfileAfterAuth = true)
                                    }
                                }
                            )
                        } else {
                            EditProfileScreen(
                                currentUser = loggedInUser,
                                profileViewModel = profileViewModel,
                                onBack = { navController.popBackStack() },
                                onSaved = { updated -> mainViewModel.setCurrentUser(updated) }
                            )
                        }
                    }

                    composable<SettingsRoute> { entry ->
                        val loggedInUser = currentUser
                        if (loggedInUser == null) {
                            ProtectedAuthRedirect(
                                onRedirect = {
                                    if (navController.currentBackStackEntry?.id == entry.id) {
                                        navController.popBackStack()
                                        openAuthScreen(startWithSignUp = true, openProfileAfterAuth = true)
                                    }
                                }
                            )
                        } else {
                            SettingsScreen(
                                currentUser = loggedInUser,
                                isDarkMode = isDarkMode,
                                languageCode = languageCode,
                                isSupabaseConfigured = mainViewModel.isSupabaseConfigured,
                                onToggleDarkMode = { mainViewModel.toggleDarkMode(it) },
                                onSelectLanguage = { mainViewModel.setLanguage(it) },
                                onSignOut = {
                                    mainViewModel.signOut()
                                    appScope.launch {
                                        runCatching {
                                            CredentialManager.create(context)
                                                .clearCredentialState(ClearCredentialStateRequest())
                                        }
                                    }
                                    authViewModel.setMode(AuthMode.SIGN_UP)
                                    navController.navigate(
                                        AuthRoute(startWithSignUp = true, openProfileAfterAuth = true)
                                    ) {
                                        popUpTo(HomeRoute) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    composable<WalletRoute> { entry ->
                        val loggedInUser = currentUser
                        if (loggedInUser == null) {
                            ProtectedAuthRedirect(
                                onRedirect = {
                                    if (navController.currentBackStackEntry?.id == entry.id) {
                                        navController.popBackStack()
                                        openAuthScreen(startWithSignUp = true, openProfileAfterAuth = true)
                                    }
                                }
                            )
                        } else {
                            WalletScreen(
                                currentUser = loggedInUser,
                                profileViewModel = profileViewModel,
                                onCoinsUpdated = { bal ->
                                    mainViewModel.setCurrentUser(loggedInUser.copy(coinsBalance = bal))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    composable<AdminRoute> { entry ->
                        if (!adminUiState.isAdminAuthenticated) {
                            ProtectedAuthRedirect(
                                onRedirect = {
                                    if (navController.currentBackStackEntry?.id == entry.id) {
                                        navController.popBackStack()
                                        openAuthScreen(startWithSignUp = false)
                                    }
                                }
                            )
                        } else {
                            AdminScreen(
                                adminViewModel = adminViewModel,
                                isDarkMode = isDarkMode,
                                languageCode = languageCode,
                                onToggleDarkMode = { mainViewModel.toggleDarkMode(it) },
                                onSelectLanguage = { mainViewModel.setLanguage(it) },
                                onPlayVideo = { video -> openVideoDetail(video) },
                                onVideoDeleted = { videoId ->
                                    feedViewModel.removeVideo(videoId)
                                    discoverViewModel.removeVideo(videoId)
                                    feedViewModel.loadFeed()
                                    discoverViewModel.refresh()
                                    mainViewModel.refreshCurrentUser()
                                    currentUser?.id?.let { uid -> profileViewModel.loadProfile(uid) }
                                },
                                onFeedMutated = {
                                    feedViewModel.loadFeed()
                                    discoverViewModel.refresh()
                                    mainViewModel.refreshAppSettings()
                                    mainViewModel.refreshCurrentUser()
                                    currentUser?.id?.let { uid -> profileViewModel.loadProfile(uid) }
                                },
                                onShowMessage = { mainViewModel.emitMessage(it) },
                                onBack = { navController.popBackStack() },
                                onLoggedOut = {
                                    navController.popBackStack()
                                    openAuthScreen(startWithSignUp = false)
                                }
                            )
                        }
                    }

                    composable<VideoPlayerRoute> { entry ->
                        val args = entry.toRoute<VideoPlayerRoute>()
                        VideoDetailPlayerScreen(
                            videoUrl = args.videoUrl,
                            title = args.title,
                            caption = args.caption,
                            username = args.username,
                            filterName = args.filterName,
                            trimStartMs = args.trimStartMs,
                            trimEndMs = args.trimEndMs,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        val isOnSplash = currentDest?.hasRoute<SplashRoute>() == true
        val isOnAuth = currentDest?.hasRoute<AuthRoute>() == true
        val isOnAdmin = currentDest?.hasRoute<AdminRoute>() == true
        var dismissedBannerText by remember(appSettings.announcementBanner) { mutableStateOf("") }

        if (appSettings.maintenanceMode && !isOnSplash && !isOnAuth && !isOnAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Construction,
                        contentDescription = "Under maintenance",
                        tint = BladeRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Scheduled Maintenance",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = appSettings.announcementBanner.ifBlank {
                            "Blade is temporarily under maintenance. Please check back shortly."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { openAuthScreen(startWithSignUp = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Admin Access")
                    }
                }
            }
        } else if (
            appSettings.announcementBanner.isNotBlank() &&
            dismissedBannerText != appSettings.announcementBanner &&
            !isOnSplash &&
            !isOnAdmin
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 54.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = BladeRed, modifier = Modifier.size(20.dp))
                    Text(
                        text = appSettings.announcementBanner,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { dismissedBannerText = appSettings.announcementBanner }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss announcement", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CompactBottomNavItem(
    selected: Boolean,
    label: String,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val contentColor = if (selected) selectedColor else unselectedColor
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
            icon()
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

@Composable
private fun BladeCreateNavBadge() {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(25.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(BladeRedDark, BladeRed, BladeRedAccent))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Create",
            tint = TextWhite,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun ProtectedAuthRedirect(onRedirect: () -> Unit) {
    LaunchedEffect(Unit) {
        onRedirect()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )
}
