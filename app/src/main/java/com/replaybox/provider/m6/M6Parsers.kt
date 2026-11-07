package com.replaybox.provider.m6

import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.provider.api.CataloguePage
import com.replaybox.provider.api.IndexEntry
import com.replaybox.provider.tf1.StreamResolutionException
import com.replaybox.provider.tf1.Tf1Parsers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure parsers of the 6play middleware (platform m6group_web, SPEC §Provider M6+), tested on
 * fixtures trimmed from the 2026-11-07 captures.
 *
 * Ids are namespaced with [ID_PREFIX] so they can be routed to M6 later:
 * Show `m6:{service}:{programId}`, Season `m6:{service}:{programId}:{season or ""}`,
 * Episode `m6:{service}:{clipId}`.
 */
object M6Parsers {
    const val ID_PREFIX = "m6:"
    internal const val SEPARATOR = ":"
    internal const val UNNUMBERED_SEASON = "Épisodes"

    /**
     * Images carry only an `external_key`; the raw file is served at this URL (measured
     * 2026-11-07: 200 anonymous, v1 and v2 identical). Resized variants need a signed `hash`
     * parameter (403 without), so the raw file is used.
     */
    private const val IMAGE_URL = "https://images.6play.fr/v2/images/%s/raw"

    private const val DASH_ASSET = "usp_dashcenc_h264"

    private val json = Json { ignoreUnknownKeys = true }

    // ---- Catalogue (Index pages) ----

    /**
     * One catalogue page (`/services/6play/programs`, JSON array). [totalHeader] is
     * `x-rest-collection-count`; without it, a full page means there may be more.
     */
    fun catalogPage(body: String, totalHeader: Int?, pageIndex: Int, pageSize: Int): CataloguePage {
        val items = objects(json.parseToJsonElement(body))
        val entries = items.mapNotNull { item ->
            val service = str(item, "service_display", "code")
            val channel = M6Channels.forService(service) ?: return@mapNotNull null
            if ((int(item, "count", "vi") ?: 0) <= 0) return@mapNotNull null
            val programId = str(item, "id") ?: return@mapNotNull null
            val images = objects(get(item, "images"))
            IndexEntry(
                channel.id,
                Show(
                    id = ShowId(ID_PREFIX + service + SEPARATOR + programId),
                    title = str(item, "title") ?: "",
                    posterUrl = imageUrl(images, "portrait") ?: imageUrl(images, "cover"),
                    synopsis = str(item, "description")?.takeIf { it.isNotBlank() },
                    genres = M6Genres.map(str(item, "neustar", "category"), str(item, "program_type_wording", "code")),
                    backdropUrl = imageUrl(images, "jumbotron"),
                    // No recency signal in the catalogue (count.vi is the whole back-catalogue).
                    recentEpisodeCount = 0,
                ),
            )
        }
        val hasMore = items.isNotEmpty() &&
            if (totalHeader != null) (pageIndex + 1) * pageSize < totalHeader else items.size >= pageSize
        return CataloguePage(entries, hasMore)
    }

    private fun imageUrl(images: List<JsonObject>, role: String): String? =
        images.firstOrNull { str(it, "role") == role }?.let { str(it, "external_key") }?.let { IMAGE_URL.format(it) }

    // ---- Videos of a Programme ----

    data class VideoPage(val episodes: List<M6Video>, val total: Int?)

    data class M6Video(
        val clipId: String,
        val title: String,
        val synopsis: String?,
        val season: Int?,
        val episode: Int?,
        val part: Int?,
        val firstDiffusion: String?,
        val durationSeconds: Int?,
        val thumbnailUrl: String?,
    ) {
        /** [firstDiffusion] read as Paris local time (SPEC §Provider M6+). */
        val broadcastAt: Instant? get() = firstDiffusion?.let(::parisInstant)
    }

    /** One page of `/programs/{id}/videos?type=vi`; [totalHeader] is `x-rest-collection-count`. */
    fun videoPage(body: String, totalHeader: Int?): VideoPage {
        val videos = objects(json.parseToJsonElement(body)).mapNotNull { item ->
            if (str(item, "type") != null && str(item, "type") != "vi") return@mapNotNull null
            val clipId = str(item, "id") ?: return@mapNotNull null
            val product = objects(get(item, "clips")).firstOrNull()?.let { get(it, "product") }
            val thumbnailKey = str(item, "display_image", "external_key")
                ?: objects(get(item, "images")).firstOrNull { str(it, "role") == "vignette" }?.let { str(it, "external_key") }
            M6Video(
                clipId = clipId,
                title = str(item, "title") ?: "",
                synopsis = str(item, "description")?.takeIf { it.isNotBlank() },
                season = int(product, "season"),
                episode = int(product, "episode"),
                part = int(product, "part_number"),
                firstDiffusion = str(product, "first_diffusion"),
                durationSeconds = int(item, "duration"),
                thumbnailUrl = thumbnailKey?.let { IMAGE_URL.format(it) },
            )
        }
        return VideoPage(videos, totalHeader)
    }

