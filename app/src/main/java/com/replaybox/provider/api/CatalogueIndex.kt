package com.replaybox.provider.api

import com.replaybox.core.model.Show
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One Programme of a Provider family's catalogue, with the Chaîne it belongs to. */
data class IndexEntry(val channelId: String, val show: Show)

/** One page of a catalogue crawl; [hasMore] tells the crawler to fetch the next page index. */
data class CataloguePage(val entries: List<IndexEntry>, val hasMore: Boolean)

/**
 * Index (glossary): the full local copy of a Provider family's Programmes.
 *
 * Crawls the whole catalogue page by page through [fetchPage], once for every Chaîne of the
 * family, and hands each Chaîne its slice. Storage and the 12 h TTL stay in CatalogStore
 * (ADR 0002); this class only crawls:
 * - a page is retried [maxAttempts] times, then the whole crawl fails (nothing partial is
 *   returned, so the store keeps serving the previous Index);
 * - a Programme seen on two pages (offset drift) is kept once, first occurrence wins;
 * - the last successful crawl is memoized for [memoMs], so the family's Chaînes refreshed
 *   back to back share one crawl; concurrent callers wait for the same crawl;
 * - at most [maxPages] pages are fetched, whatever the source claims.
 */
class CatalogueIndex(
    private val fetchPage: suspend (pageIndex: Int) -> CataloguePage,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val memoMs: Long = 10 * 60 * 1000,
    private val maxAttempts: Int = 3,
    private val retryDelayMs: Long = 1_000,
    private val maxPages: Int = 50,
) {

    private val mutex = Mutex()
    private var memo: List<IndexEntry>? = null
    private var memoAt: Long = 0

    /** The whole catalogue, in crawl order, de-duplicated on the Programme id. */
    suspend fun all(): List<IndexEntry> = mutex.withLock {
        memo?.takeIf { clock() - memoAt <= memoMs }?.let { return@withLock it }
        val crawled = crawl()
        memo = crawled
        memoAt = clock()
        crawled
    }

    /** The slice of [channelId], crawl order kept. */
    suspend fun shows(channelId: String): List<Show> =
        all().filter { it.channelId == channelId }.map { it.show }

    private suspend fun crawl(): List<IndexEntry> {
        val byId = LinkedHashMap<String, IndexEntry>()
        var pageIndex = 0
        while (pageIndex < maxPages) {
            val page = fetchWithRetry(pageIndex)
            page.entries.forEach { byId.putIfAbsent(it.show.id.value, it) }
            if (!page.hasMore) break
            pageIndex++
        }
        return byId.values.toList()
    }

    private suspend fun fetchWithRetry(pageIndex: Int): CataloguePage {
        var attempt = 1
        while (true) {
            try {
                return fetchPage(pageIndex)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (attempt >= maxAttempts) throw error
                attempt++
                if (retryDelayMs > 0) delay(retryDelayMs)
            }
        }
    }
}
