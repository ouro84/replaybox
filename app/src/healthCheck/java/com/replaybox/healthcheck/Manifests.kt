package com.replaybox.healthcheck

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Checks on a resolved Flux URL (manifest). */
object Manifests {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    fun host(url: String): String = runCatching { URI(url).host }.getOrNull() ?: "?"

    /** GET the manifest: HTTP 200 and a DASH MPD body. */
    fun checkDash(url: String) {
        val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "manifest HTTP ${response.code} at ${host(url)}" }
            val head = response.body?.source()?.let { source ->
                source.request(64 * 1024)
                source.buffer.snapshot().utf8()
            }.orEmpty()
            check(head.contains("<MPD")) { "manifest at ${host(url)} is not a DASH MPD" }
        }
    }

    /**
     * hdfauth-signed URLs carry `exp=…~acl=…~hmac=…` base64-encoded as their first
     * path segment (fixture ftv_hdfauth_capitaine_marleau_s1e7.json); the k7
     * unsigned URL starts with `/ftv/…`. Checks the token is present and not expired.
     */
    fun checkFtvSignature(url: String, nowSeconds: Long = System.currentTimeMillis() / 1000) {
        val segment = URI(url).path.trimStart('/').substringBefore('/')
        val token = runCatching { String(Base64.getUrlDecoder().decode(segment)) }.getOrNull()
            ?: runCatching { String(Base64.getDecoder().decode(segment)) }.getOrNull()
        check(token != null && token.startsWith("exp=") && token.contains("hmac=")) {
            "Flux URL at ${host(url)} is not hdfauth-signed"
        }
        val expiry = token.substringAfter("exp=").substringBefore('~').toLongOrNull()
        check(expiry != null && expiry > nowSeconds) { "signed Flux URL already expired (exp=$expiry)" }
    }

    /**
     * M6 `full_physical_path` (fixture m6_video_clip.json): an https `.mpd` on the lbcdn
     * redirector, signed by `token=` and valid until `expiration=` (epoch seconds).
     */
    fun checkM6Signature(url: String, nowSeconds: Long = System.currentTimeMillis() / 1000) {
        val uri = URI(url)
        check(uri.scheme == "https" && uri.path.endsWith(".mpd")) { "M6 manifest URL at ${host(url)} is not an https MPD" }
        val params = uri.rawQuery.orEmpty().split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        check(!params["token"].isNullOrEmpty()) { "M6 manifest URL at ${host(url)} carries no token" }
        val expiry = params["expiration"]?.toLongOrNull()
        check(expiry != null && expiry > nowSeconds) { "M6 manifest URL already expired (expiration=$expiry)" }
    }
}
