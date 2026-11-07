package com.replaybox.provider.tf1

import com.replaybox.core.account.AccountFailure
import com.replaybox.core.model.Episode
import com.replaybox.core.model.Genre
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Stream
import com.replaybox.core.model.StreamType
import com.replaybox.provider.api.CataloguePage
import com.replaybox.provider.api.IndexEntry
import kotlinx.serialization.json.JsonObject

/**
 * Pure parsers for TF1 graphql / mediainfo payloads.
 * Every function is tested against JSON fixtures captured from the real APIs
 * (src/test/resources/fixtures) — no network involved.
 */
object Tf1Parsers {

    internal const val SEPARATOR = "::"
    internal const val REPLAY_TYPE = "REPLAY"

    // ---- Programs (listing + search share the same Program shape) ----

    fun showsFromPrograms(root: JsonObject): List<Show> =
        Tf1Json.objects(Tf1Json.array(root, "data", "programs", "items")).map { showFromProgram(it) }

    fun showsFromSearch(root: JsonObject): List<Show> =
        Tf1Json.objects(Tf1Json.array(root, "data", "searchPrograms", "items")).map { showFromProgram(it) }

    fun showsFromCategories(root: JsonObject): List<String> =
        Tf1Json.obj(root, "data")?.entries
            ?.mapNotNull { it.value as? JsonObject }
            ?.mapNotNull { Tf1Json.str(it, "id") }
            ?.filter { it.startsWith("GT_") }
            .orEmpty()

    // ---- Index page (unfiltered programs 483ce0f, SPEC §Provider TF1+) ----

    /**
     * One page of the TF1 Index: programmes of the 5 group Chaînes (initialChannel.slug) that
     * have replay Épisodes. Police series get « Policier & Thriller » from Tf1CrimeKeywords
     * (GT_SERIES_AND_FICTIONS only: films keep their native SGT_THRILLER / SGT_DETECTIVE).
     * [pageIndex] is the page index sent in `offset`.
     */
    fun indexPage(root: JsonObject, pageIndex: Int, pageSize: Int): CataloguePage {
        val programs = Tf1Json.obj(root, "data", "programs")
        val total = Tf1Json.int(programs, "total") ?: 0
        val items = Tf1Json.objects(Tf1Json.array(programs, "items"))
        val entries = items.mapNotNull { item ->
            val channelId = Tf1Json.str(item, "initialChannel", "slug")
            if (!Tf1Channels.isGroup(channelId)) return@mapNotNull null
            if ((Tf1Json.int(item, "replay", "total") ?: 0) <= 0) return@mapNotNull null
            val base = showFromProgram(item)
            val crime = SERIES_ROOT in categoryIds(item) && Genre.YOUTH !in base.genres &&
                Tf1CrimeKeywords.matches(base.id.value, base.title, base.synopsis)
            IndexEntry(
                channelId!!,
                base.copy(
                    genres = if (crime) base.genres + Genre.CRIME_THRILLER else base.genres,
                    recentEpisodeCount = Tf1Json.int(item, "replayRecent", "total") ?: 0,
                ),
            )
        }
        // An empty page ends the crawl even if the total promised more (catalogue shrinking mid-crawl).
        return CataloguePage(entries, hasMore = items.isNotEmpty() && (pageIndex + 1) * pageSize < total)
    }

    private const val SERIES_ROOT = "GT_SERIES_AND_FICTIONS"

    private fun categoryIds(item: JsonObject): List<String> =
        Tf1Json.objects(Tf1Json.array(item, "categories")).mapNotNull { Tf1Json.str(it, "id") }

    private fun showFromProgram(item: JsonObject): Show {
        val slug = Tf1Json.str(item, "slug") ?: ""
        return Show(
            id = ShowId(slug),
            title = Tf1Json.str(item, "name") ?: "",
            posterUrl = posterFrom(item),
            synopsis = Tf1Json.str(item, "decoration", "description"),
            genres = Tf1Genres.forProgram(slug, categoryIds(item)),
            backdropUrl = backdropFrom(item),
        )
    }

    /** decoration.background is the 16:9 "background-ott" key art (SPEC §Images). */
    private fun backdropFrom(item: JsonObject): String? =
        Tf1Json.objects(Tf1Json.array(item, "decoration", "background", "sources"))
            .filter { (Tf1Json.str(it, "type") ?: "").lowercase() != "mp4" }
            .maxByOrNull { Tf1Json.int(it, "width") ?: 0 }
            ?.let { Tf1Json.str(it, "url") }

