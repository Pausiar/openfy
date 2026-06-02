package com.pausiar.openfy.domain.usecases

import com.pausiar.openfy.domain.models.ParsedLink
import com.pausiar.openfy.utils.linkparser.LinkParser

class DetectLinkUseCase(
    private val linkParser: LinkParser,
) {
    operator fun invoke(url: String): ParsedLink = linkParser.parse(url)
}