package com.replaybox.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub Releases over the public REST API, unauthenticated (public repo,
 * 60 requests/h/IP — one per app launch). No token ever lives on the box.
 */
class GitHubUpdateSource(
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient(),
) : UpdateSource {

    override suspend fun latestRelease(): JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null // no release published yet
                !response.isSuccessful -> throw IOException("HTTP ${response.code} for $LATEST_RELEASE")
                else -> Json.parseToJsonElement(response.body!!.string()).jsonObject
            }
        }
    }

    override suspend fun download(url: String, target: File) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                target.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
            }
        }
    }

    companion object {
        const val LATEST_RELEASE = "https://api.github.com/repos/ouro84/replaybox/releases/latest"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
