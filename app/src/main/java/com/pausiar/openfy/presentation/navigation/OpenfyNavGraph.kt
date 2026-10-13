package com.pausiar.openfy.presentation.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.presentation.components.BottomBarItem
import com.pausiar.openfy.presentation.components.MiniPlayerBar
import com.pausiar.openfy.presentation.components.OpenfyBackground
import com.pausiar.openfy.presentation.components.OpenfyBottomBar
import com.pausiar.openfy.presentation.screens.HomeScreen
import com.pausiar.openfy.presentation.screens.LegalInfoScreen
import com.pausiar.openfy.presentation.screens.LibraryScreen
import com.pausiar.openfy.presentation.screens.LocalMusicScreen
import com.pausiar.openfy.presentation.screens.PlayerScreen
import com.pausiar.openfy.presentation.screens.PlaylistDetailScreen
import com.pausiar.openfy.presentation.screens.SettingsScreen
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.viewmodel.HomeViewModel
import com.pausiar.openfy.presentation.viewmodel.LibraryViewModel
import com.pausiar.openfy.presentation.viewmodel.LocalMusicViewModel
import com.pausiar.openfy.presentation.viewmodel.OpenfyViewModelFactory
import com.pausiar.openfy.presentation.viewmodel.PlayerViewModel
import com.pausiar.openfy.presentation.viewmodel.PlaylistDetailViewModel
import com.pausiar.openfy.presentation.viewmodel.SettingsViewModel
import com.pausiar.openfy.utils.openInPreferredApp

