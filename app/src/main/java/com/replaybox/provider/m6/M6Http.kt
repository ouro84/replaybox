package com.replaybox.provider.m6

import com.replaybox.provider.tf1.Tf1Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class M6HttpException(val code: Int, message: String) : Exception(message)

/** A middleware answer: the body and the `x-rest-collection-count` header (list total). */
data class M6Response(val body: String, val totalCount: Int?)

/**
 * HTTP layer of the M6+ provider: the 6play middleware for platform `m6group_web`
 * (decision Q18, SPEC §Provider M6+). Every call carries `x-customer-name: m6web` and a
 * browser UA; nothing in this layer needs an account. Lists are capped at [PAGE_SIZE] per
 * call and paged by item `offset`.
 */
open class M6Http(private val client: OkHttpClient = defaultClient()) {

    /** GET [path] (relative to [HOST]); non-2xx → [M6HttpException] (the Index retries it). */
    open suspend fun get(path: String): M6Response = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(HOST + path)
            .header("User-Agent", Tf1Http.BROWSER_UA)
            .header("x-customer-name", CUSTOMER)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                throw M6HttpException(response.code, "HTTP ${response.code} for $path")
            }
            M6Response(body, response.header(COUNT_HEADER)?.trim()?.toIntOrNull())
        }
    }

    companion object {
        const val HOST = "https://pc.middleware.6play.fr/6play/v2/platforms/m6group_web"
        const val CUSTOMER = "m6web"
        const val COUNT_HEADER = "x-rest-collection-count"

        /** Hard cap of the middleware's `limit` (measured 2026-11-07). */
        const val PAGE_SIZE = 100

        fun catalogPath(offset: Int): String =
            "/services/6play/programs?limit=$PAGE_SIZE&offset=$offset&csa=6&with=rights"

        /** `type=vi`: full Épisodes only (no `vc` extracts). */
        fun videosPath(serviceCode: String, programId: String, offset: Int): String =
            "/services/$serviceCode/programs/$programId/videos?csa=6&with=clips,freemiumpacks&type=vi&limit=$PAGE_SIZE&offset=$offset"

        fun videoPath(serviceCode: String, clipId: String): String =
            "/services/$serviceCode/videos/$clipId?csa=6&with=clips,freemiumpacks"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
