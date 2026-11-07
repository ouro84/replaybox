package com.replaybox.core.health

/** Santé of a Provider (glossary): last night's CI check combined with what this box observed. */
enum class HealthState { OK, KO, UNKNOWN }

/** One Provider's line of `healthcheck.json` (SPEC §Health-check). [checkedAt] in epoch ms. */
data class CiHealth(
    val state: HealthState,
    val step: String?,
    val error: String?,
    val checkedAt: Long?,
)

/** One observed outcome on the box; [error] is a short text (class name + message, ≤ 120 chars). */
data class Outcome(val ok: Boolean, val at: Long, val error: String?)

/** What this box saw of a Provider: last catalogue refresh and last Flux resolution. */
data class LocalHealth(val catalogue: Outcome?, val stream: Outcome?)

object Health {

    /** KO if either side is KO; else OK if either side is OK. An unknown side is missing evidence. */
    fun combine(ci: HealthState, local: HealthState): HealthState = when {
        ci == HealthState.KO || local == HealthState.KO -> HealthState.KO
        ci == HealthState.OK || local == HealthState.OK -> HealthState.OK
        else -> HealthState.UNKNOWN
    }

    /** KO if the latest catalogue or Flux outcome failed, OK if any outcome exists, else UNKNOWN. */
    fun localState(local: LocalHealth): HealthState {
        val outcomes = listOfNotNull(local.catalogue, local.stream)
        return when {
            outcomes.isEmpty() -> HealthState.UNKNOWN
            outcomes.any { !it.ok } -> HealthState.KO
            else -> HealthState.OK
        }
    }

    fun ciState(ci: CiHealth?): HealthState = ci?.state ?: HealthState.UNKNOWN
}
