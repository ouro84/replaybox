package com.replaybox.provider.tf1

/**
 * TF1 graphql persisted query IDs, measured against https://www.tf1.fr/graphql/web
 * on 2026-11-05 (GET, referer https://www.tf1.fr/programmes-tv + browser UA).
 * The web client also accepts the full "sha256:<hex>" form; the short prefixes below
 * are the first characters of those hashes and are accepted as-is.
 */
object Tf1QueryIds {
    const val CATEGORIES = "909c68c0"
    const val PROGRAMS = "483ce0f"
    const val SEARCH_PROGRAMS = "e78b188"
    const val SEARCH_VIDEOS = "b2dc9439"

    /** videos of a program: programBySlug.videos {items, total}. */
    const val PROGRAM_BY_SLUG = "a6f9cf0e"

    /** single video + streamId (the internal wat media id); same hash as yt-dlp tf1.py. */
    const val VIDEO_BY_SLUG = "9b80783950b85247541dd1d851f9cc7fa36574af015621f853ab111a679ce26f"
}
