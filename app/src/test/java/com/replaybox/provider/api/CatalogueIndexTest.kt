package com.replaybox.provider.api

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.show
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** Generic Index crawl (glossary: Index): paging, retry, dedupe, memo. */
class CatalogueIndexTest {

    private var now = 1_000_000L

    private fun entry(channel: String, slug: String) = IndexEntry(channel, show(slug, slug.uppercase()))

    /** Serves [pages] in order; [failures] maps a page index to how many times it throws first. */
    private class FakePages(
        private val pages: List<List<IndexEntry>>,
        private val failures: MutableMap<Int, Int> = mutableMapOf(),
        private val alwaysFail: Set<Int> = emptySet(),
        private val error: () -> Exception = { IOException("503") },
    ) {
        val calls = mutableListOf<Int>()
        var faultCleared = false

        suspend fun fetch(pageIndex: Int): CataloguePage {
            calls += pageIndex
            if (pageIndex in alwaysFail && !faultCleared) throw error()
            val left = failures[pageIndex] ?: 0
            if (left > 0) {
                failures[pageIndex] = left - 1
                throw error()
            }
            return CataloguePage(pages[pageIndex], hasMore = pageIndex < pages.lastIndex)
        }
    }

    private fun index(fake: FakePages, maxPages: Int = 50) = CatalogueIndex(
        fetchPage = fake::fetch,
        clock = { now },
        memoMs = 10_000,
        retryDelayMs = 0,
        maxPages = maxPages,
    )

    @Test
    fun crawlsUntilHasMoreIsFalse() = runBlocking {
        val fake = FakePages(
            listOf(
                listOf(entry("tf1", "a"), entry("tmc", "b")),
                listOf(entry("tf1", "c")),
                listOf(entry("lci", "d")),
            ),
        )
        val all = index(fake).all()
        assertThat(all.map { it.show.id.value }).containsExactly("a", "b", "c", "d").inOrder()
        assertThat(fake.calls).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun duplicatesAcrossPagesKeptOnce() = runBlocking {
        val fake = FakePages(
            listOf(
                listOf(entry("tf1", "a"), entry("tf1", "b")),
                listOf(entry("tmc", "a"), entry("tf1", "c")),
            ),
        )
        val all = index(fake).all()
        assertThat(all.count { it.show.id.value == "a" }).isEqualTo(1)
        assertThat(all.first { it.show.id.value == "a" }.channelId).isEqualTo("tf1")
        assertThat(all.map { it.show.id.value }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun retriesAFailingPage() = runBlocking {
        val fake = FakePages(
            listOf(listOf(entry("tf1", "a")), listOf(entry("tf1", "b"))),
            failures = mutableMapOf(1 to 1),
        )
        val all = index(fake).all()
        assertThat(all.map { it.show.id.value }).containsExactly("a", "b").inOrder()
        assertThat(fake.calls.count { it == 1 }).isEqualTo(2)
    }

    @Test
    fun failedPageAfterRetriesThrows() {
        val fake = FakePages(
            listOf(listOf(entry("tf1", "a")), listOf(entry("tf1", "b"))),
            alwaysFail = setOf(1),
        )
        val index = index(fake)
        assertThrows(IOException::class.java) { runBlocking { index.all() } }
        assertThat(fake.calls.count { it == 1 }).isEqualTo(3)

        fake.faultCleared = true
        fake.calls.clear()
        val all = runBlocking { index.all() }
        assertThat(fake.calls.first()).isEqualTo(0)
        assertThat(all.map { it.show.id.value }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun memoServesSecondCallerWithoutRefetch() = runBlocking {
        val fake = FakePages(listOf(listOf(entry("tf1", "a"), entry("tmc", "b"), entry("tf1", "c"))))
        val index = index(fake)

        assertThat(index.shows("tf1").map { it.id.value }).containsExactly("a", "c").inOrder()
        assertThat(index.shows("tf1").map { it.id.value }).containsExactly("a", "c").inOrder()
        assertThat(index.shows("tmc").map { it.id.value }).containsExactly("b")
        assertThat(fake.calls.count { it == 0 }).isEqualTo(1)

        now += 10_000 + 1
        index.shows("tf1")
        assertThat(fake.calls.count { it == 0 }).isEqualTo(2)
    }

    @Test
    fun stopsAtMaxPages() = runBlocking {
        val calls = mutableListOf<Int>()
        val index = CatalogueIndex(
            fetchPage = { page -> calls += page; CataloguePage(listOf(entry("tf1", "p$page")), hasMore = true) },
            clock = { now },
            retryDelayMs = 0,
            maxPages = 4,
        )
        index.all()
        assertThat(calls).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun cancellationIsNotRetried() {
        val fake = FakePages(
            listOf(listOf(entry("tf1", "a"))),
            alwaysFail = setOf(0),
            error = { CancellationException("left the screen") },
        )
        val index = index(fake)
        assertThrows(CancellationException::class.java) { runBlocking { index.all() } }
        assertThat(fake.calls).containsExactly(0)
    }
}
