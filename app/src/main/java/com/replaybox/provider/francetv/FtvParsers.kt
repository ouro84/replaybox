package com.replaybox.provider.francetv

import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.StreamType
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure parsers for France TV payloads (yatta, k7, hdfauth).
 * Every function is tested against JSON fixtures captured from the real APIs
 * (src/test/resources/fixtures/ftv_*.json, measured 2026-11-05) — no network.
 * Measured contract notes live in SPEC §Provider France TV.
 */
object FtvParsers {

    /** Id namespace so the app can route any id back to this provider. */
    const val ID_PREFIX = "ftv::"
    internal const val SEPARATOR = "::"
    internal const val INTEGRALE = "integrale"
    internal const val MISC_SEASON = "Divers"

    private const val PLAYLIST_VIDEO = "playlist_video"
    private val SEASON_LABEL = Regex("^Saison\\s*(\\d+)$", RegexOption.IGNORE_CASE)
    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Europe/Paris"))

    // ---- Channel listing (flat items across all collections) ----

    fun showsFromChannel(root: JsonObject): List<Show> {
        val byPath = LinkedHashMap<String, Show>()
        for (collection in FtvJson.objects(root["collections"])) {
            for (item in FtvJson.objects(collection["items"])) {
                val path = FtvJson.str(item, "program_path") ?: continue
                if (byPath.containsKey(path)) continue
                byPath[path] = Show(
                    id = showIdFromPath(path),
                    title = FtvJson.str(item, "label") ?: "",
                    posterUrl = posterFrom(item),
                    synopsis = FtvJson.str(item, "synopsis") ?: FtvJson.str(item, "description"),
                    genres = emptySet(),
                )
            }
        }
        return byPath.values.toList()
    }

    // ---- Program detail: Seasons + Episodes ----

    fun seasonsFromProgram(root: JsonObject, showId: ShowId): List<Season> {
        val numbered = LinkedHashMap<Int, String>()
        var hasVideoCollection = false
        for (collection in FtvJson.objects(root["collections"])) {
            if (FtvJson.str(collection, "type") != PLAYLIST_VIDEO) continue
            hasVideoCollection = true
            val label = FtvJson.str(collection, "label") ?: continue
            val match = SEASON_LABEL.find(label) ?: continue
            val number = match.groupValues[1].toIntOrNull() ?: continue
            numbered.putIfAbsent(number, label)
        }
        return when {
            numbered.isNotEmpty() -> numbered.entries
                .sortedByDescending { it.key }
                .map { (n, label) -> Season(seasonId(showId, n), showId, n, label) }
            // Magazine: the Chaîne does not number episodes — one Season holds
            // every integrale (glossary: Season absent when unnumbered).
            hasVideoCollection -> listOf(Season(seasonId(showId, null), showId, null, MISC_SEASON))
            else -> emptyList()
        }
    }

    fun episodesFromProgram(root: JsonObject, season: SeasonId?): List<Episode> {
        val wanted = season?.value?.substringAfterLast(SEPARATOR)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val bySiId = LinkedHashMap<String, Episode>()
        for (collection in FtvJson.objects(root["collections"])) {
            if (FtvJson.str(collection, "type") != PLAYLIST_VIDEO) continue
            for (item in FtvJson.objects(collection["items"])) {
                if (FtvJson.str(item, "type") != INTEGRALE) continue
                val siId = FtvJson.str(item, "si_id") ?: continue
                val itemSeason = FtvJson.int(item, "season")
                if (wanted != null && itemSeason != wanted) continue
                if (bySiId.containsKey(siId)) continue
                bySiId[siId] = Episode(
                    id = EpisodeId(ID_PREFIX + siId),
                    seasonId = season,
                    title = FtvJson.str(item, "episode_title")
                        ?: FtvJson.str(item, "title")
                        ?: siId,
                    synopsis = FtvJson.str(item, "description"),
                    broadcastDate = broadcastDateFrom(item),
                    durationSeconds = FtvJson.int(item, "duration"),
                    thumbnailUrl = vignetteFrom(item),
                )
            }
        }
        return bySiId.values.toList()
    }

