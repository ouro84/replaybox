package com.replaybox.app.update

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** Fixture: GET /repos/ouro84/replaybox/releases/latest recorded 2026-11-06 (v0.2.0). */
class GitHubReleaseParserTest {

    private fun fixture(): JsonObject {
        val stream = javaClass.getResourceAsStream("/fixtures/github_release_latest.json")
            ?: throw AssertionError("fixture not found")
        return Json.parseToJsonElement(stream.readBytes().decodeToString()).jsonObject
    }

    @Test
    fun `picks the release apk asset of the latest release`() {
        val release = GitHubReleaseParser.parse(fixture())!!
        assertThat(release.version).isEqualTo(AppVersion(0, 2, 0))
        assertThat(release.apkUrl)
            .isEqualTo("https://github.com/ouro84/replaybox/releases/download/v0.2.0/replaybox-0.2.0-release.apk")
        assertThat(release.apkSize).isEqualTo(9664598L)
    }

    @Test
    fun `ignores a release without a release apk`() {
        val json = Json.parseToJsonElement(
            """{"tag_name":"v0.9.0","draft":false,"prerelease":false,
               "assets":[{"name":"replaybox-0.9.0-debug.apk","state":"uploaded","size":1,
               "browser_download_url":"https://example.invalid/d.apk"}]}""",
        ).jsonObject
        assertThat(GitHubReleaseParser.parse(json)).isNull()
    }

    @Test
    fun `ignores drafts, prereleases and unparseable tags`() {
        val asset = """"assets":[{"name":"replaybox-0.9.0-release.apk","state":"uploaded","size":1,
               "browser_download_url":"https://example.invalid/r.apk"}]"""
        val draft = Json.parseToJsonElement("""{"tag_name":"v0.9.0","draft":true,"prerelease":false,$asset}""").jsonObject
        val pre = Json.parseToJsonElement("""{"tag_name":"v0.9.0","draft":false,"prerelease":true,$asset}""").jsonObject
        val badTag = Json.parseToJsonElement("""{"tag_name":"nightly","draft":false,"prerelease":false,$asset}""").jsonObject
        assertThat(GitHubReleaseParser.parse(draft)).isNull()
        assertThat(GitHubReleaseParser.parse(pre)).isNull()
        assertThat(GitHubReleaseParser.parse(badTag)).isNull()
    }

    @Test
    fun `ignores an asset still uploading`() {
        val json = Json.parseToJsonElement(
            """{"tag_name":"v0.9.0","draft":false,"prerelease":false,
               "assets":[{"name":"replaybox-0.9.0-release.apk","state":"starter","size":1,
               "browser_download_url":"https://example.invalid/r.apk"}]}""",
        ).jsonObject
        assertThat(GitHubReleaseParser.parse(json)).isNull()
    }
}
