package com.replaybox.provider.francetv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class FtvHttpException(message: String) : Exception(message)

/**
 * HTTP layer of the France TV provider (SPEC §Provider France TV).
 * Catalogue: GET https://api-mobile.yatta.francetv.fr/apps/... with a browser UA.
 * Flux: GET https://k7.ftven.fr/videos/<si_id> then signing at
 * video.token (hdfauth.ftven.fr) — short-lived signed manifests, no DRM.
 *
 * Bodies are decoded from the stream, never read into a String, and every body is capped:
 * past [jsonMaxBytes] (whole-tree JSON) or [programPageMaxBytes] (Programme detail pages) the
 * call fails with [FtvHttpException] instead of exhausting the heap.
 */
open class FtvHttp(
    private val client: OkHttpClient = FtvHttp.defaultClient(),
    private val jsonMaxBytes: Long = JSON_MAX_BYTES,
    private val programPageMaxBytes: Long = PROGRAM_PAGE_MAX_BYTES,
) {

    suspend fun channel(slug: String): JsonObject = get(channelUrl(slug))

    fun channelUrl(slug: String): String = "$YATTA/apps/channels/$slug?platform=apps"

    /**
     * The whole replay of a Programme, read by pages of [PROGRAM_PAGE_SIZE] (see
     * [FtvProgramPages]): `size=2000` in one call weighed 28.5 MB for « Ça commence
     * aujourd'hui » and ran the Bbox out of memory (measured 2026-11-07).
     */
    suspend fun program(programPath: String): JsonObject = withContext(Dispatchers.IO) {
        FtvProgramPages.collect(PROGRAM_PAGE_SIZE, MAX_PROGRAM_PAGES) { page ->
            fetch(programUrl(programPath, page), FtvProgramPages.Page.serializer(), programPageMaxBytes)
        }
    }

    /** yatta's `page` is 0-based; `page=0` gives the same response as no `page`. */
    fun programUrl(programPath: String, page: Int = 0): String =
        "$YATTA/apps/program/$programPath?platform=apps&size=$PROGRAM_PAGE_SIZE&page=$page"

    fun programMetaUrl(programPath: String): String = "$YATTA/apps/program/meta/$programPath?platform=apps"

    suspend fun k7(siId: String): JsonObject = get(k7Url(siId))

    fun k7Url(siId: String): String =
        "$K7/videos/$siId?country_code=FR&capabilities=drm&os=androidtv&offline=false"

    /** Signs a manifest at the hdfauth base (k7 gives it in video.token). */
    suspend fun sign(manifestUrl: String, tokenBase: String): JsonObject =
        get("$tokenBase&url=${encode(manifestUrl)}")

    private suspend fun get(url: String): JsonObject = withContext(Dispatchers.IO) {
        fetch(url, JsonObject.serializer(), jsonMaxBytes)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> fetch(url: String, deserializer: DeserializationStrategy<T>, maxBytes: Long): T {
        val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
        return client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw FtvHttpException("HTTP ${response.code} for $url")
            }
            // -1 when unknown (chunked, or gzip that OkHttp decompresses transparently).
            if (body.contentLength() > maxBytes) throw tooLarge(url, maxBytes)
            CappedInputStream(body.byteStream(), maxBytes) { tooLarge(url, maxBytes) }.use { stream ->
                FtvJson.json.decodeFromStream(deserializer, stream)
            }
        }
    }

    private fun tooLarge(url: String, maxBytes: Long) =
        FtvHttpException("Response body over $maxBytes bytes for $url")

    /** Throws [onOverflow] once more than [maxBytes] bytes have been read. */
    private class CappedInputStream(
        input: InputStream,
        private val maxBytes: Long,
        private val onOverflow: () -> Exception,
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) add(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) add(n.toLong())
            return n
        }

        override fun skip(n: Long): Long {
            val skipped = super.skip(n)
            add(skipped)
            return skipped
        }

        private fun add(n: Long) {
            count += n
            if (count > maxBytes) throw onOverflow()
        }
    }

    companion object {
        const val YATTA = "https://api-mobile.yatta.francetv.fr"
        const val K7 = "https://k7.ftven.fr"
        const val HDFAUTH = "https://hdfauth.ftven.fr/esi/TA?format=json"
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        /** yatta's default collection cap, given explicitly so a full page is recognisable. */
        const val PROGRAM_PAGE_SIZE = 250

        /** 5000 items: 10x the longest list measured (« Ça commence aujourd'hui », 498 items). */
        const val MAX_PROGRAM_PAGES = 20

        /**
         * Programme detail page cap: the heaviest page measured is 8.2 MB (« Ça commence
         * aujourd'hui », size=250). Only the declared fields of a page stay in memory.
         */
        const val PROGRAM_PAGE_MAX_BYTES = 32L * 1024 * 1024

        /** Whole-tree JSON cap (Chaîne listings, k7, hdfauth): the largest listing is ~1 MB. */
        const val JSON_MAX_BYTES = 8L * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        fun encode(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
