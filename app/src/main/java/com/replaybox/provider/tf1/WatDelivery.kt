package com.replaybox.provider.tf1

/**
 * Faithful port of the media-id handling in yt-dlp's wat extractor
 * (yt_dlp/extractor/wat.py, master @ bc6ff877dd, 2026-01-27):
 * `video_id = video_id if video_id.isdigit() and len(video_id) > 6 else str(int(video_id, 36))`
 *
 * TF1's graphql `streamId` values are already numeric (>6 digits) so this is a no-op
 * for them; it is kept to honor the original chain for legacy wat.tv ids.
 */
object WatDelivery {

    fun normalizeMediaId(raw: String): String =
        if (raw.all { it.isDigit() } && raw.length > 6) raw
        else raw.toLongOrNull(36)?.toString() ?: raw
}
