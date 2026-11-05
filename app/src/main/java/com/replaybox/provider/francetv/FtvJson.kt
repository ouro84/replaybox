package com.replaybox.provider.francetv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal object FtvJson {

    val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    fun obj(element: JsonElement?, vararg path: String): JsonObject? {
        var current: JsonElement? = element
        for (key in path) {
            current = (current as? JsonObject)?.get(key)
            if (current == null) return null
        }
        return current as? JsonObject
    }

    fun array(element: JsonElement?, vararg path: String): JsonArray? {
        var current: JsonElement? = element
        for (key in path) {
            current = (current as? JsonObject)?.get(key)
            if (current == null) return null
        }
        return current as? JsonArray
    }

    fun str(element: JsonElement?, vararg path: String): String? {
        var current: JsonElement? = element
        for (key in path) {
            current = (current as? JsonObject)?.get(key)
            if (current == null) return null
        }
        return (current as? JsonPrimitive)?.content
    }

    fun int(element: JsonElement?, vararg path: String): Int? = str(element, *path)?.toIntOrNull()

    fun bool(element: JsonElement?, vararg path: String): Boolean? =
        (element?.let { str(it, *path) })?.toBooleanStrictOrNull()

    fun objects(array: JsonElement?): List<JsonObject> =
        (array as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
