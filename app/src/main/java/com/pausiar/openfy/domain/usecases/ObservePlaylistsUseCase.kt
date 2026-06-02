package com.pausiar.openfy.domain.usecases

import com.pausiar.openfy.data.repository.MusicRepository

class ObservePlaylistsUseCase(
    private val musicRepository: MusicRepository,
) {
    operator fun invoke() = musicRepository.observePlaylistSummaries()
}