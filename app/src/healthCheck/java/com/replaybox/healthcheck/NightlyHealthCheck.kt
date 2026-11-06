package com.replaybox.healthcheck

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * One JUnit case per [ProviderProbe]. Each writes
 * `build/healthcheck/<probe>.json` {provider, label, ok, step, error, detail}
 * that the workflow turns into the « health-check KO » issue.
 *
 * Run: ./gradlew testDebugUnitTest -PhealthCheck
 * Simulate a KO: HEALTHCHECK_SIMULATE_FAILURE=tf1|francetv.
 */
@RunWith(Parameterized::class)
class NightlyHealthCheck(private val probe: ProviderProbe) {

    @Test
    fun `episode resolves up to its Flux`() {
        val simulate = System.getenv("HEALTHCHECK_SIMULATE_FAILURE").orEmpty().trim() == probe.name
        val result = runBlocking {
            try {
                val detail = probe.run(Steps(probe.name, simulate))
                println("[${probe.name}] OK — $detail")
                Result(ok = true, step = null, error = null, detail = detail)
            } catch (failure: StepFailed) {
                println("[${probe.name}] KO at ${failure.step} — ${failure.message}")
                Result(ok = false, step = failure.step, error = failure.message, detail = null)
            }
        }
        write(result)
        if (!result.ok) fail("${probe.label}: KO at step `${result.step}` — ${result.error}")
    }

    private data class Result(val ok: Boolean, val step: String?, val error: String?, val detail: String?)

    private fun write(result: Result) {
        val dir = File(System.getProperty("healthcheck.out") ?: "build/healthcheck").apply { mkdirs() }
        val json = buildJsonObject {
            put("provider", probe.name)
            put("label", probe.label)
            put("ok", result.ok)
            put("step", result.step)
            put("error", result.error)
            put("detail", result.detail)
        }
        File(dir, "${probe.name}.json").writeText(json.toString())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun probes(): List<ProviderProbe> = Probes.all
    }
}
