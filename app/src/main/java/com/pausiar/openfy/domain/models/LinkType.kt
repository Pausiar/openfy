package com.pausiar.openfy.domain.models

enum class LinkType(val label: String) {
    SPOTIFY_PLAYLIST("Playlist de Spotify"),
    SPOTIFY_TRACK("Cancion de Spotify"),
    YOUTUBE_PLAYLIST("Playlist de YouTube"),
    YOUTUBE_VIDEO("Video de YouTube"),
    INVALID("Enlace no valido")
}