package com.replaybox.healthcheck

import com.replaybox.provider.m6.M6Channels
import com.replaybox.provider.m6.M6Provider

/**
 * M6+ without an account: the M6 Index crawl (Chaîne `m6`), then the first listed Programmes
 * (of [MAX_SHOWS]) until one goes `seasons` → `episodes` → `manifest`. Programmes and
 * Épisodes are picked dynamically; no id is hard-coded.
 *
 * Every M6 Flux is Widevine, so `stream` needs an account (step 4b) and is not probed: the
 * anonymous chain ends at [M6Provider.manifestUrl]. The `manifest` step stops at the signed
 * URL, like France TV's `signature` step: the URL's `countries` parameter restricts it to
 * France and its overseas territories (AD|FR|GP|…|WF, measured 2026-11-07), so the GET would
 * be refused from a GitHub runner (US). Measured from France on 2026-11-07 (local probe run):
 * the GET answers 302 to *.cdn.bedrock.tech, then 200 with a 1-Period DASH MPD.
 */
class M6Probe : ProviderProbe() {
    override val name = "m6"
    override val label = "M6+ (anonyme)"

    override suspend fun run(steps: Steps): String {
        val provider = M6Provider(channel = M6Channels.GROUP.first { it.id == "m6" })
        val shows = steps.step("shows") {
            provider.shows(null).ifEmpty { error("empty M6 listing") }
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
                val url = steps.step("manifest") {
                    provider.manifestUrl(episode.id).also { Manifests.checkM6Signature(it) }
                }
                return "${show.title} — ${episode.title} → ${Manifests.host(url)}"
            } catch (failure: StepFailed) {
                println("[m6] ${show.title}: ${failure.step} — ${failure.message}")
                lastFailure = failure
                if (failure.message.orEmpty().startsWith("simulated")) throw failure
            }
        }
        val last = lastFailure ?: StepFailed("shows", "no Programme to try")
        throw StepFailed(
            last.step,
            "no Épisode with a manifest in the first $MAX_SHOWS Programmes; last failure: ${last.message}",
            last,
        )
    }

    private companion object {
        const val MAX_SHOWS = 5
    }
}
