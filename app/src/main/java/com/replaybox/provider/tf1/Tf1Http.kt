package com.replaybox.provider.tf1

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class Tf1HttpException(message: String) : Exception(message)

/**
 * HTTP layer of the TF1 provider.
 * Catalogue: GET https://www.tf1.fr/graphql/web?id=<persisted id>&variables=<json>
 * with browser-like headers (referer https://www.tf1.fr/programmes-tv + UA).
 * Delivery: GET https://mediainfo.tf1.fr/mediainfocombo/<media id>?pver=5010000
 * — exact chain of yt-dlp's wat.py extractor, no account, no DRM.
 */
open class Tf1Http(private val client: OkHttpClient = defaultClient()) {

    open suspend fun graphql(queryId: String, variables: JsonObject): JsonObject = get(
        "$GRAPHQL?id=$queryId&variables=${encode(variables.toString())}",
        referer = REFERER,
    )

    suspend fun mediainfo(mediaId: String, token: String? = null): JsonObject =
        get(mediainfoUrl(mediaId), referer = REFERER, token = token)

    /**
     * mediainfocombo with the full web-player parameter set (same contract as
     * cloudstream TF1Playback / the tf1.fr player). With only pver, the
     * authenticated delivery answers "Erreur de syndication"; with the app
     * context it returns the manifest (or the real rights error).
     */
    fun mediainfoUrl(mediaId: String): String =
        "$MEDIAINFO/${WatDelivery.normalizeMediaId(mediaId)}" +
            "?context=MYTF1&pver=5010000&platform=web&device=desktop&os=linux&osVersion=unknown" +
            "&topDomain=${encode("https://www.tf1.fr")}&playerVersion=5.19.0" +
            "&productName=mytf1&productVersion=3.22.0"

    private suspend fun get(url: String, referer: String?, token: String? = null): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
        referer?.let { builder.header("Referer", it) }
        token?.let { builder.header("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                throw Tf1HttpException("HTTP ${response.code} for $url")
            }
            Tf1Json.parse(body)
        }
    }

    fun programsVariables(channelSlug: String, offset: Int, limit: Int): JsonObject = buildJsonObject {
        put("context", buildJsonObject {
            put("persona", "PERSONA_2")
            put("application", "WEB")
            put("device", "DESKTOP")
            put("os", "WINDOWS")
        })
        put("filter", buildJsonObject { put("channel", channelSlug) })
        put("offset", offset)
        put("limit", limit)
    }

    /** [pageIndex] goes in `offset`: TF1 reads it as a page index, not an item index. */
    fun programVideosVariables(programSlug: String, pageIndex: Int, limit: Int): JsonObject = buildJsonObject {
        put("programSlug", programSlug)
        put("offset", pageIndex)
        put("limit", limit)
        put("sort", buildJsonObject {
            put("type", "DATE")
            put("order", "DESC")
        })
        put("types", buildJsonArray { add("REPLAY") })
    }

    fun searchProgramsVariables(query: String): JsonObject = buildJsonObject {
        put("query", query)
        put("offset", 0)
        put("limit", 100)
    }

    fun searchVideosVariables(query: String): JsonObject = buildJsonObject {
        put("query", query)
        put("offset", 0)
        put("limit", 100)
    }

    fun videoBySlugVariables(programSlug: String, slug: String): JsonObject = buildJsonObject {
        put("programSlug", programSlug)
        put("slug", slug)
    }

    companion object {
        const val GRAPHQL = "https://www.tf1.fr/graphql/web"
        const val MEDIAINFO = "https://mediainfo.tf1.fr/mediainfocombo"
        const val REFERER = "https://www.tf1.fr/programmes-tv"
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        const val PAGE_LIMIT = 20

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        fun encode(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