    private fun posterFrom(item: JsonObject): String? {
        val sources = Tf1Json.objects(Tf1Json.array(item, "decoration", "image", "sources"))
        return sources
            .filter { (Tf1Json.str(it, "type") ?: "").lowercase() != "mp4" }
            .maxByOrNull { Tf1Json.int(it, "width") ?: 0 }
            ?.let { Tf1Json.str(it, "url") }
    }

    // ---- Search videos (SPEC id=b2dc9439) ----

    fun searchVideoTitles(root: JsonObject): List<String> =
        Tf1Json.objects(Tf1Json.array(root, "data", "searchVideos", "items"))
            .mapNotNull { Tf1Json.str(it, "decoration", "label") }

    // ---- Replay videos of a program (id=a6f9cf0e, types=[REPLAY]) ----

    data class ReplayPage(
        val total: Int,
        val videos: List<ReplayVideo>,
        /** Items TF1 returned on this page, before any filtering: 0 means past the last page. */
        val itemCount: Int = videos.size,
    )

    data class ReplayVideo(
        val slug: String,
        val title: String,
        val synopsis: String?,
        val date: String?,
        val durationSeconds: Int?,
        val season: Int?,
        val episode: Int?,
        val thumbnailUrl: String?,
        /** `date`: broadcast start (ISO UTC). Old back-catalogue carries placeholders. */
        val broadcastAt: java.time.Instant? = null,
        /** `published`: start of online availability (ISO UTC). */
        val publishedAt: java.time.Instant? = null,
        /** Upper bound of the end of availability, from `playingInfos.remainingDays`. */
        val availableUntil: java.time.Instant? = null,
    )

    /**
     * One page of a programme's replay videos. [fetchedAt] anchors `remainingDays` when the
     * response has no `extensions.report.execution.start` (server instant, epoch ns).
     * Videos outside the free TF1+ offer (avant-premières) are dropped, see [isFreeOffer].
     */
    fun replayPage(root: JsonObject, fetchedAt: java.time.Instant): ReplayPage {
        val videos = Tf1Json.obj(root, "data", "programBySlug", "videos") ?: return ReplayPage(0, emptyList())
        val total = Tf1Json.int(videos, "total") ?: 0
        val raw = Tf1Json.objects(videos["items"])
        val items = raw
            .filter { Tf1Json.str(it, "type") == REPLAY_TYPE && isFreeOffer(it) }
            .map { replayVideoFrom(it, serverInstant(root) ?: fetchedAt) }
        return ReplayPage(total, items, itemCount = raw.size)
    }

    /** Right of the free TF1+ offer; `MAX` is the paid one (SPEC §Provider TF1+). */
    internal const val FREE_RIGHT = "BASIC"

    /**
     * Measured 2026-11-07: ordinary Épisodes carry `rights: ["BASIC","MAX"]`, avant-premières
     * (paid offer, broadcast days later) only `["MAX"]`, with `freeRemainingDays` 0. The
     * household account is a free one, and a video without the BASIC right belongs to the paid
     * offer only (the title prefix is unreliable: HPI S04E04 lost it). No `rights` field: kept.
     */
    private fun isFreeOffer(item: JsonObject): Boolean {
        val rights = Tf1Json.array(item, "rights") ?: return true
        return rights.any { (it as? kotlinx.serialization.json.JsonPrimitive)?.content == FREE_RIGHT }
    }

    private fun replayVideoFrom(item: JsonObject, countedAt: java.time.Instant): ReplayVideo = ReplayVideo(
        slug = Tf1Json.str(item, "slug") ?: "",
        title = Tf1Json.str(item, "decoration", "label") ?: "",
        synopsis = Tf1Json.str(item, "decoration", "description"),
        date = Tf1Json.str(item, "date"),
        durationSeconds = Tf1Json.int(item, "playingInfos", "duration"),
        season = Tf1Json.int(item, "season"),
        episode = Tf1Json.int(item, "episode"),
        thumbnailUrl = thumbnailFrom(item),
        broadcastAt = Tf1Json.str(item, "date")?.let(::isoInstant),
        publishedAt = Tf1Json.str(item, "published")?.let(::isoInstant),
        availableUntil = Tf1Json.int(item, "playingInfos", "remainingDays")
            ?.takeIf { it >= 0 }
            ?.let { countedAt.plus(java.time.Duration.ofDays(it.toLong())) },
    )

    /**
     * TF1 gives no end date (measured 2026-11-07): `remainingDays` is the whole number of days
     * left, rounded up, when the server answered. Anchored on that instant, the result is an
     * upper bound: availability really ends during the last 24 h before it.
     */
    private fun serverInstant(root: JsonObject): java.time.Instant? {
        val nanos = Tf1Json.str(root, "extensions", "report", "execution", "start")?.toLongOrNull() ?: return null
        return java.time.Instant.ofEpochSecond(nanos / 1_000_000_000, nanos % 1_000_000_000)
    }