    /**
     * Numbered Saisons newest first (as Tf1Parsers.seasonsFromVideos). Videos without a season
     * number get one unnumbered Saison « Épisodes », last — the only one for magazines and films.
     */
    fun seasons(showId: ShowId, videos: List<M6Video>): List<Season> {
        val numbered = videos.mapNotNull { it.season }.distinct().sortedDescending()
            .map { n -> Season(SeasonId(showId.value + SEPARATOR + n), showId, n, "Saison $n") }
        val unnumbered = if (videos.any { it.season == null } || numbered.isEmpty()) {
            listOf(Season(SeasonId(showId.value + SEPARATOR), showId, null, UNNUMBERED_SEASON))
        } else {
            emptyList()
        }
        return numbered + unnumbered
    }

    /**
     * The Épisodes of [seasonId], by episode number then part (unnumbered last, newest first),
     * so a numbered Saison plays in order.
     */
    fun episodes(seasonId: SeasonId, serviceCode: String, videos: List<M6Video>): List<Episode> {
        val wanted = seasonId.value.substringAfterLast(SEPARATOR)
        return videos
            .filter { (it.season?.toString() ?: "") == wanted }
            .sortedWith(
                compareBy<M6Video, Int?>(nullsLast()) { it.episode }
                    .thenBy(nullsFirst()) { it.part }
                    .thenByDescending { it.firstDiffusion ?: "" },
            )
            .map { v ->
                Episode(
                    id = EpisodeId(ID_PREFIX + serviceCode + SEPARATOR + v.clipId),
                    seasonId = seasonId,
                    title = v.title.ifEmpty { v.clipId },
                    synopsis = v.synopsis,
                    broadcastDate = v.firstDiffusion?.let(::displayDate),
                    durationSeconds = v.durationSeconds,
                    thumbnailUrl = v.thumbnailUrl,
                    // Measured 2026-11-07: `publication_date` is rewritten a week after the
                    // broadcast and no end of availability is listed, so only this instant.
                    broadcastAt = v.broadcastAt,
                    number = v.episode,
                )
            }
    }

    private val SOURCE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** `first_diffusion` is Paris local time « 2026-05-22 22:10:00 »; shown « 22/05/2026 ». */
    private fun displayDate(raw: String): String? =
        runCatching { DISPLAY_DATE.format(LocalDateTime.parse(raw, SOURCE_DATE)) }.getOrNull()

    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    /** Paris wall-clock time to an instant; in the autumn overlap hour the earlier offset wins. */
    internal fun parisInstant(raw: String): Instant? =
        runCatching { LocalDateTime.parse(raw, SOURCE_DATE).atZone(PARIS).toInstant() }.getOrNull()

    // ---- One video → manifest ----

    /**
     * The Widevine DASH manifest of one video (`/videos/{clipId}`): the first
     * `usp_dashcenc_h264` asset of `clips[0]`, `hd` before `sd`. `usp_hlsfp_h264` is FairPlay
     * and never used. No DASH asset → [StreamResolutionException] (not an account error).
     */
    fun dashManifest(videoBody: String): String {
        val video = json.parseToJsonElement(videoBody) as? JsonObject
        val assets = objects(get(objects(get(video, "clips")).firstOrNull(), "assets"))
            .filter { str(it, "type") == DASH_ASSET && !str(it, "full_physical_path").isNullOrBlank() }
        val best = assets.firstOrNull { str(it, "video_quality") == "hd" } ?: assets.firstOrNull()
        return best?.let { str(it, "full_physical_path") }
            ?: throw StreamResolutionException(
                Tf1Parsers.Delivery.Failure(null, null, "No DASH asset for this M6 video", false, false),
            )
    }

    // ---- Ids ----

    /** `m6:{service}:{programId}` → (service, programId). */
    internal fun showParts(showId: ShowId): Pair<String, String> {
        val rest = showId.value.removePrefix(ID_PREFIX)
        return rest.substringBefore(SEPARATOR) to rest.substringAfter(SEPARATOR)
    }

    /** `m6:{service}:{programId}:{season}` → the Show id. */
    internal fun showOf(seasonId: SeasonId): ShowId = ShowId(seasonId.value.substringBeforeLast(SEPARATOR))

    /** `m6:{service}:{clipId}` → (service, clipId). */
    internal fun episodeParts(episodeId: EpisodeId): Pair<String, String> {
        val rest = episodeId.value.removePrefix(ID_PREFIX)
        return rest.substringBefore(SEPARATOR) to rest.substringAfter(SEPARATOR)
    }

    // ---- JSON helpers ----

    private fun get(element: JsonElement?, vararg path: String): JsonElement? {
        var current: JsonElement? = element
        for (key in path) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current
    }

    private fun str(element: JsonElement?, vararg path: String): String? =
        (get(element, *path) as? JsonPrimitive)?.takeUnless { it.content == "null" && !it.isString }?.content

    private fun int(element: JsonElement?, vararg path: String): Int? = str(element, *path)?.toIntOrNull()

    private fun objects(element: JsonElement?): List<JsonObject> =
        (element as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
