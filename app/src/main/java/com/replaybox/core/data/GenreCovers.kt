package com.replaybox.core.data

import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Show
import com.replaybox.core.model.Titles

/**
 * Which image fronts each Genre tile on the Genres page (spec § PR C, « Genre covers »): the
 * first Programme of the Genre's « Tout voir » grid that has an image. A Programme covers one
 * tile only and an image fronts one tile only, so two Genres never show the same picture; a
 * Genre left with no Programme to show stays a text tile.
 */
object GenreCovers {

    /** The image of [show]: its backdrop, else its poster; a blank URL is no image. */
    fun imageOf(show: Show): String? =
        show.backdropUrl?.takeIf { it.isNotBlank() } ?: show.posterUrl?.takeIf { it.isNotBlank() }

    /**
     * One cover per Genre of [genres], resolved in that order from [grids] (each in « Tout voir »
     * order). A Genre with no Programme that has an image and is not already a cover, or with
     * no grid, is absent. A Programme is identified by its normalized title, as in [GenreGrid]
     * (the same Programme on two Chaînes is one); an entry whose image an earlier tile already
     * shows (a Provider placeholder, a spin-off's art) is skipped too. Only chosen entries count
     * as used.
     */
    fun pick(genres: List<Genre>, grids: Map<Genre, List<ChannelShow>>): Map<Genre, String> {
        val usedTitles = HashSet<String>()
        val usedImages = HashSet<String>()
        val covers = LinkedHashMap<Genre, String>()
        for (genre in genres) {
            for (entry in grids[genre].orEmpty()) {
                val image = imageOf(entry.show) ?: continue
                val title = Titles.normalize(entry.show.title)
                if (title in usedTitles || image in usedImages) continue
                usedTitles.add(title)
                usedImages.add(image)
                covers[genre] = image
                break
            }
        }
        return covers
    }
}
