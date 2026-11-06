package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.EpisodeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Leaving the player cancels its screen scope at once: the last Progression
 * write must outlive it (it used to be cancelled mid-write).
 */
class ProgressRecorderTest {

    /** A DAO whose writes take time, like Room on its IO executor. */
    private class SlowDao : CatalogDao by FakeCatalogDao() {
        val saved = HashMap<String, ProgressEntity>()
        override suspend fun putProgress(progress: ProgressEntity) {
            delay(50)
            saved[progress.episodeId] = progress
        }
    }

    @Test
    fun `the last save survives the cancelled screen scope`() = runBlocking {
        val dao = SlowDao()
        val recorder = ProgressRecorder(CatalogStore(dao) { 1_000L }, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val screen = CoroutineScope(Job() + Dispatchers.Default)

        var write: Job? = null
        screen.launch { write = recorder.record(EpisodeId("e1"), 754, 3600, null) }.join()
        screen.cancel()
        write!!.join()

        assertThat(dao.saved["e1"]?.positionSeconds).isEqualTo(754)
    }
}
