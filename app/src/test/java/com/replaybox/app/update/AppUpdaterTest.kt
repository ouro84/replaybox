package com.replaybox.app.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AppUpdaterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeSource(
        var release: JsonObject?,
        val payload: ByteArray = ByteArray(APK_SIZE) { 7 },
        val failLatest: Boolean = false,
        val failDownload: Boolean = false,
    ) : UpdateSource {
        var latestCalls = 0
        val downloads = mutableListOf<String>()

        override suspend fun latestRelease(): JsonObject? {
            latestCalls++
            if (failLatest) throw IOException("offline")
            return release
        }

        override suspend fun download(url: String, target: File) {
            downloads += url
            if (failDownload) throw IOException("connection reset")
            target.writeBytes(payload)
        }
    }

    private fun release(tag: String, size: Int = APK_SIZE): JsonObject = Json.parseToJsonElement(
        """{"tag_name":"$tag","draft":false,"prerelease":false,"assets":[
           {"name":"replaybox-${tag.removePrefix("v")}-debug.apk","state":"uploaded","size":99,
            "browser_download_url":"https://example.invalid/debug.apk"},
           {"name":"replaybox-${tag.removePrefix("v")}-release.apk","state":"uploaded","size":$size,
            "browser_download_url":"https://example.invalid/$tag/release.apk"}]}""",
    ).jsonObject

    private fun updater(source: UpdateSource, current: String = "0.3.0"): AppUpdater =
        AppUpdater(AppVersion.parse(current)!!, source, temp.root)

    @Test
    fun `up to date - one request, no download, stays idle`() {
        runBlocking {
            val source = FakeSource(release("v0.3.0"))
            val updater = updater(source)
            updater.check()
            assertThat(source.latestCalls).isEqualTo(1)
            assertThat(source.downloads).isEmpty()
            assertThat(updater.state.value).isEqualTo(UpdateState.Idle)
        }
    }

    @Test
    fun `older release on github - no download`() {
        runBlocking {
            val source = FakeSource(release("v0.2.0"))
            val updater = updater(source)
            updater.check()
            assertThat(source.downloads).isEmpty()
            assertThat(updater.state.value).isEqualTo(UpdateState.Idle)
        }
    }

    @Test
    fun `newer release - downloads the release apk and becomes ready`() {
        runBlocking {
            val source = FakeSource(release("v0.3.1"))
            val updater = updater(source)
            updater.check()
            assertThat(source.downloads).containsExactly("https://example.invalid/v0.3.1/release.apk")
            val ready = updater.state.value as UpdateState.Ready
            assertThat(ready.version).isEqualTo(AppVersion(0, 3, 1))
            assertThat(ready.apk.length()).isEqualTo(APK_SIZE.toLong())
            assertThat(ready.apk.parentFile).isEqualTo(temp.root)
            assertThat(temp.root.list()!!.toList()).containsExactly(ready.apk.name)
        }
    }

    @Test
    fun `apk already downloaded for that version - no second download`() {
        runBlocking {
            val source = FakeSource(release("v0.3.1"))
            updater(source).check()
            val again = updater(source)
            again.check()
            assertThat(source.downloads).hasSize(1)
            assertThat(again.state.value).isInstanceOf(UpdateState.Ready::class.java)
        }
    }

    @Test
    fun `truncated download is discarded and stays idle`() {
        runBlocking {
            val source = FakeSource(release("v0.3.1", size = APK_SIZE + 10))
            val updater = updater(source)
            updater.check()
            assertThat(updater.state.value).isEqualTo(UpdateState.Idle)
            assertThat(temp.root.list()!!.toList()).isEmpty()
        }
    }

    @Test
    fun `network failures are silent`() {
        runBlocking {
            val offline = updater(FakeSource(release("v0.3.1"), failLatest = true))
            offline.check()
            assertThat(offline.state.value).isEqualTo(UpdateState.Idle)

            val broken = updater(FakeSource(release("v0.3.1"), failDownload = true))
            broken.check()
            assertThat(broken.state.value).isEqualTo(UpdateState.Idle)
            assertThat(temp.root.list()!!.toList()).isEmpty()
        }
    }

    @Test
    fun `no published release - idle`() {
        runBlocking {
            val updater = updater(FakeSource(null))
            updater.check()
            assertThat(updater.state.value).isEqualTo(UpdateState.Idle)
        }
    }

    @Test
    fun `once up to date, stale downloaded apks are deleted`() {
        runBlocking {
            File(temp.root, "replaybox-0.3.0.apk").writeBytes(ByteArray(3))
            File(temp.root, "replaybox-0.3.1.apk.part").writeBytes(ByteArray(3))
            val updater = updater(FakeSource(release("v0.3.0")))
            updater.check()
            assertThat(temp.root.list()!!.toList()).isEmpty()
        }
    }

    private companion object {
        const val APK_SIZE = 64
    }

    @Test
    fun `last check outcome tells up to date, ready and failed apart`() {
        runBlocking {
            val upToDate = updater(FakeSource(release("v0.3.0")))
            assertThat(upToDate.lastCheck.value).isNull()
            upToDate.check()
            assertThat(upToDate.lastCheck.value).isEqualTo(CheckOutcome.UP_TO_DATE)
            assertThat(upToDate.checking.value).isFalse()

            val none = updater(FakeSource(null))
            none.check()
            assertThat(none.lastCheck.value).isEqualTo(CheckOutcome.UP_TO_DATE)

            val ready = updater(FakeSource(release("v0.3.1")))
            ready.check()
            assertThat(ready.lastCheck.value).isEqualTo(CheckOutcome.READY)
        }
    }

    @Test
    fun `offline, broken or truncated downloads report a failed check`() {
        runBlocking {
            val offline = updater(FakeSource(release("v0.3.1"), failLatest = true))
            offline.check()
            assertThat(offline.lastCheck.value).isEqualTo(CheckOutcome.FAILED)

            val broken = updater(FakeSource(release("v0.3.1"), failDownload = true))
            broken.check()
            assertThat(broken.lastCheck.value).isEqualTo(CheckOutcome.FAILED)

            val truncated = updater(FakeSource(release("v0.3.1", size = APK_SIZE + 10)))
            truncated.check()
            assertThat(truncated.lastCheck.value).isEqualTo(CheckOutcome.FAILED)
            assertThat(truncated.state.value).isEqualTo(UpdateState.Idle)
        }
    }
}
