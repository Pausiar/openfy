package com.pausiar.openfy.domain.models

sealed interface ImportResult {
    data class Success(
        val playlistId: Long,
        val message: String,
        val requiresApiConfiguration: Boolean = false,
    ) : ImportResult

    data class Error(val message: String) : ImportResult
}