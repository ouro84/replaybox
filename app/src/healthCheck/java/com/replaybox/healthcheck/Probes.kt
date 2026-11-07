package com.replaybox.healthcheck

/**
 * Nightly health-check (SPEC §CI, ADR 0002): one Épisode per Provider pushed
 * through the real Provider chain up to its Flux (signed manifest URL).
 *
 * This is the only real-network test of the project. Its sources are compiled
 * and run only with `-PhealthCheck` (see app/build.gradle.kts) — never as part
 * of the unit test suite (AGENTS.md: zero network in unit tests).
 *
 * Adding a Chaîne = one more [ProviderProbe] in [Probes.all] (and in KNOWN_PROVIDERS of
 * .github/scripts/healthcheck-status.js).
 */
object Probes {
    val all: List<ProviderProbe> = listOf(
        Tf1Probe(),
        FtvProbe(),
        M6Probe(),
    )
}

/** One Provider walked step by step; [name] matches the workflow `simulate_failure` input. */
abstract class ProviderProbe {
    abstract val name: String
    abstract val label: String

    /** Walks the chain; returns a one-line detail of what was resolved. */
    abstract suspend fun run(steps: Steps): String

    override fun toString(): String = name
}

/** A named step of the Provider chain failed; [step] is what the alert reports. */
class StepFailed(val step: String, message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs named steps (Provider method names: shows, seasons, episodes, stream…).
 * With [simulateFailure], the first step fails on purpose so the alerting path
 * (issue creation/update) can be exercised from workflow_dispatch.
 */
class Steps(private val probe: String, private val simulateFailure: Boolean) {

    suspend fun <T> step(name: String, block: suspend () -> T): T {
        if (simulateFailure) {
            throw StepFailed(name, "simulated failure (workflow_dispatch simulate_failure=$probe)")
        }
        return try {
            block()
        } catch (failure: StepFailed) {
            throw failure
        } catch (error: Throwable) {
            throw StepFailed(name, "${error::class.java.simpleName}: ${error.message}", error)
        }
    }
}
