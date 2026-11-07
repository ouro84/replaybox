package com.replaybox.core.health

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Last night's CI check, read from the public `status` branch (SPEC
 * §Health-check). The body is kept in memory for the process lifetime and
 * refetched when older than [REFRESH_MS]; a failed fetch keeps the previous
 * body (the 48 h rule of [CiHealthParser] ages it out). Runs on
 * [Dispatchers.IO] with a 5 s call timeout so Paramètres never waits on it.
 */
class CiHealthSource(
    private val client: OkHttpClient = defaultClient(),
    private val url: String = URL,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    @Volatile private var body: String? = null
    @Volatile private var fetchedAt: Long = 0

    /** Parsed from what is already in memory, without network: for the first frame. */
    fun cached(): Map<String, CiHealth> = CiHealthParser.parse(body, clock())

    suspend fun health(): Map<String, CiHealth> {
        if (body == null || clock() - fetchedAt > REFRESH_MS) {
            fetch()?.let {
                body = it
                fetchedAt = clock()
            }
        }
        return cached()
    }

    private suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    companion object {
        const val URL = "https://raw.githubusercontent.com/ouro84/replaybox/status/healthcheck.json"
        const val REFRESH_MS = 10 * 60_000L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