    /** Glossary: an Episode is dated by broadcast; yatta gives unix seconds. */
    private fun broadcastDateFrom(item: JsonObject): String? {
        val seconds = FtvJson.int(item, "broadcast_begin_date")
            ?: FtvJson.int(item, "begin_date")
            ?: return null
        return DATE_FORMAT.format(Instant.ofEpochSecond(seconds.toLong()))
    }

    // ---- Flux: k7 + hdfauth (see SPEC for the measured contract) ----

    data class ResolvedFlux(
        val manifestUrl: String,
        val type: StreamType,
        val subtitleUrl: String?,
        val subtitleLanguage: String?,
        val signBase: String,
    )

    fun streamFromK7(root: JsonObject): ResolvedFlux {
        val video = FtvJson.obj(root, "video")
            ?: throw FtvHttpException("k7 response has no video object")
        val drm = FtvJson.bool(video, "drm")
        if (drm == true) throw FtvHttpException("France TV flux is DRM-protected: not supported")
        val url = FtvJson.str(video, "url")
            ?: throw FtvHttpException("k7 response has no video.url")
        val format = (FtvJson.str(video, "format") ?: "dash").lowercase()
        val subtitle = FtvJson.objects(FtvJson.array(root, "meta", "subtitles")).firstOrNull()
        return ResolvedFlux(
            manifestUrl = url,
            type = if (format == "hls") StreamType.HLS else StreamType.DASH,
            subtitleUrl = subtitle?.let { FtvJson.str(it, "url") },
            subtitleLanguage = subtitle?.let { FtvJson.str(it, "lang") },
            // video.token carries the hdfauth signing base; SPEC constant as fallback.
            signBase = FtvJson.str(video, "token") ?: FtvHttp.HDFAUTH,
        )
    }

    fun signedUrl(hdfauth: JsonObject): String? = FtvJson.str(hdfauth, "url")

    // ---- Image picking ----

    /**
     * urls are shaped {"w:800": url, "w:0": empty}; pick the widest non-empty
     * entry of the requested image type, falling back to whatever exists.
     */
    private fun imageOf(item: JsonObject, vararg types: String): String? {
        val list = FtvJson.objects(item["images"])
        for (type in types) {
            val urls = list.firstOrNull { FtvJson.str(it, "type") == type }?.get("urls")
                ?: continue
            val best = (urls as? JsonObject)?.entries
                ?.mapNotNull { (key, value) ->
                    val width = key.removePrefix("w:").toIntOrNull() ?: return@mapNotNull null
                    val url = (value as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: return@mapNotNull null
                    if (width <= 0) null else width to url
                }
                ?.maxByOrNull { it.first }
                ?.second
            if (best != null) return best
        }
        return null
    }

    private fun posterFrom(item: JsonObject): String? =
        imageOf(item, "vignette_3x4", "vignette_2x3", "background_16x9", "vignette_16x9")

    private fun vignetteFrom(item: JsonObject): String? =
        imageOf(item, "vignette_16x9", "vignette_3x4", "vignette_2x3")

    // ---- Id plumbing ----

    internal fun showIdFromPath(path: String): ShowId = ShowId(ID_PREFIX + path)

    internal fun programPath(showId: ShowId): String = showId.value.removePrefix(ID_PREFIX)

    internal fun programPathOfSeason(seasonId: SeasonId): String =
        seasonId.value.removePrefix(ID_PREFIX).substringBeforeLast(SEPARATOR)

    internal fun siIdOf(episodeId: EpisodeId): String = episodeId.value.removePrefix(ID_PREFIX)

    internal fun seasonId(showId: ShowId, number: Int?): SeasonId =
        SeasonId(showId.value + SEPARATOR + (number?.toString() ?: ""))
}
