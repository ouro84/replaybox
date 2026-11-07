package com.replaybox.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.replaybox.R

/**
 * Épisode numbering as the Chaîne gives it (M4 « À suivre »): « S2 E4 · Titre » on the
 * Programme hero and the « À suivre » cards, « Saison 2 · Épisode 4 » on the Épisode suivant
 * panel. Only real numbers are shown; with either missing, the title alone. A 0 is not a
 * number: TF1 files films and one-offs as season 0 / episode 0 (« S0 E0 » seen on the box).
 */
internal object EpisodeLabels {

    /** True when [title] already reads « S02 E04 » (TF1, France TV): no second numbering. */
    fun carriesNumbering(title: String, season: Int, episode: Int): Boolean =
        Regex("""\bS0*$season\s*[-.]?\s*E0*$episode\b""", RegexOption.IGNORE_CASE).containsMatchIn(title)

    /** Whether the short label prefixes the numbering to [title]. */
    fun shortNumbering(season: Int?, episode: Int?, title: String): Pair<Int, Int>? {
        val s = real(season) ?: return null
        val e = real(episode) ?: return null
        return if (carriesNumbering(title, s, e)) null else s to e
    }

    /** [number] when it is a real numbering (1 or more), else null. */
    fun real(number: Int?): Int? = number?.takeIf { it > 0 }
}

/** « S2 E4 · Titre », or the title. */
@Composable
internal fun shortEpisodeLabel(season: Int?, episode: Int?, title: String): String {
    val numbers = EpisodeLabels.shortNumbering(season, episode, title) ?: return title
    return stringResource(R.string.episode_label_short, numbers.first, numbers.second, title)
}

/** « Saison 2 · Épisode 4 », « Saison 2 », or null. */
@Composable
internal fun longNumbering(season: Int?, episode: Int?): String? {
    val s = EpisodeLabels.real(season)
    val e = EpisodeLabels.real(episode)
    return when {
        s != null && e != null -> stringResource(R.string.episode_numbering, s, e)
        s != null -> stringResource(R.string.season_numbering, s)
        else -> null
    }
}
