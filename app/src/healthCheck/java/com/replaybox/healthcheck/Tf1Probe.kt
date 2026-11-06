package com.replaybox.healthcheck

import com.replaybox.core.model.ShowId
import com.replaybox.provider.tf1.Tf1Provider

/**
 * TF1+: anonymous chain on « Le 20h » (programSlug `jt-20h`, measured
 * 2026-11-06 — still playable without an account, SPEC §Provider TF1+).
 * The Épisode is picked dynamically (latest replay); no id is hard-coded.
 * Measured from a GitHub runner (US): the whole chain including the manifest
 * GET answers 200, so TF1 also checks the manifest body.
 */
class Tf1Probe : ProviderProbe() {
    override val name = "tf1"
    override val label = "TF1+ — Le 20h (anonyme)"

    override suspend fun run(steps: Steps): String {
        val provider = Tf1Provider(tokenProvider = null, log = { println("[tf1] $it") })
        steps.step("shows") {
            check(provider.shows(null).isNotEmpty()) { "empty TF1 listing" }
        }
        val season = steps.step("seasons") {
            provider.seasons(ShowId(SHOW_SLUG)).firstOrNull() ?: error("no Saison for $SHOW_SLUG")
        }
        val episode = steps.step("episodes") {
            provider.episodes(season.id).firstOrNull() ?: error("no Épisode for $SHOW_SLUG")
        }
        val stream = steps.step("stream") { provider.stream(episode.id) }
        steps.step("manifest") { Manifests.checkDash(stream.manifestUrl) }
        return "${episode.title} → ${Manifests.host(stream.manifestUrl)}"
    }

    private companion object {
        const val SHOW_SLUG = "jt-20h"
    }
}
