package com.replaybox.provider.francetv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bounded read of a yatta Programme detail (SPEC §Provider France TV, « Plafond 250 »,
 * measured 2026-11-07).
 *
 * `size` inflates the `playlist_audio` podcasts collection as well as the episode list: with
 * `size=2000`, « Ça commence aujourd'hui » weighs 28.5 MB, 19.5 MB of it 2000 podcasts, and
 * reading it as one String ran the Bbox out of memory. The detail is therefore read by pages
 * of [pageSize]: `page=N` (0-based) pages every collection that hits the cap, the thematic
 * collections repeat unchanged on every page, and a collection with nothing left on a page is
 * absent from it. Pages are requested until no `playlist_video` collection is full.
 *
 * Pages are decoded from the stream into [Page], which declares only the fields the parsers
 * read (unknown keys are skipped by the decoder, never materialized), and the merged result
 * keeps only `playlist_video` collections and their `integrale` items: the parsers ignore
 * everything else.
 */
internal object FtvProgramPages {

    private const val PLAYLIST_VIDEO = "playlist_video"

    @Serializable
    class Page(val collections: List<Collection>? = null)

    @Serializable
    class Collection(
        val type: String? = null,
        val label: String? = null,
        val items: List<Item>? = null,
    )

    /** An item as [FtvParsers.episodesFromProgram] reads it; JSON nulls decode to absent. */
    @Serializable
    class Item(
        val type: String? = null,
        @SerialName("si_id") val siId: String? = null,
        val season: JsonElement? = null,
        val episode: JsonElement? = null,
        val title: JsonElement? = null,
        @SerialName("episode_title") val episodeTitle: JsonElement? = null,
        val description: JsonElement? = null,
        val duration: JsonElement? = null,
        @SerialName("begin_date") val beginDate: JsonElement? = null,
        @SerialName("end_date") val endDate: JsonElement? = null,
        @SerialName("broadcast_begin_date") val broadcastBeginDate: JsonElement? = null,
        @SerialName("broadcast_channel") val broadcastChannel: JsonElement? = null,
        val images: JsonElement? = null,
    ) {
        fun toJson(): JsonObject {
            val fields = LinkedHashMap<String, JsonElement>()
            type?.let { fields["type"] = JsonPrimitive(it) }
            siId?.let { fields["si_id"] = JsonPrimitive(it) }
            season?.let { fields["season"] = it }
            episode?.let { fields["episode"] = it }
            title?.let { fields["title"] = it }
            episodeTitle?.let { fields["episode_title"] = it }
            description?.let { fields["description"] = it }
            duration?.let { fields["duration"] = it }
            beginDate?.let { fields["begin_date"] = it }
            endDate?.let { fields["end_date"] = it }
            broadcastBeginDate?.let { fields["broadcast_begin_date"] = it }
            broadcastChannel?.let { fields["broadcast_channel"] = it }
            images?.let { fields["images"] = it }
            return JsonObject(fields)
        }
    }

    /**
     * Fetches pages 0, 1, … through [fetch] and merges them into a detail shaped like the yatta
     * response (`{"collections": [...]}`) for [FtvParsers]. A collection keeps receiving items
     * while its previous page was full; thematic repeats on later pages are skipped. At most
     * [maxPages] pages are read, so a misbehaving API cannot loop forever.
     */
    fun collect(pageSize: Int, maxPages: Int, fetch: (page: Int) -> Page): JsonObject {
        val labels = LinkedHashMap<String, String?>()
        val itemsByKey = LinkedHashMap<String, MutableList<JsonObject>>()
        var open: Set<String> = emptySet()
        var page = 0
        do {
            val full = HashSet<String>()
            for (collection in fetch(page).collections.orEmpty()) {
                if (collection.type != PLAYLIST_VIDEO) continue
                val key = collection.label.orEmpty()
                if (page > 0 && key !in open) continue
                val items = collection.items.orEmpty()
                labels.putIfAbsent(key, collection.label)
                val kept = itemsByKey.getOrPut(key) { ArrayList() }
                for (item in items) {
                    if (item.type == FtvParsers.INTEGRALE && item.siId != null) kept += item.toJson()
                }
                // Count every item, kept or not: the cap applies to the raw collection.
                if (items.size >= pageSize) full += key
            }
            open = full
            page++
        } while (open.isNotEmpty() && page < maxPages)

        val collections = itemsByKey.map { (key, items) ->
            val fields = LinkedHashMap<String, JsonElement>()
            fields["type"] = JsonPrimitive(PLAYLIST_VIDEO)
            labels[key]?.let { fields["label"] = JsonPrimitive(it) }
            fields["items"] = JsonArray(items)
            JsonObject(fields)
        }
        return JsonObject(mapOf("collections" to JsonArray(collections)))
    }
}
