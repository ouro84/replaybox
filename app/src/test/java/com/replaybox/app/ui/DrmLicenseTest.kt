package com.replaybox.app.ui

import androidx.media3.common.C
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

/**
 * drmtoday answers the Widevine licence wrapped in JSON (`{"license": "<base64>"}`), per the
 * catchup-tv-and-more source (`|JBlicense`). `m6_drmtoday_license_shape.json` is a SHAPE from
 * that source, NOT a recorded response; the spike PR replaces it.
 */
class DrmLicenseTest {

    private fun fixture(name: String): ByteArray =
        DrmLicenseTest::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes()

    private val licence = byteArrayOf(8, 2, 18, 4, 0x6c, 0x69, 0x63, 0x21)

    @Test
    fun rawBodyPassesThroughWithoutField() {
        assertThat(DrmLicense.unwrap(licence, null)).isEqualTo(licence)
    }

    @Test
    fun jsonFieldIsBase64Decoded() {
        assertThat(DrmLicense.unwrap(fixture("m6_drmtoday_license_shape.json"), "license")).isEqualTo(licence)
    }

    @Test
    fun missingFieldIsAClearError() {
        val error = assertThrows(IllegalStateException::class.java) {
            DrmLicense.unwrap("""{"status":"ERROR"}""".toByteArray(), "license")
        }
        assertThat(error.message).contains("license")
    }

    @Test
    fun nonJsonBodyIsAClearError() {
        assertThrows(IllegalStateException::class.java) { DrmLicense.unwrap(licence, "license") }
    }

    private class FakeCallback(private val body: ByteArray) : MediaDrmCallback {
        var keyRequests = 0
        override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray = byteArrayOf(1)
        override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
            keyRequests++
            return body
        }
    }

    @Test
    fun callbackUnwrapsKeyResponses() {
        val delegate = FakeCallback(fixture("m6_drmtoday_license_shape.json"))
        val callback = UnwrappingDrmCallback(delegate, "license", log = {})
        val answer = callback.executeKeyRequest(C.WIDEVINE_UUID, ExoMediaDrm.KeyRequest(byteArrayOf(9), "https://lic.example/"))
        assertThat(answer).isEqualTo(licence)
        assertThat(delegate.keyRequests).isEqualTo(1)
    }

    @Test
    fun callbackLeavesProvisioningAlone() {
        val callback = UnwrappingDrmCallback(FakeCallback(byteArrayOf()), "license", log = {})
        val answer = callback.executeProvisionRequest(C.WIDEVINE_UUID, ExoMediaDrm.ProvisionRequest(byteArrayOf(), "https://prov.example/"))
        assertThat(answer).isEqualTo(byteArrayOf(1))
    }
}
