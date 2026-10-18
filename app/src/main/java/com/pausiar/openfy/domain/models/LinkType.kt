package com.pausiar.openfy.domain.models

enum class LinkType(val label: String) {
    SPOTIFY_PLAYLIST("Playlist de Spotify"),
    SPOTIFY_TRACK("Canción de Spotify"),
    SPOTIFY_ALBUM("Álbum de Spotify"),

    /** Enlace corto (spotify.link): se resuelve a la URL completa al importar. */
    SPOTIFY_SHORT_LINK("Enlace de Spotify"),
    YOUTUBE_PLAYLIST("Playlist de YouTube"),
    YOUTUBE_VIDEO("Vídeo de YouTube"),
    INVALID("Enlace no válido")
}