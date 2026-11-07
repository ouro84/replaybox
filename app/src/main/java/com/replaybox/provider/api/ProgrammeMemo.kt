package com.replaybox.provider.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory memo of what a Provider fetched for one Programme (its videos, its program
 * detail): one crawl serves the Saisons and every Saison's Épisodes of a Programme page.
 * Entries live [lifetimeMs] (10 min by default) and no longer: the box process lives for
 * days, and a memo kept for the whole process handed the 12 h refresh of the catalog cache
 * (ADR 0002) the data it was meant to replace.
 */
class ProgrammeMemo<K, V>(
    private val lifetimeMs: Long = LIFETIME_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val entries = HashMap<K, Pair<Long, V>>()
    private val mutex = Mutex()

    suspend fun get(key: K): V? = mutex.withLock {
        val entry = entries[key] ?: return@withLock null
        if (clock() - entry.first < lifetimeMs) {
            entry.second
        } else {
            entries.remove(key)
            null
        }
    }

    suspend fun put(key: K, value: V) {
        mutex.withLock { entries[key] = clock() to value }
    }

    companion object {
        const val LIFETIME_MS: Long = 10 * 60 * 1000
    }
}
