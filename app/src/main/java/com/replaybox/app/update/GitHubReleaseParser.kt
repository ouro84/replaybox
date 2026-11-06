package com.replaybox.app.update

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** A published release the box can update to: its version and the release APK asset. */
data class AppRelease(val version: AppVersion, val apkUrl: String, val apkSize: Long)

/**
 * Parses GET /repos/ouro84/replaybox/releases/latest (GitHub REST, fixture
 * github_release_latest.json). The APK to install is the asset named
 * `*-release.apk` (scripts/release.ps1 attaches it next to the debug one).
 */
object GitHubReleaseParser {

    private const val RELEASE_APK_SUFFIX = "-release.apk"

    fun parse(root: JsonObject): AppRelease? {
        if (root.bool("draft") == true || root.bool("prerelease") == true) return null
        val version = root.str("tag_name")?.let(AppVersion::parse) ?: return null
        val asset = (root["assets"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { asset ->
                asset.str("name")?.endsWith(RELEASE_APK_SUFFIX) == true && asset.str("state") == "uploaded"
            } ?: return null
        val url = asset.str("browser_download_url") ?: return null
        val size = (asset["size"] as? JsonPrimitive)?.longOrNull ?: return null
        return AppRelease(version, url, size)
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}