    private fun isoInstant(raw: String): java.time.Instant? =
        runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrNull()

    private fun thumbnailFrom(item: JsonObject): String? {
        val images = Tf1Json.objects(Tf1Json.array(item, "decoration", "images"))
        val thumbnail = images.firstOrNull { Tf1Json.str(it, "type") == "THUMBNAIL" } ?: return null
        return Tf1Json.objects(thumbnail["sources"])
            .filter { (Tf1Json.str(it, "type") ?: "").lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            .maxByOrNull { Tf1Json.int(it, "width") ?: 0 }
            ?.let { Tf1Json.str(it, "url") }
    }

    // ---- videoBySlug (yt-dlp hash) → streamId ----

    data class VideoDetail(
        val slug: String,
        val streamId: String?,
        val title: String?,
        val season: Int?,
        val episode: Int?,
        val type: String?,
    )

    fun videoDetail(root: JsonObject): VideoDetail? {
        val video = Tf1Json.obj(root, "data", "videoBySlug") ?: return null
        return VideoDetail(
            slug = Tf1Json.str(video, "slug") ?: "",
            streamId = Tf1Json.str(video, "streamId"),
            title = Tf1Json.str(video, "title"),
            season = Tf1Json.int(video, "season"),
            episode = Tf1Json.int(video, "episode"),
            type = Tf1Json.str(video, "type"),
        )
    }

    // ---- Season / Episode assembly ----

    fun seasonsFromVideos(showId: ShowId, videos: List<ReplayVideo>): List<Season> {
        val numbered = videos.mapNotNull { it.season }.distinct().sortedDescending()
        if (numbered.isEmpty()) {
            return listOf(Season(SeasonId(showId.value + SEPARATOR), showId, null, "Divers"))
        }
        return numbered.map { n ->
            Season(SeasonId(showId.value + SEPARATOR + n), showId, n, "Saison $n")
        }
    }

    private val DATE_FORMAT: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(java.time.ZoneId.of("Europe/Paris"))

    /** TF1 dates are ISO instants; shown like France TV's (« 04/11/2026 », Paris). Unparsable: none. */
    private fun displayDate(raw: String): String? =
        runCatching { DATE_FORMAT.format(java.time.OffsetDateTime.parse(raw).toInstant()) }.getOrNull()

    fun episodesFromVideos(seasonId: SeasonId?, showId: ShowId, videos: List<ReplayVideo>): List<Episode> {
        val filtered = if (seasonId == null) videos else {
            val wanted = seasonId.value.substringAfter(SEPARATOR)
            videos.filter { it.season?.toString() == wanted || (wanted.isEmpty() && it.season == null) }
        }
        return filtered.map { v ->
            Episode(
                id = EpisodeId(showId.value + SEPARATOR + v.slug),
                seasonId = seasonId ?: SeasonId(showId.value + SEPARATOR + (v.season ?: "")),
                title = v.title.ifEmpty { v.slug },
                synopsis = v.synopsis,
                broadcastDate = v.date?.let(::displayDate),
                durationSeconds = v.durationSeconds,
                thumbnailUrl = v.thumbnailUrl,
                broadcastAt = v.broadcastAt,
                publishedAt = v.publishedAt,
                availableUntil = v.availableUntil,
                number = v.episode,
            )
        }
    }

    // ---- mediainfo delivery (faithful port of yt-dlp yt_dlp/extractor/wat.py) ----

    sealed interface Delivery {
        data class Ok(
            val manifestUrl: String,
            val type: StreamType,
            val licenseUrl: String? = null,
            val licenseHeaders: Map<String, String> = emptyMap(),
        ) : Delivery
        data class Failure(
            val httpDeliveryCode: Int?,
            val errorCode: String?,
            val description: String?,
            val geoBlocked: Boolean,
            val accountOrDrm: Boolean,
        ) : Delivery
    }

    fun delivery(root: JsonObject): Delivery {
        val delivery = Tf1Json.obj(root, "delivery")
        val desc = Tf1Json.str(root, "media", "error_desc")
        val errorCode = Tf1Json.str(root, "media", "error_code")
        if (!desc.isNullOrBlank()) {
            val geo = errorCode == "GEOBLOCKED"
            val deliveryCode = Tf1Json.int(root, "delivery", "code")
            // yt-dlp reports DRM for DELIVERY_ERROR + code 403/500. Measured 2026-11-05:
            // TF1 also answers PERMISSION_DENIED + 403 when the episode requires a
            // signed-in account — both mean the anonymous Flux is unavailable.
            val drm = errorCode == "DELIVERY_ERROR" && deliveryCode in setOf(403, 500)
            val account = drm || errorCode == "PERMISSION_DENIED"
            return Delivery.Failure(deliveryCode, errorCode, desc, geo, account)
        }
        val url = Tf1Json.str(delivery, "url") ?: return Delivery.Failure(
            Tf1Json.int(delivery, "code"), null, null, false, false,
        )
        val format = (Tf1Json.str(delivery, "format") ?: "").lowercase()
        val type = if (format == "hls") StreamType.HLS else StreamType.DASH
        val manifest = if (type == StreamType.DASH) {
            // yt-dlp: dash and mpd are both DASH; das-q1 is rewritten to its ssl host.
            url.replace("://das-q1.tf1.fr/", "://das-q1-ssl.tf1.fr/")
        } else {
            url
        }
        val license = licenseFrom(delivery)
        return Delivery.Ok(manifest, type, license?.first, license?.second ?: emptyMap())
    }

    /**
     * Widevine license attached by TF1 when the content is DRM-protected
     * (delivery.drms[0]: {name: "widevine", url, h: [{k, v}]} — contract of the
     * authenticated mediainfo response, cf. cloudstream TF1PlaybackPayload).
     */
    fun licenseFrom(delivery: JsonObject?): Pair<String, Map<String, String>>? {
        val drms = Tf1Json.objects(Tf1Json.array(delivery, "drms"))
        val widevine = drms.firstOrNull {
            (Tf1Json.str(it, "name") ?: "").equals("widevine", ignoreCase = true)
        } ?: return null
        val licenseUrl = Tf1Json.str(widevine, "url") ?: return null
        val headers = Tf1Json.objects(Tf1Json.array(widevine, "h"))
            .mapNotNull { header ->
                val key = Tf1Json.str(header, "k") ?: Tf1Json.str(header, "key") ?: return@mapNotNull null
                val value = Tf1Json.str(header, "v") ?: Tf1Json.str(header, "value") ?: return@mapNotNull null
                key to value
            }
            .toMap()
        return licenseUrl to headers
    }

    // ---- Gigya login payloads (compte.tf1.fr) ----

    data class GigyaIdentity(val uid: String, val signature: String, val timestamp: Long)

    fun gigyaIdentity(login: JsonObject): GigyaIdentity {
        val errorCode = Tf1Json.int(login, "errorCode")
        if (errorCode != 0) {
            throw Tf1AuthException(
                "TF1 sign-in rejected: " + (Tf1Json.str(login, "errorMessage") ?: "invalid credentials"),
                gigyaFailure(errorCode),
                errorCode?.toString(),
            )
        }
        val user = Tf1Json.obj(login, "userInfo") ?: login
        val uid = Tf1Json.str(user, "UID") ?: throw Tf1AuthException("Missing TF1 user id")
        val signature = Tf1Json.str(user, "UIDSignature") ?: throw Tf1AuthException("Missing TF1 signature")
        val timestamp = (Tf1Json.str(user, "signatureTimestamp") ?: "").toDoubleOrNull()?.toLong()
            ?: throw Tf1AuthException("Missing TF1 signature timestamp")
        return GigyaIdentity(uid, signature, timestamp)
    }

    /** Gigya `errorCode` of a refused accounts.login, shared by the TF1+ and M6+ sign-ins. */
    fun gigyaFailure(errorCode: Int?): AccountFailure = when (errorCode) {
        // 403042 "invalid loginID or password" (measured, tf1_login_invalid_credentials.json).
        403042 -> AccountFailure.INVALID_CREDENTIALS
        // 403120 "Account temporarily locked out" (Gigya error reference).
        403120 -> AccountFailure.ACCOUNT_LOCKED
        else -> AccountFailure.REJECTED
    }

    fun streamFrom(delivery: Delivery): Stream = when (delivery) {
        is Delivery.Ok -> Stream(delivery.manifestUrl, delivery.type, delivery.licenseUrl, delivery.licenseHeaders)
        is Delivery.Failure -> throw StreamResolutionException(delivery)
    }
}

class StreamResolutionException(val failure: Tf1Parsers.Delivery.Failure) :
    Exception(failure.description ?: failure.errorCode ?: "TF1 delivery failure") {

    val accountOrDrmRequired: Boolean get() = failure.accountOrDrm
    val geoBlocked: Boolean get() = failure.geoBlocked
}
