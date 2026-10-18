package com.pausiar.openfy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pausiar.openfy.presentation.OpenfyApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; insets are handled in Compose.
        enableEdgeToEdge()
        setContent {
            OpenfyApp()
        }
    }
}
