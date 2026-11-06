package com.replaybox.healthcheck

import com.replaybox.core.model.Channel
import com.replaybox.provider.francetv.FtvProvider

/**
 * France TV: France TV Épisodes expire, so the Épisode is chosen dynamically —
 * the france-2 listing (yatta), then the first Programme (of [MAX_SHOWS]) whose
 * first Épisode resolves through k7 + hdfauth. Never a hard-coded si_id.
 *
 * Stops at the signed manifest URL: measured 2026-11-06, the cloudreplay CDN
 * answers 403 to non-French IPs (GitHub runners are in the US) while yatta, k7
 * and hdfauth answer 200. The signature itself is checked instead (step
 * `signature`), because FtvProvider falls back to the unsigned URL when
 * hdfauth returns no url.
 */
class FtvProbe : ProviderProbe() {
    override val name = "francetv"
    override val label = "France TV — france-2 (anonyme)"

    override suspend fun run(steps: Steps): String {
        val provider = FtvProvider("france-2", Channel("france-2", "France 2"))
        val shows = steps.step("shows") {
            provider.shows(null).ifEmpty { error("empty france-2 listing") }
        }
        var lastFailure: StepFailed? = null
        for (show in shows.take(MAX_SHOWS)) {
            try {
                val season = steps.step("seasons") {
                    provider.seasons(show.id).firstOrNull() ?: error("no Saison for ${show.title}")
                }
                val episode = steps.step("episodes") {
                    provider.episodes(season.id).firstOrNull() ?: error("no Épisode for ${show.title}")
                }
                val stream = steps.step("stream") { provider.stream(episode.id) }
                steps.step("signature") { Manifests.checkFtvSignature(stream.manifestUrl) }
                return "${show.title} — ${episode.title} → ${Manifests.host(stream.manifestUrl)}"
            } catch (failure: StepFailed) {
                println("[francetv] ${show.title}: ${failure.step} — ${failure.message}")
                lastFailure = failure
                if (failure.message.orEmpty().startsWith("simulated")) throw failure
            }
        }
        val last = lastFailure ?: StepFailed("shows", "no Programme to try")
        throw StepFailed(
            last.step,
            "no playable Épisode in the first $MAX_SHOWS Programmes; last failure: ${last.message}",
            last,
        )
    }

    private companion object {
        const val MAX_SHOWS = 10
    }
}
