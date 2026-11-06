package com.replaybox.provider.francetv

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class FtvHttpException(message: String) : Exception(message)

/**
 * HTTP layer of the France TV provider (SPEC §Provider France TV).
 * Catalogue: GET https://api-mobile.yatta.francetv.fr/apps/... with a browser UA.
 * Flux: GET https://k7.ftven.fr/videos/<si_id> then signing at
 * video.token (hdfauth.ftven.fr) — short-lived signed manifests, no DRM.
 */
open class FtvHttp(private val client: OkHttpClient = FtvHttp.defaultClient()) {

    suspend fun channel(slug: String): JsonObject = get(channelUrl(slug))

    fun channelUrl(slug: String): String = "$YATTA/apps/channels/$slug?platform=apps"

    suspend fun program(programPath: String): JsonObject = get(programUrl(programPath))

    /**
     * yatta caps each collection at 250 items by default; [PROGRAM_SIZE] returns the whole
     * replay in one call (measured 2026-11-06, journal-20h00: 249 → 443 integrales).
     */
    fun programUrl(programPath: String): String = "$YATTA/apps/program/$programPath?platform=apps&size=$PROGRAM_SIZE"

    fun programMetaUrl(programPath: String): String = "$YATTA/apps/program/meta/$programPath?platform=apps"

    suspend fun k7(siId: String): JsonObject = get(k7Url(siId))

    fun k7Url(siId: String): String =
        "$K7/videos/$siId?country_code=FR&capabilities=drm&os=androidtv&offline=false"

    /** Signs a manifest at the hdfauth base (k7 gives it in video.token). */
    suspend fun sign(manifestUrl: String, tokenBase: String): JsonObject =
        get("$tokenBase&url=${encode(manifestUrl)}")

    private suspend fun get(url: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                throw FtvHttpException("HTTP ${response.code} for $url")
            }
            FtvJson.parse(body)
        }
    }

    companion object {
        const val YATTA = "https://api-mobile.yatta.francetv.fr"
        const val PROGRAM_SIZE = 2000
        const val K7 = "https://k7.ftven.fr"
        const val HDFAUTH = "https://hdfauth.ftven.fr/esi/TA?format=json"
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        fun encode(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
