package com.pausiar.openfy.domain.usecases

import android.net.Uri
import com.pausiar.openfy.data.repository.MusicRepository

class ImportLocalTracksUseCase(
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(playlistTitle: String, uris: List<Uri>) =
        musicRepository.importLocalTracks(playlistTitle, uris)
}