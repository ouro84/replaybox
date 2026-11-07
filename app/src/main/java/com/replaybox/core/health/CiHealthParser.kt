package com.replaybox.core.health

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/**
 * Reads `healthcheck.json` (SPEC §Health-check). An unreadable file, a file
 * without `generatedAt` or `providers`, or one generated more than 48 h before
 * [now] yields an empty map: CI state unknown for every Provider, never a
 * stale OK. Entries without a `provider` name or an `ok` flag are skipped.
 */
object CiHealthParser {

    const val MAX_AGE_MS = 48 * 3_600_000L

    fun parse(json: String?, now: Long): Map<String, CiHealth> {
        val root = json?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            ?: return emptyMap()
        val generatedAt = instant(root["generatedAt"]) ?: return emptyMap()
        if (now - generatedAt > MAX_AGE_MS) return emptyMap()
        val providers = root["providers"] as? JsonArray ?: return emptyMap()
        val result = LinkedHashMap<String, CiHealth>()
        for (element in providers) {
            val entry = element as? JsonObject ?: continue
            val name = string(entry["provider"]) ?: continue
            val ok = (entry["ok"] as? JsonPrimitive)?.booleanOrNull ?: continue
            result[name] = CiHealth(
                state = if (ok) HealthState.OK else HealthState.KO,
                step = string(entry["step"]),
                error = string(entry["error"]),
                checkedAt = instant(entry["checkedAt"]),
            )
        }
        return result
    }

    private fun string(element: Any?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun instant(element: Any?): Long? =
        string(element)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}
