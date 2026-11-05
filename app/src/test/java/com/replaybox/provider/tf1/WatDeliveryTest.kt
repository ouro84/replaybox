package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatDeliveryTest {

    @Test
    fun `keeps numeric media ids longer than six digits`() {
        assertThat(WatDelivery.normalizeMediaId("14564313")).isEqualTo("14564313")
        assertThat(WatDelivery.normalizeMediaId("14010600")).isEqualTo("14010600")
    }

    @Test
    fun `decodes legacy base36 wat ids`() {
        // yt-dlp test vector: wat:14010600 / soupe-de-figues video id "6z1uz" -> 11713067
        assertThat(WatDelivery.normalizeMediaId("6z1uz")).isEqualTo("11713067")
    }

    @Test
    fun `returns raw id when not base36-decodable`() {
        assertThat(WatDelivery.normalizeMediaId("abc-12")).isEqualTo("abc-12")
    }
}
