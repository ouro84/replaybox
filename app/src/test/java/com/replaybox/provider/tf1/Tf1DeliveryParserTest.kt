package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.StreamType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertThrows
import org.junit.Test

class Tf1DeliveryParserTest {

    @Test
    fun `parses a delivered dash manifest`() {
        val delivery = Tf1Parsers.delivery(TestFixtures.load("tf1_mediainfo_ok.json"))
        val ok = delivery as Tf1Parsers.Delivery.Ok
        assertThat(ok.manifestUrl).startsWith("https://vod-das.cdn-0.diff.tf1.fr/")
        assertThat(ok.type).isEqualTo(StreamType.DASH)
    }

    @Test
    fun `maps syndication error to account or drm failure`() {
        val delivery = Tf1Parsers.delivery(TestFixtures.load("tf1_mediainfo_syndication_error.json"))
        val failure = delivery as Tf1Parsers.Delivery.Failure
        assertThat(failure.httpDeliveryCode).isEqualTo(403)
        assertThat(failure.errorCode).isEqualTo("DELIVERY_ERROR")
        assertThat(failure.description).isEqualTo("Contenu non disponible sur le site")
        assertThat(failure.accountOrDrm).isTrue()
        assertThat(failure.geoBlocked).isFalse()
    }

    @Test
    fun `maps permission error to failure`() {
        val delivery = Tf1Parsers.delivery(TestFixtures.load("tf1_mediainfo_permission_denied.json"))
        val failure = delivery as Tf1Parsers.Delivery.Failure
        assertThat(failure.httpDeliveryCode).isEqualTo(403)
        assertThat(failure.description).isEqualTo("Permission insuffisante")
        assertThat(failure.accountOrDrm).isTrue()
    }

    @Test
    fun `throws StreamResolutionException on failure`() {
        val delivery = Tf1Parsers.delivery(TestFixtures.load("tf1_mediainfo_syndication_error.json"))
        val exception = assertThrows(StreamResolutionException::class.java) {
            Tf1Parsers.streamFrom(delivery)
        }
        assertThat(exception.accountOrDrmRequired).isTrue()
    }

    @Test
    fun `rewrites das-q1 to its ssl host`() {
        val root = buildJsonObject {
            put(
                "delivery",
                buildJsonObject {
                    put("format", "dash")
                    put("url", "https://das-q1.tf1.fr/path/manifest.mpd")
                },
            )
        }
        val delivery = Tf1Parsers.delivery(root) as Tf1Parsers.Delivery.Ok
        assertThat(delivery.manifestUrl).isEqualTo("https://das-q1-ssl.tf1.fr/path/manifest.mpd")
    }

    @Test
    fun `detects hls format`() {
        val root = buildJsonObject {
            put(
                "delivery",
                buildJsonObject {
                    put("format", "hls")
                    put("url", "https://example.invalid/path.m3u8")
                },
            )
        }
        val delivery = Tf1Parsers.delivery(root) as Tf1Parsers.Delivery.Ok
        assertThat(delivery.type).isEqualTo(StreamType.HLS)
    }
}
