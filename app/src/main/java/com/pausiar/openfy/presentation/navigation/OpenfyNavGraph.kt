package com.pausiar.openfy.presentation.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pausiar.openfy.presentation.components.MiniPlayerBar
import com.pausiar.openfy.presentation.viewmodel.HomeViewModel
import com.pausiar.openfy.presentation.viewmodel.LibraryViewModel
import com.pausiar.openfy.presentation.viewmodel.LocalMusicViewModel
import com.pausiar.openfy.presentation.viewmodel.OpenfyViewModelFactory
import com.pausiar.openfy.presentation.viewmodel.PlayerViewModel
import com.pausiar.openfy.presentation.viewmodel.PlaylistDetailViewModel
import com.pausiar.openfy.presentation.viewmodel.SettingsViewModel
import com.pausiar.openfy.utils.openInPreferredApp

private data class TopLevelItem(
    val destination: OpenfyDestination,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun OpenfyNavGraph(playerViewModel: PlayerViewModel) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    val currentRoute = currentDestination?.route
    val context = LocalContext.current
    val playerUiState by playerViewModel.uiState.collectAsState()
    val topLevelItems = listOf(
        TopLevelItem(OpenfyDestination.Home, "Inicio", Icons.Rounded.Home),
        TopLevelItem(OpenfyDestination.Library, "Biblioteca", Icons.Rounded.LibraryMusic),
        TopLevelItem(OpenfyDestination.LocalMusic, "Local", Icons.Rounded.MusicNote),
        TopLevelItem(OpenfyDestination.Settings, "Ajustes", Icons.Rounded.Settings),
    )
    val showBottomBar = topLevelItems.any { it.destination.route == currentRoute }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    val currentTrack = playerUiState.player.currentTrack
                    if (currentTrack != null && currentRoute != OpenfyDestination.Player.route) {
                        MiniPlayerBar(
                            track = currentTrack,
                            isPlaying = playerUiState.player.isPlaying,
                            progress = if (playerUiState.player.durationMs > 0L) {
                                playerUiState.player.positionMs.toFloat() / playerUiState.player.durationMs.toFloat()
                            } else {
                                0f
                            },
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onOpenPlayer = { navController.navigate(OpenfyDestination.Player.route) },
                        )
                    }
                    if (showBottomBar) {
                        NavigationBar {
                            topLevelItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentDestination?.hierarchy?.any { it.route == item.destination.route } == true,
                                    onClick = {
                                        navController.navigate(item.destination.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { androidx.compose.material3.Text(item.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = OpenfyDestination.Home.route,
                modifier = Modifier.padding(paddingValues),
            ) {
                composable(OpenfyDestination.Home.route) {
                    val viewModel: HomeViewModel = viewModel(factory = OpenfyViewModelFactory.homeFactory())
                    val uiState by viewModel.uiState.collectAsState()

                    LaunchedEffect(viewModel) {
                        viewModel.openPlaylist.collect { playlistId ->
                            navController.navigate(OpenfyDestination.PlaylistDetail.createRoute(playlistId))
                        }
                    }

                    com.pausiar.openfy.presentation.screens.HomeScreen(
                        uiState = uiState,
                        onUrlChange = viewModel::updateInput,
                        onImport = viewModel::importUrl,
                        onOpenLibrary = { navController.navigate(OpenfyDestination.Library.route) },
                        onOpenLocal = { navController.navigate(OpenfyDestination.LocalMusic.route) },
                        onOpenSettings = { navController.navigate(OpenfyDestination.Settings.route) },
                        onOpenPlaylist = { playlistId -> navController.navigate(OpenfyDestination.PlaylistDetail.createRoute(playlistId)) },
                        onDismissMessage = viewModel::clearMessage,
                    )
                }

                composable(OpenfyDestination.Library.route) {
                    val viewModel: LibraryViewModel = viewModel(factory = OpenfyViewModelFactory.libraryFactory())
                    val uiState by viewModel.uiState.collectAsState()
                    com.pausiar.openfy.presentation.screens.LibraryScreen(
                        uiState = uiState,
                        onQueryChange = viewModel::updateQuery,
                        onPlatformChange = viewModel::updatePlatform,
                        onSortChange = viewModel::updateSort,
                        onOpenPlaylist = { playlistId -> navController.navigate(OpenfyDestination.PlaylistDetail.createRoute(playlistId)) },
                        onRenamePlaylist = viewModel::renamePlaylist,
                        onDeletePlaylist = viewModel::deletePlaylist,
                        onDismissMessage = viewModel::clearMessage,
                    )
                }

                composable(OpenfyDestination.LocalMusic.route) {
                    val viewModel: LocalMusicViewModel = viewModel(factory = OpenfyViewModelFactory.localMusicFactory())
                    val uiState by viewModel.uiState.collectAsState()
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
                        viewModel.openPlaylist.collect { playlistId ->
                            navController.navigate(OpenfyDestination.PlaylistDetail.createRoute(playlistId))
                        }
                    }

                    com.pausiar.openfy.presentation.screens.LocalMusicScreen(
                        uiState = uiState,
                        onPlaylistNameChange = viewModel::updatePlaylistName,
                        onCreatePlaylist = viewModel::createEmptyPlaylist,
                        onPickFiles = { picker.launch(arrayOf("audio/*")) },
                        onOpenPlaylist = { playlistId -> navController.navigate(OpenfyDestination.PlaylistDetail.createRoute(playlistId)) },
                        onDismissMessage = viewModel::clearMessage,
                    )
                }

                composable(OpenfyDestination.Settings.route) {
                    val viewModel: SettingsViewModel = viewModel(factory = OpenfyViewModelFactory.settingsFactory())
                    val uiState by viewModel.uiState.collectAsState()
                    com.pausiar.openfy.presentation.screens.SettingsScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenLegal = { navController.navigate(OpenfyDestination.Legal.route) },
                        onDismissMessage = viewModel::clearMessage,
                    )
                }

                composable(OpenfyDestination.Legal.route) {
                    com.pausiar.openfy.presentation.screens.LegalInfoScreen(onBack = { navController.popBackStack() })
                }

                composable(OpenfyDestination.Player.route) {
                    com.pausiar.openfy.presentation.screens.PlayerScreen(
                        uiState = playerUiState,
                        onBack = { navController.popBackStack() },
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onSkipNext = playerViewModel::skipNext,
                        onSkipPrevious = playerViewModel::skipPrevious,
                        onSeekTo = playerViewModel::seekTo,
                        onCycleRepeat = playerViewModel::cycleRepeatMode,
                        onToggleShuffle = playerViewModel::toggleShuffle,
                        onDismissError = playerViewModel::clearError,
                    )
                }

                composable(OpenfyDestination.PlaylistDetail.route) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: return@composable
                    val viewModel: PlaylistDetailViewModel = viewModel(factory = OpenfyViewModelFactory.playlistDetailFactory(playlistId))
                    val uiState by viewModel.uiState.collectAsState()

                    com.pausiar.openfy.presentation.screens.PlaylistDetailScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onQueryChange = viewModel::updateQuery,
                        onToggleFavorites = viewModel::toggleFavoritesOnly,
                        onSortChange = viewModel::updateSort,
                        onPlay = { shuffle ->
                            viewModel.playAll(shuffle)
                            navController.navigate(OpenfyDestination.Player.route)
                        },
                        onTrackClick = { track ->
                            if (track.isPlayableInApp) {
                                viewModel.playTrack(track)
                                navController.navigate(OpenfyDestination.Player.route)
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