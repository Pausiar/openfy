package com.pausiar.openfy.domain.usecases

import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.domain.models.ImportResult

class ImportPlaylistUseCase(
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(url: String): ImportResult = musicRepository.importFromUrl(url)
}