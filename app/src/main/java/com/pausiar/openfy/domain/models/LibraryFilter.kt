package com.pausiar.openfy.domain.models

data class LibraryFilter(
    val query: String = "",
    val platform: Platform? = null,
    val sort: PlaylistSort = PlaylistSort.RECENT,
)

enum class PlaylistSort(val label: String) {
    RECENT("Recientes"),
    TITLE("Titulo"),
    TRACK_COUNT("Canciones")
}

data class TrackFilter(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val sort: TrackSort = TrackSort.POSITION,
)

enum class TrackSort(val label: String) {
    POSITION("Orden original"),
    TITLE("Titulo"),
    DURATION("Duracion")
}