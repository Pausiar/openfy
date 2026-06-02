package com.pausiar.openfy.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pausiar.openfy.presentation.navigation.OpenfyNavGraph
import com.pausiar.openfy.presentation.theme.OpenfyTheme
import com.pausiar.openfy.presentation.viewmodel.OpenfyViewModelFactory
import com.pausiar.openfy.presentation.viewmodel.PlayerViewModel
import com.pausiar.openfy.presentation.viewmodel.SettingsViewModel

@Composable
fun OpenfyApp() {
    val settingsViewModel: SettingsViewModel = viewModel(factory = OpenfyViewModelFactory.settingsFactory())
    val playerViewModel: PlayerViewModel = viewModel(factory = OpenfyViewModelFactory.playerFactory())
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    OpenfyTheme(themeMode = settingsUiState.settings.themeMode) {
        OpenfyNavGraph(playerViewModel = playerViewModel)
    }
}