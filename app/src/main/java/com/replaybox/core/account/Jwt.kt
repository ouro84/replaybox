package com.replaybox.core.account

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/** Reads the claims a Provider account needs from a JWT, without verifying it (the server does). */
object Jwt {

    /** `exp` claim of the JWT payload, in epoch ms; null when unparsable. */
    fun expiry(token: String): Long? = runCatching {
        val payload = token.split('.')[1]
        val decoded = Base64.getUrlDecoder().decode(payload.trimEnd('='))
        val claims = Json.parseToJsonElement(decoded.decodeToString()) as? JsonObject ?: return@runCatching null
        (claims["exp"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.let { (it * 1000).toLong() }
    }.getOrNull()
}
