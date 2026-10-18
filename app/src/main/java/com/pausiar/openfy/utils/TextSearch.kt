package com.pausiar.openfy.utils

import java.text.Normalizer
import java.util.Locale

private val DIACRITICS_REGEX = Regex("\\p{Mn}+")

/** Minusculas y sin tildes, para que "cancion" encuentre "Canción". */
fun String.normalizeForSearch(): String {
    if (isEmpty()) return this
    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
    return DIACRITICS_REGEX.replace(decomposed, "").lowercase(Locale.ROOT)
}
