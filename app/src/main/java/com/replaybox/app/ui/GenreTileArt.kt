package com.replaybox.app.ui

/**
 * How a Genre tile frames its cover (DESIGN.md, Genres): the crop of the key art and the ground
 * scrim that keeps the name on 92 % ground or more (The Contrast Floor Rule).
 */
object GenreTileArt {

    /** Vertical bias of a portrait cover (poster): held high, on the faces, above its title block. */
    const val POSTER_BIAS = -0.6f

    /** Ground alpha from 8 dp above the name down: the Contrast Floor under text over key art. */
    const val NAME_FLOOR = 0.92f

    /** Ground alpha where the art starts to dissolve, as in the Home vertical scrim. */
    const val SOFT = 0.35f

    /** Vertical bias of the crop: a landscape cover (backdrop) stays centred, a portrait one is held high. */
    fun verticalBias(width: Float, height: Float): Float =
        if (width > 0f && height > width) POSTER_BIAS else 0f

    /**
     * Ground scrim stops, top to bottom, as (fraction of the tile height, ground alpha): transparent
     * at the top, [SOFT] at [ramp] + [lead] above the name, [NAME_FLOOR] at [lead] above it, opaque
     * at the bottom. [nameTop] is the top of the name's box; all lengths in the same unit (px).
     * Fractions never decrease; a name reaching the top edge puts the floor there.
     */
    fun scrimStops(height: Float, nameTop: Float, lead: Float, ramp: Float): List<Pair<Float, Float>> {
        if (height <= 0f) return listOf(0f to NAME_FLOOR, 1f to 1f)
        val floorAt = ((nameTop - lead) / height).coerceIn(0f, 1f)
        val softAt = ((nameTop - lead - ramp) / height).coerceIn(0f, floorAt)
        if (floorAt == 0f) return listOf(0f to NAME_FLOOR, 1f to 1f)
        return listOf(0f to 0f, softAt to SOFT, floorAt to NAME_FLOOR, 1f to 1f)
    }
}