private val TopLevelItems = listOf(
    BottomBarItem(OpenfyDestination.Home.route, "Inicio", Icons.Outlined.Home, Icons.Rounded.Home),
    BottomBarItem(OpenfyDestination.Library.route, "Biblioteca", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    BottomBarItem(OpenfyDestination.LocalMusic.route, "Local", Icons.Outlined.PhoneAndroid, Icons.Rounded.PhoneAndroid),
    BottomBarItem(OpenfyDestination.Settings.route, "Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

private const val NavAnimMillis = 260

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPlayerTarget(): Boolean =
    targetState.destination.route == OpenfyDestination.Player.route

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPlayerInitial(): Boolean =
    initialState.destination.route == OpenfyDestination.Player.route

/**
 * App shell. Edge-to-edge: the Scaffold does not apply any window insets itself.
 * - Regular screens are wrapped in [ShellPage] (status bar padding + bottom chrome padding + IME).
 * - PlaylistDetail draws behind the status bar (hero) and handles its own top inset.
 * - Player is full screen (chrome hidden) and handles all of its insets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenfyNavGraph(playerViewModel: PlayerViewModel) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    val currentRoute = currentDestination?.route
    val selectedTopLevelRoute = remember(currentDestination) {
        TopLevelItems.firstOrNull { item ->
            currentDestination?.hierarchy?.any { it.route == item.route } == true
        }?.route
    }
    val context = LocalContext.current
    val isPlayerRoute = currentRoute == OpenfyDestination.Player.route
    val openPlayer = remember(navController) {
        { navController.navigate(OpenfyDestination.Player.route) { launchSingleTop = true } }
    }
    val openPlaylist = remember(navController) {
        { playlistId: Long -> navController.navigate(OpenfyDestination.PlaylistDetail.createRoute(playlistId)) }
    }
    val goBack = remember(navController) { { navController.popBackStack(); Unit } }

    OpenfyBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                BottomChrome(
                    playerViewModel = playerViewModel,
                    visible = !isPlayerRoute,
                    selectedRoute = selectedTopLevelRoute,
                    onOpenPlayer = openPlayer,
                    onSelectTab = { item -> navController.navigateToTopLevel(item.route) },
                )
            },
        ) { chromePadding ->
            NavHost(
                navController = navController,
                startDestination = OpenfyDestination.Home.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    if (isPlayerTarget()) {
                        slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))
                    } else {
                        fadeIn(tween(NavAnimMillis, delayMillis = 60)) +
                            slideInVertically(tween(NavAnimMillis, easing = FastOutSlowInEasing)) { it / 28 }
                    }
                },
                exitTransition = {
                    if (isPlayerTarget()) ExitTransition.None else fadeOut(tween(120))
                },
                popEnterTransition = {
                    if (isPlayerInitial()) EnterTransition.None else fadeIn(tween(NavAnimMillis, delayMillis = 60))
                },
                popExitTransition = {
                    if (isPlayerInitial()) {
                        slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(260, delayMillis = 60))
                    } else {
                        fadeOut(tween(140)) + slideOutVertically(tween(NavAnimMillis)) { it / 28 }
                    }
                },
            ) {
                composable(OpenfyDestination.Home.route) {
                    val viewModel: HomeViewModel = viewModel(factory = OpenfyViewModelFactory.homeFactory())
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(viewModel) {
                        viewModel.openPlaylist.collect { playlistId -> openPlaylist(playlistId) }
                    }

                    ShellPage(chromePadding) {
                        HomeScreen(
                            uiState = uiState,
                            onUrlChange = viewModel::updateInput,
                            onImport = viewModel::importUrl,
                            onOpenLibrary = { navController.navigateToTopLevel(OpenfyDestination.Library.route) },
                            onOpenLocal = { navController.navigateToTopLevel(OpenfyDestination.LocalMusic.route) },
                            onOpenSettings = { navController.navigateToTopLevel(OpenfyDestination.Settings.route) },
                            onOpenPlaylist = openPlaylist,
                            onDismissMessage = viewModel::clearMessage,
                        )
                    }
                }

                composable(OpenfyDestination.Library.route) {
                    val viewModel: LibraryViewModel = viewModel(factory = OpenfyViewModelFactory.libraryFactory())
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    ShellPage(chromePadding) {
                        LibraryScreen(
                            uiState = uiState,
                            onQueryChange = viewModel::updateQuery,
                            onPlatformChange = viewModel::updatePlatform,
                            onSortChange = viewModel::updateSort,
                            onOpenPlaylist = openPlaylist,
                            onRenamePlaylist = viewModel::renamePlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onDismissMessage = viewModel::clearMessage,
                        )
                    }
                }

                composable(OpenfyDestination.LocalMusic.route) {
                    val viewModel: LocalMusicViewModel = viewModel(factory = OpenfyViewModelFactory.localMusicFactory())
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                        uris.forEach { uri ->
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }
                        }
                        viewModel.importUris(uris)
                    }

                    LaunchedEffect(viewModel) {
                        viewModel.openPlaylist.collect { playlistId -> openPlaylist(playlistId) }
                    }

                    ShellPage(chromePadding) {
                        LocalMusicScreen(
                            uiState = uiState,
                            onPlaylistNameChange = viewModel::updatePlaylistName,
                            onCreatePlaylist = viewModel::createEmptyPlaylist,
                            onPickFiles = { picker.launch(arrayOf("audio/*")) },
                            onOpenPlaylist = openPlaylist,
                            onDismissMessage = viewModel::clearMessage,
                        )
                    }
                }

                composable(OpenfyDestination.Settings.route) {
                    val viewModel: SettingsViewModel = viewModel(factory = OpenfyViewModelFactory.settingsFactory())
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    ShellPage(chromePadding) {
                        SettingsScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onOpenLegal = { navController.navigate(OpenfyDestination.Legal.route) },
                            onDismissMessage = viewModel::clearMessage,
                        )
                    }
                }

                composable(OpenfyDestination.Legal.route) {
                    ShellPage(chromePadding) {
                        LegalInfoScreen(onBack = goBack)
                    }
                }

                composable(OpenfyDestination.Player.route) {
                    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()
                    PlayerScreen(
                        uiState = playerUiState,
                        progress = playerViewModel.progress,
                        onBack = goBack,
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onSkipNext = playerViewModel::skipNext,
                        onSkipPrevious = playerViewModel::skipPrevious,
                        onSeekTo = playerViewModel::seekTo,
                        onCycleRepeat = playerViewModel::cycleRepeatMode,
                        onToggleShuffle = playerViewModel::toggleShuffle,
                        onPlayQueueItem = playerViewModel::playQueueItem,
                        onDismissError = playerViewModel::clearError,
                    )
                }

                composable(OpenfyDestination.PlaylistDetail.route) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: return@composable
                    val viewModel: PlaylistDetailViewModel = viewModel(factory = OpenfyViewModelFactory.playlistDetailFactory(playlistId))
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = chromePadding.calculateBottomPadding())
                            .consumeWindowInsets(PaddingValues(bottom = chromePadding.calculateBottomPadding()))
                            .imePadding(),
                    ) {
                        PlaylistDetailScreen(
                            uiState = uiState,
                            currentTrackId = playerUiState.player.currentTrack?.id,
                            isPlaying = playerUiState.player.isPlaying,
                            onBack = goBack,
                            onQueryChange = viewModel::updateQuery,
                            onToggleFavorites = viewModel::toggleFavoritesOnly,
                            onSortChange = viewModel::updateSort,
                            onPlay = { shuffle ->
                                viewModel.playAll(shuffle)
                                openPlayer()
                            },
                            onTrackClick = { track: Track ->
                                if (track.canPlayInApp) {
                                    viewModel.playTrack(track)
                                    openPlayer()
                                } else {
                                    track.originalUrl?.let { url -> context.openInPreferredApp(url, track.platform) }
                                }
                            },
                            onFavoriteToggle = viewModel::toggleFavorite,
                            onOpenOriginal = { url, platform -> context.openInPreferredApp(url, platform) },
                            onDismissMessage = viewModel::clearMessage,
                        )
                    }
                }
            }
        }
    }
}

/** Wrapper for regular (non-immersive) screens: status bar inset, bottom chrome padding and IME. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShellPage(
    chromePadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(chromePadding)
            .consumeWindowInsets(chromePadding)
            .statusBarsPadding()
            .imePadding(),
    ) {
        content()
    }
}

/**
 * Mini player + bottom navigation. Collects the player state here (not at NavGraph level),
 * and only reads playback progress inside the mini player's draw phase.
 */
@Composable
private fun BottomChrome(
    playerViewModel: PlayerViewModel,
    visible: Boolean,
    selectedRoute: String?,
    onOpenPlayer: () -> Unit,
    onSelectTab: (BottomBarItem) -> Unit,
) {
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val progressState = playerViewModel.progress.collectAsStateWithLifecycle()
    val progressProvider = remember(progressState) { { progressState.value.fraction } }
    val currentTrack = playerUiState.player.currentTrack
    // Keep the last track so the mini player can animate out with content.
    var lastTrack by remember { mutableStateOf<Track?>(null) }
    SideEffect {
        if (currentTrack != null) lastTrack = currentTrack
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + expandVertically(tween(260)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(220)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AnimatedVisibility(
                visible = currentTrack != null,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 } + expandVertically(tween(260)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
            ) {
                val shownTrack = currentTrack ?: lastTrack
                if (shownTrack != null) {
                    MiniPlayerBar(
                        track = shownTrack,
                        isPlaying = playerUiState.player.isPlaying,
                        isPreparing = playerUiState.player.isPreparing,
                        progress = progressProvider,
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onSkipNext = playerViewModel::skipNext,
                        onOpenPlayer = onOpenPlayer,
                        modifier = Modifier.padding(bottom = OpenfySpacing.sm),
                    )
                }
            }
            if (selectedRoute != null) {
                OpenfyBottomBar(
                    items = TopLevelItems,
                    selectedRoute = selectedRoute,
                    onSelect = onSelectTab,
                )
            } else {
                // Secondary screens: no tabs, but keep clear of the system navigation bar.
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(0.dp),
                )
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
