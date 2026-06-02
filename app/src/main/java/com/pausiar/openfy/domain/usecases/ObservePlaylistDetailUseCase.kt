package com.pausiar.openfy.domain.usecases

import com.pausiar.openfy.data.repository.MusicRepository

class ObservePlaylistDetailUseCase(
    private val musicRepository: MusicRepository,
) {
    operator fun invoke(playlistId: Long) = musicRepository.observePlaylist(playlistId)
}