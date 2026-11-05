package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtvK7ParserTest {

    @Test
    fun `parses an anonymous dash flux with side-loaded subtitles`() {
        val flux = FtvParsers.streamFromK7(FtvTestFixtures.load("ftv_k7_capitaine_marleau_s1e7.json"))
        assertThat(flux.manifestUrl)
            .isEqualTo("https://cloudingest.ftven.fr/ftv/a/af/aafa17b95be96/c931bb6f-3cc9-4762-bfa9-9f85f832fde1-1777033206_france-domtom_TA.ism/manifest.mpd")
        assertThat(flux.type).isEqualTo(com.replaybox.core.model.StreamType.DASH)
        assertThat(flux.subtitleUrl)
            .isEqualTo("https://assets.webservices.francetelevisions.fr/v1/assets/captions/87/b0/6c/2733bd70-bbd1-48a4-9e56-8d13fab58ff6.vtt")
        assertThat(flux.subtitleLanguage).isEqualTo("qsm")
        assertThat(flux.signBase).isEqualTo("https://hdfauth.ftven.fr/esi/TA?format=json")
    }

    @Test
    fun `parses the same flux shape for a login-gated episode`() {
        // Simon Coleman S4E1 is login:true on yatta, yet k7 answers anonymously.
        val flux = FtvParsers.streamFromK7(FtvTestFixtures.load("ftv_k7_simon_coleman_s4e1.json"))
        assertThat(flux.manifestUrl).startsWith("https://cloudreplay.ftven.fr/")
        assertThat(flux.subtitleUrl).endsWith(".vtt")
    }

    @Test
    fun `parses the flux of an unnumbered magazine episode`() {
        val flux = FtvParsers.streamFromK7(FtvTestFixtures.load("ftv_k7_ca_commence.json"))
        assertThat(flux.manifestUrl).endsWith("manifest.mpd")
        assertThat(flux.type).isEqualTo(com.replaybox.core.model.StreamType.DASH)
        assertThat(flux.subtitleUrl).isNotNull()
    }

    @Test
    fun `extracts the signed url from hdfauth`() {
        val signed = FtvParsers.signedUrl(FtvTestFixtures.load("ftv_hdfauth_capitaine_marleau_s1e7.json"))
        assertThat(signed).startsWith("https://cloudingest.ftven.fr/")
        assertThat(signed).contains("hdnea=exp=")
        assertThat(signed).contains("manifest.mpd?hdnea=")
    }
}
