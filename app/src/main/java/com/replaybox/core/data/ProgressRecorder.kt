package com.replaybox.core.data

import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ProgressContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Writes the playhead on an application-lifetime [scope], never the
 * player screen's: leaving an Épisode cancels the screen at once, and a
 * Room write still in flight there was lost (Back right after a seek
 * resumed up to 5 s earlier). Callers pass a snapshot taken before the
 * player is released.
 */
class ProgressRecorder(private val catalog: CatalogStore, private val scope: CoroutineScope) {

    fun record(
        episodeId: EpisodeId,
        positionSeconds: Long,
        durationSeconds: Int?,
        context: ProgressContext?,
    ): Job = scope.launch { catalog.saveProgress(episodeId, positionSeconds, durationSeconds, context) }
}
