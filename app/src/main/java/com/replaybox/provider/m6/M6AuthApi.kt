package com.replaybox.provider.m6

import com.replaybox.core.account.AccountException
import com.replaybox.core.account.AccountFailure
import com.replaybox.provider.tf1.Tf1Http
import com.replaybox.provider.tf1.Tf1Parsers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Pure request builders and response parsers of the M6+ sign-in chain (SPEC §Provider M6+).
 *
 * SOURCED, not measured: ported from catchup-tv-and-more `resources/lib/channels/fr/6play.py`
 * @ fa49e5a0c4bfb5f501ac8d5486c5f6465cc16745 (`get_login_token`, `get_video_url`), with the
 * web app release measured on m6.fr (2026-11-07). The live spike on the box confirms or fixes
 * each step. Nothing here logs; [AccountException] messages carry step names and codes only.
 */
object M6AuthApi {

    /** Gigya `accounts.login` host of the source (`URL_COMPTE_LOGIN`). No webSdkBootstrap step there. */
    const val LOGIN_URL = "https://login-gigya.m6.fr/accounts.login"

    /** Public Gigya client id of the M6 web login (source `API_KEY`; not a secret). */
    const val GIGYA_API_KEY = "3_hH5KBv25qZTd_sURpixbQW6a4OsiIzIEF2Ei_2H7TXTGLJb_1Hr4THKZianCQhWK"

    /** The source asks Gigya for JSONP with this fixed callback, then strips the wrapper. */
    const val JSONP_CALLBACK = "jsonp_3bbusffr388pem4"
    const val LOGIN_REFERER = "https://www.6play.fr/connexion"

    const val JWT_URL = "https://front-auth.6cloud.fr/v2/platforms/m6group_web/getJwt"
    const val DRM_BASE = "https://drm.6cloud.fr/v1/customers/m6web/platforms/m6group_web"
    const val CUSTOMER = "m6web"

    /**
     * `X-Client-Release` of the m6.fr web app, measured 2026-11-07 (the source sends 5.103.3).
     * The one place to bump it.
     */
    const val CLIENT_RELEASE = "6.54.2"

    data class GigyaIdentity(val uid: String, val signature: String, val timestamp: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun loginRequest(email: String, password: String): Request = Request.Builder()
        .url(LOGIN_URL)
        .header("User-Agent", Tf1Http.BROWSER_UA)
        .header("referer", LOGIN_REFERER)
        .post(
            FormBody.Builder()
                .add("loginID", email)
                .add("password", password)
                .add("apiKey", GIGYA_API_KEY)
                .add("format", "jsonp")
                .add("callback", JSONP_CALLBACK)
                .build(),
        )
        .build()

    fun jwtRequest(identity: GigyaIdentity, deviceId: String): Request = Request.Builder()
        .url(JWT_URL)
        .header("User-Agent", Tf1Http.BROWSER_UA)
        .header("x-auth-gigya-uid", identity.uid)
        .header("x-auth-gigya-signature", identity.signature)
        .header("x-auth-gigya-signature-timestamp", identity.timestamp)
        .header("x-auth-device-id", deviceId)
        .header("x-customer-name", CUSTOMER)
        // The web app sends its release on every 6cloud call (theme bundle, 2026-11-07).
        .header("x-client-release", CLIENT_RELEASE)
        .get()
        .build()

    /**
     * Upfront token of [clipId] for the Chaîne's real [serviceCode] (m6replay, w9replay,
     * 6terreplay, gulli, 6play), as the web app builds `services/{service}/users/{UID}`. The
     * source hard-codes `m6replay` for every replay: if the spike shows the per-service path
     * refused, fall back to `m6replay` here.
     */
    fun upfrontRequest(serviceCode: String, uid: String, clipId: String, jwt: String): Request {
        val url = DRM_BASE.toHttpUrl().newBuilder()
            .addPathSegment("services").addPathSegment(serviceCode)
            .addPathSegment("users").addPathSegment(uid)
            .addPathSegment("videos").addPathSegment(clipId)
            .addPathSegment("upfront-token")
            .build()
        return Request.Builder()
            .url(url)
            .header("User-Agent", Tf1Http.BROWSER_UA)
            .header("Authorization", "Bearer $jwt")
            .header("x-customer-name", CUSTOMER)
            .header("x-client-release", CLIENT_RELEASE)
            .get()
            .build()
    }

    /** Gigya identity of an `accounts.login` answer, JSONP-wrapped (as requested) or plain. */
    fun parseLogin(body: String): GigyaIdentity {
        val root = parseObject(unwrapJsonp(body))
            ?: throw AccountException(AccountFailure.SERVICE, "M6 gigya login: unreadable answer")
        val errorCode = str(root, "errorCode")?.toIntOrNull()
        if (errorCode != null && errorCode != 0) {
            throw AccountException(Tf1Parsers.gigyaFailure(errorCode), "M6 gigya login refused ($errorCode)", errorCode.toString())
        }
        // The source reads the top level; Gigya's other shape nests it under userInfo.
        val user = root["userInfo"] as? JsonObject
        fun field(name: String) = str(root, name) ?: user?.let { str(it, name) }
        val uid = field("UID")
        val signature = field("UIDSignature")
        val timestamp = field("signatureTimestamp")
        if (uid.isNullOrEmpty() || signature.isNullOrEmpty() || timestamp.isNullOrEmpty()) {
            throw AccountException(AccountFailure.SERVICE, "M6 gigya login: no UID / signature in the answer")
        }
        return GigyaIdentity(uid, signature, timestamp)
    }

    /**
     * `{token}` of a getJwt or upfront-token answer. The recorded error shapes are
     * `{"error":{"message","status"}}` (getJwt) and `{"code","message"}` (upfront-token).
     */
    fun parseToken(body: String, step: String): String {
        val root = parseObject(body) ?: throw AccountException(AccountFailure.SERVICE, "M6 $step: unreadable answer")
        str(root, "token")?.takeIf { it.isNotEmpty() }?.let { return it }
        val code = (root["error"] as? JsonObject)?.let { str(it, "status") } ?: str(root, "code")
        throw AccountException(AccountFailure.SERVICE, "M6 $step: no token" + (code?.let { " ($it)" } ?: ""), code)
    }

    internal fun unwrapJsonp(body: String): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) return trimmed
        val open = trimmed.indexOf('(')
        val close = trimmed.lastIndexOf(')')
        return if (open in 0 until close) trimmed.substring(open + 1, close) else trimmed
    }

    private fun parseObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun str(obj: JsonObject, key: String): String? {
        val value = obj[key] as? JsonPrimitive ?: return null
        return if (value is JsonNull) null else value.content
    }
}
