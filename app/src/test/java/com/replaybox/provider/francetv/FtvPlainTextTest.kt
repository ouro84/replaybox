package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtvPlainTextTest {

    @Test
    fun `plain text is returned untouched`() {
        assertThat(FtvJson.plainText("Faustine reçoit des invités.")).isEqualTo("Faustine reçoit des invités.")
    }

    @Test
    fun `paragraph tags and entities are removed`() {
        val html = "<p>Faustine accueille sur son plateau&nbsp;plusieurs invités.</p><p>L&#39;émission &amp; ses &quot;témoins&quot;.</p>"
        assertThat(FtvJson.plainText(html))
            .isEqualTo("Faustine accueille sur son plateau plusieurs invités. L'émission & ses \"témoins\".")
    }

    @Test
    fun `line breaks become spaces`() {
        assertThat(FtvJson.plainText("Première ligne<br/>seconde<BR>troisième")).isEqualTo("Première ligne seconde troisième")
    }
}
