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

    /**
     * Display text: like [str], minus the backslash France TV leaves before
     * quotes in some descriptions (double-escaped upstream, seen 2026-11-06),
     * and minus the HTML some magazines send (« <p>Faustine accueille… »,
     * Ça commence aujourd'hui, seen on the box 2026-11-07).
     */
    fun text(element: JsonElement?, vararg path: String): String? =
        str(element, *path)?.replace("\\\"", "\"")?.let(::plainText)

    /** Tags dropped (a paragraph or line break becomes a space), common entities decoded. */
    fun plainText(html: String): String {
        if ('<' !in html && '&' !in html) return html
        return html
            .replace(BREAK, " ")
            .replace(TAG, "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rsquo;", "’")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(SPACES, " ")
            .trim()
    }

    private val BREAK = Regex("""(?i)<br\s*/?>|</p>\s*<p[^>]*>""")
    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("""\s{2,}""")

    fun str(element: JsonElement?, vararg path: String): String? {
        var current: JsonElement? = element
        for (key in path) {
            current = (current as? JsonObject)?.get(key)
            if (current == null) return null
        }
        return (current as? JsonPrimitive)?.content
    }

    fun int(element: JsonElement?, vararg path: String): Int? = str(element, *path)?.toIntOrNull()

    fun long(element: JsonElement?, vararg path: String): Long? = str(element, *path)?.toLongOrNull()

    /** A JSON string value; null for a missing key and for JSON `null` (which [str] reads as "null"). */
    fun string(element: JsonElement?, vararg path: String): String? {
        var current: JsonElement? = element
        for (key in path) {
            current = (current as? JsonObject)?.get(key)
            if (current == null) return null
        }
        return (current as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    fun bool(element: JsonElement?, vararg path: String): Boolean? =
        (element?.let { str(it, *path) })?.toBooleanStrictOrNull()

    fun objects(array: JsonElement?): List<JsonObject> =
        (array as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
