package com.replaybox.app.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallbackException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.replaybox.provider.tf1.Tf1Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import java.util.UUID

/** Licence answers that are not the raw Widevine bytes (`Stream.licenseResponseField`). */
object DrmLicense {

    /**
     * [body] as the CDM wants it: unchanged when [field] is null, else the base64 value of
     * that JSON field (drmtoday's `{"license": "…"}`, SPEC §Provider M6+). The error never
     * quotes the body.
     */
    fun unwrap(body: ByteArray, field: String?): ByteArray {
        if (field == null) return body
        val root = runCatching { Json.parseToJsonElement(body.decodeToString()) as? JsonObject }.getOrNull()
            ?: throw IllegalStateException("licence answer is not a JSON object (expected field '$field')")
        val value = root[field] as? JsonPrimitive
        if (value == null || value is JsonNull || !value.isString) {
            throw IllegalStateException("licence answer has no '$field' field (keys: ${root.keys.joinToString()})")
        }
        return runCatching { Base64.getDecoder().decode(value.content) }.getOrElse {
            throw IllegalStateException("licence field '$field' is not base64")
        }
    }
}

/**
 * Wraps Media3's licence callback (HttpMediaDrmCallback) to unwrap JSON licence answers, and
 * logs each licence exchange by status only (tag `ReplayboxDrm`) for the M6+ spike.
 */
@OptIn(UnstableApi::class)
class UnwrappingDrmCallback(
    private val delegate: MediaDrmCallback,
    private val field: String?,
    private val log: (String) -> Unit = { message -> android.util.Log.i(TAG, message) },
) : MediaDrmCallback {

    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray =
        delegate.executeProvisionRequest(uuid, request)

    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
        val body = try {
            delegate.executeKeyRequest(uuid, request)
        } catch (error: MediaDrmCallbackException) {
            val http = error.cause as? HttpDataSource.InvalidResponseCodeException
            if (http != null) {
                // drmtoday explains a refusal in x-dt-resp-code (no token, no body logged).
                val drmCode = http.headerFields.entries
                    .firstOrNull { it.key.equals("x-dt-resp-code", ignoreCase = true) }
                    ?.value?.firstOrNull()
                log("licence: HTTP ${http.responseCode}" + (drmCode?.let { " x-dt-resp-code=$it" } ?: ""))
            } else {
                log("licence: failed (${error.cause?.javaClass?.simpleName ?: "unknown"})")
            }
            throw error
        }
        log("licence: HTTP 200, ${body.size} bytes")
        return try {
            DrmLicense.unwrap(body, field).also { if (field != null) log("licence: unwrapped '$field' (${it.size} bytes)") }
        } catch (error: IllegalStateException) {
            log("licence: ${error.message}")
            throw error
        }
    }

    companion object {
        const val TAG = "ReplayboxDrm"
    }
}

/**
 * Media source factory of a Flux whose licence answer is JSON-wrapped (M6+ drmtoday): Widevine
 * through [UnwrappingDrmCallback] over Media3's HttpMediaDrmCallback, always on [licenseUrl]
 * with [headers] (`x-dt-auth-token`). The manifest URL 302-redirects to another host
 * (lbcdn.6cloud.fr to *.cdn.bedrock.tech, SPEC §Provider M6+): DefaultHttpDataSource follows
 * cross-host redirects itself; cross-protocol ones are allowed too. Browser UA, as the source.
 */
@OptIn(UnstableApi::class)
internal fun jsonLicenseMediaSourceFactory(
    context: Context,
    licenseUrl: String,
    headers: Map<String, String>,
    responseField: String,
): MediaSource.Factory {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Tf1Http.BROWSER_UA)
        .setAllowCrossProtocolRedirects(true)
    val licence = HttpMediaDrmCallback(licenseUrl, /* forceDefaultLicenseUrl= */ true, http)
    headers.forEach { (name, value) -> licence.setKeyRequestProperty(name, value) }
    val drm = DefaultDrmSessionManager.Builder()
        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
        .build(UnwrappingDrmCallback(licence, responseField))
    return DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
        .setDrmSessionManagerProvider { drm }
}
