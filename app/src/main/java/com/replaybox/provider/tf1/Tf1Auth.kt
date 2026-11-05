package com.replaybox.provider.tf1

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * TF1 sign-in over Gigya (compte.tf1.fr), then token exchange on tf1.fr.
 * Flow measured against cloudstream TF1Auth / Catch-up TV & More tf1plus
 * (same public flow as the TF1 web login page). Credentials never leave the
 * device: they live in the encrypted store (Android Keystore-backed).
 */
class Tf1Auth(
    private val store: Tf1CredentialStore,
    private val client: OkHttpClient = defaultClient(),
    private val now: () -> Long = System::currentTimeMillis,
) {

    @Volatile private var cachedToken: String? = null
    @Volatile private var expiresAt: Long = 0
    private val mutex = Mutex()

    fun hasCredentials(): Boolean = store.read() != null

    fun storedEmail(): String? = store.read()?.first

    suspend fun signIn(email: String, password: String) {
        store.save(email, password)
        token(forceRefresh = true)
    }

    fun signOut() {
        store.clear()
        cachedToken = null
        expiresAt = 0
    }

    /**
     * Returns a valid Bearer token or null when no account is configured.
     * Re-signs in silently with the stored credentials when the token expired.
     */
    suspend fun token(forceRefresh: Boolean = false): String? {
        val credentials = store.read() ?: return null
        mutex.withLock {
            cachedToken?.let { token ->
                if (!forceRefresh && now() + 60_000 < expiresAt) return token
            }
            val fresh = authenticate(credentials.first, credentials.second)
            cachedToken = fresh.token
            expiresAt = fresh.expiresAt
            return fresh.token
        }
    }

    private suspend fun authenticate(email: String, password: String): TokenAndExpiry = withContext(Dispatchers.IO) {
        val bootstrap = postGet(
            "${Companion.BOOTSTRAP}?apiKey=$GIGYA_KEY&pageURL=${Tf1Http.encode(PAGE_URL)}&sdk=js_latest&sdkBuild=13987&format=json",
        )
        if (Tf1Json.int(bootstrap, "errorCode") != 0) throw Tf1AuthException("TF1 bootstrap failed")

        val login = postForm(
            Companion.LOGIN,
            mapOf(
                "loginID" to email,
                "password" to password,
                "sessionExpiration" to "31536000",
                "targetEnv" to "jssdk",
                "include" to "identities-all,data,profile,preferences",
                "includeUserInfo" to "true",
                "loginMode" to "standard",
                "lang" to "fr",
                "APIKey" to GIGYA_KEY,
                "sdk" to "js_latest",
                "authMode" to "cookie",
                "pageURL" to PAGE_URL,
                "sdkBuild" to "13987",
                "format" to "json",
            ),
        )
        val identity = Tf1Parsers.gigyaIdentity(login)

        val exchange = postJson(
            TOKEN_EXCHANGE,
            buildJsonObject {
                put("UID", identity.uid)
                put("signature", identity.signature)
                put("timestamp", identity.timestamp)
                putJsonArray("consent_ids") {
                    // Didomi purpose ids from the tf1.fr web bundle CONSENTS registry.
                    // Sending an empty list is the "all cookies refused" state: TF1
                    // then gates the Flux (same symptom as declining on the web,
                    // measured via Playwright + reported 2026-11-05). We mirror the
                    // "TOUT ACCEPTER" state of the account the user signs in with.
                    Companion.CONSENT_IDS.forEach { add(it) }
                }
            },
        )
        val token = Tf1Json.str(exchange, "token")
            ?: throw Tf1AuthException("TF1 did not return a playback token")
        TokenAndExpiry(token, jwtExpiry(token) ?: (now() + 15 * 60_000L))
    }

    data class TokenAndExpiry(val token: String, val expiresAt: Long)

    private fun postGet(url: String): JsonObject = client.newCall(
        Request.Builder().url(url).header("User-Agent", Tf1Http.BROWSER_UA).get().build(),
    ).execute().use { response ->
        val body = response.body?.string() ?: throw Tf1AuthException("Empty TF1 auth response")
        if (!response.isSuccessful) throw Tf1AuthException("TF1 auth HTTP ${response.code}")
        Tf1Json.parse(body)
    }

    private fun postForm(url: String, fields: Map<String, String>): JsonObject {
        val form = FormBody.Builder()
        fields.forEach { (k, v) -> form.add(k, v) }
        return client.newCall(
            Request.Builder().url(url).header("User-Agent", Tf1Http.BROWSER_UA).post(form.build()).build(),
        ).execute().use { response ->
            val payload = response.body?.string() ?: throw Tf1AuthException("Empty TF1 auth response")
            if (!response.isSuccessful) throw Tf1AuthException("TF1 auth HTTP ${response.code}")
            Tf1Json.parse(payload)
        }
    }

    private fun postJson(url: String, json: JsonObject): JsonObject {
        val media = "application/json".toMediaType()
        val body = okhttp3.RequestBody.create(media, json.toString())
        return client.newCall(
            Request.Builder().url(url).header("User-Agent", Tf1Http.BROWSER_UA)
                .header("Content-Type", "application/json").post(body).build(),
        ).execute().use { response ->
            val payload = response.body?.string() ?: throw Tf1AuthException("Empty TF1 auth response")
            if (!response.isSuccessful) throw Tf1AuthException("TF1 auth HTTP ${response.code}")
            Tf1Json.parse(payload)
        }
    }

    companion object {
        // Public client identifier from TF1's own login page (not a secret).
        const val GIGYA_KEY = "3_hWgJdARhz_7l1oOp3a8BDLoR9cuWZpUaKG4aqF7gum9_iK3uTZ2VlDBl8ANf8FVk"
        const val PAGE_URL = "https://www.tf1.fr/"
        const val BOOTSTRAP = "https://compte.tf1.fr/accounts.webSdkBootstrap"
        const val LOGIN = "https://compte.tf1.fr/accounts.login"
        const val TOKEN_EXCHANGE = "https://www.tf1.fr/token/gigya/web"

        /** Didomi purpose ids, "TOUT ACCEPTER" equivalent (tf1.fr web bundle, 2026-11-05). */
        val CONSENT_IDS = listOf(
            "technique-HWZ9HRWz",
            "publicite-cidJFbya",
            "personnali-yf2njgbr",
            "mesureda-mn4zfWU6",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Gigya blocks accounts.login with 400006 "Invalid parameter value"
            // unless the gmid/ucid cookies from webSdkBootstrap travel with it
            // (measured 2026-11-05, fixture tf1_login_missing_cookies.json).
            .cookieJar(MemoryCookieJar())
            .build()

        /** exp claim of the JWT payload, in epoch ms; null when unparsable. */
        fun jwtExpiry(token: String): Long? = runCatching {
            val payload = token.split('.')[1]
            val decoded = Base64.getUrlDecoder().decode(payload)
            val claims = Tf1Json.parse(decoded.decodeToString())
            (Tf1Json.str(claims, "exp") ?: return@runCatching null).toDoubleOrNull()
                ?.let { (it * 1000).toLong() }
        }.getOrNull()
    }
}

/** In-memory cookie jar so the bootstrap cookies reach accounts.login. */
private class MemoryCookieJar : CookieJar {
    private val cookies = LinkedHashMap<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { this.cookies[it.domain + "/" + it.name] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.values.filter { it.matches(url) }
}

class Tf1AuthException(message: String) : Exception(message)
