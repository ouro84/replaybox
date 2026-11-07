package com.replaybox.core.health

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

/** healthcheck.json from the `status` branch (SPEC §Health-check), recorded 2026-11-07. */
class CiHealthParserTest {

    private val fixture: String =
        CiHealthParserTest::class.java.getResourceAsStream("/fixtures/healthcheck_status.json")!!
            .bufferedReader().use { it.readText() }

    private val generatedAt = Instant.parse("2026-11-07T10:51:33Z").toEpochMilli()
    private val hour = 3_600_000L

    /** The recorded run was all green: the KO side is the same file with France TV failing. */
    private val francetvKo = fixture.replace(
        Regex(
            "(\"provider\": \"francetv\",\\s*\"label\": \"[^\"]*\",\\s*)\"ok\": true,\\s*\"step\": null,\\s*\"error\": null",
        ),
        "$1\"ok\": false, \"step\": \"signature\", \"error\": \"exp is in the past\"",
    )

    @Test
    fun freshOkAndKo() {
        check(francetvKo != fixture) { "fixture rewrite did not apply" }
        val health = CiHealthParser.parse(francetvKo, now = generatedAt + 2 * hour)

        assertThat(health["tf1"]).isEqualTo(
            CiHealth(HealthState.OK, step = null, error = null, checkedAt = Instant.parse("2026-11-07T10:51:29Z").toEpochMilli()),
        )
        assertThat(health["francetv"]).isEqualTo(
            CiHealth(
                HealthState.KO,
                step = "signature",
                error = "exp is in the past",
                checkedAt = Instant.parse("2026-11-07T10:51:31Z").toEpochMilli(),
            ),
        )
    }

    @Test
    fun olderThan48hIsUnknown() {
        // Three days later, an `ok: true` must not read as a current OK.
        assertThat(CiHealthParser.parse(fixture, now = generatedAt + 72 * hour)).isEmpty()
        assertThat(CiHealthParser.parse(fixture, now = generatedAt + 48 * hour + 1)).isEmpty()
        assertThat(CiHealthParser.parse(fixture, now = generatedAt + 47 * hour)).isNotEmpty()
    }

    @Test
    fun unreadableIsUnknown() {
        val now = generatedAt + hour
        assertThat(CiHealthParser.parse(null, now)).isEmpty()
        assertThat(CiHealthParser.parse("{", now)).isEmpty()
        assertThat(CiHealthParser.parse("""{"generatedAt": "2026-11-07T10:51:33Z"}""", now)).isEmpty()
        assertThat(CiHealthParser.parse("""{"providers": []}""", now)).isEmpty()
        assertThat(CiHealthParser.parse("<html>404</html>", now)).isEmpty()
    }

    @Test
    fun unknownProviderIgnored() {
        val json = """
            {"generatedAt": "2026-11-07T10:51:33Z", "runUrl": "x", "providers": [
              {"provider": "zz", "label": "?", "ok": false, "step": "build", "error": "e", "checkedAt": "2026-11-07T10:51:33Z"},
              {"label": "no provider name", "ok": false},
              {"provider": "francetv", "label": "no ok field"},
              {"provider": "tf1", "label": "TF1+", "ok": true, "step": null, "error": null, "checkedAt": "bad date"}
            ]}
        """.trimIndent()

        val health = CiHealthParser.parse(json, now = generatedAt + hour)

        assertThat(health["tf1"]).isEqualTo(CiHealth(HealthState.OK, null, null, checkedAt = null))
        assertThat(health).doesNotContainKey("francetv")
        assertThat(health.keys).containsExactly("tf1", "zz")
    }
}
