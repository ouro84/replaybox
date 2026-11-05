package com.replaybox.core.model

import java.text.Normalizer

/**
 * Title normalization for cross-chaîne search (SPEC §Recherche): matching is
 * case- and accent-insensitive. œ is expanded by hand — NFD does not
 * decompose it.
 */
object Titles {

    fun normalize(title: String): String {
        val expanded = title.replace("œ", "oe").replace("Œ", "oe")
        val decomposed = Normalizer.normalize(expanded, Normalizer.Form.NFD)
        val noMarks = decomposed.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return noMarks.lowercase().trim()
    }
}
