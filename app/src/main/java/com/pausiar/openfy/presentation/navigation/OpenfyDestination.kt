package com.pausiar.openfy.presentation.navigation

sealed class OpenfyDestination(val route: String) {
    data object Home : OpenfyDestination("home")
    data object Library : OpenfyDestination("library")
    data object LocalMusic : OpenfyDestination("local-music")
    data object Settings : OpenfyDestination("settings")
    data object Legal : OpenfyDestination("legal")
    data object Player : OpenfyDestination("player")

    data object PlaylistDetail : OpenfyDestination("playlist/{playlistId}") {
        fun createRoute(playlistId: Long): String = "playlist/$playlistId"
    }
}