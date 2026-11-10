package com.replaybox.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.foundation.lazy.grid.TvLazyGridState
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ResumeItem
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Titles

// ---- On-screen keyboard ----

/** One key of the Search keyboard; [focusKey] restores focus on it. */
sealed interface KeyboardKey {
    val focusKey: String

    data class Letter(val char: Char) : KeyboardKey {
        override val focusKey: String get() = SearchFocus.KEY_PREFIX + char
    }

    object Space : KeyboardKey {
        override val focusKey: String = SearchFocus.KEY_PREFIX + "space"
    }

    object Delete : KeyboardKey {
        override val focusKey: String = SearchFocus.KEY_PREFIX + "delete"
    }
}

/**
 * The D-pad keyboard that replaces the system IME on Search (ROADMAP M4, grilling 2026-11-07):
 * A–Z then 0–9 in rows of six, then the wide « Espace » and « Effacer » keys. No accents (search
 * is normalized) and no voice. Letters are typed in lower case.
 */
object SearchKeyboard {
    const val COLUMNS = 6

    /** Longer than any Programme title worth typing; keeps the query on one line. */
    const val MAX_LENGTH = 40

    val rows: List<List<KeyboardKey>> =
        (('A'..'Z') + ('0'..'9')).map { KeyboardKey.Letter(it) }.chunked(COLUMNS) +
            listOf(listOf(KeyboardKey.Space, KeyboardKey.Delete))

    val first: KeyboardKey = rows.first().first()

    /** The query after pressing [key]: no leading or double space, nothing past [MAX_LENGTH]. */
    fun type(query: String, key: KeyboardKey): String = when (key) {
        is KeyboardKey.Letter -> if (query.length >= MAX_LENGTH) query else query + key.char.lowercaseChar()
        KeyboardKey.Space ->
            if (query.isEmpty() || query.endsWith(' ') || query.length >= MAX_LENGTH) query else "$query "
        KeyboardKey.Delete -> query.dropLast(1)
    }

    /** Keys of the right-hand edge: RIGHT from them enters the results column. */
    fun isLastColumn(key: KeyboardKey): Boolean = rows.any { row -> row.last() == key }
}

// ---- Results ----

/** One shown Programme with the Chaîne it was found on (null when unknown, never guessed). */
data class SearchEntry(val show: Show, val channelId: String?)

/** What one Chaîne's Provider answered for a query; [shows] is null when it failed. */
data class ChannelHits(val channelId: String, val shows: List<Show>?)

/** Every Chaîne's answer to one (trimmed) [query], kept apart so filters apply before fusion. */
data class SearchHits(val query: String, val channels: List<ChannelHits>) {

    /** Every Provider failed: « Recherche indisponible. », not « no result » (Round 8 Q4). */
    val failed: Boolean get() = channels.isNotEmpty() && channels.all { it.shows == null }

    /**
     * The ranked results under a Chaîne group ([channelIds], null: every Chaîne) and a [genre].
     * Filtering comes before the title fusion, as on « Tout voir »: a group keeps its own copy
     * of a Programme another group also lists.
     */
    fun filtered(channelIds: Set<String>?, genre: Genre?): List<SearchEntry> {
        val kept = channels
            .filter { channelIds == null || it.channelId in channelIds }
            .map { hits -> hits.channelId to hits.shows.orEmpty().filter { genre == null || genre in it.genres } }
        val fused = SearchFusion.fuse(query, kept.map { it.second })
        val origin = HashMap<ShowId, String>()
        for ((channelId, shows) in kept) for (show in shows) origin.putIfAbsent(show.id, channelId)
        return fused.map { SearchEntry(it, origin[it.id]) }
    }
}

/**
 * The best result: the top-ranked entry when its normalized title equals the query or starts
 * with it. Anything else is not highlighted.
 */
object BestResult {
    fun pick(query: String, ranked: List<SearchEntry>): SearchEntry? {
        val wanted = Titles.normalize(query)
        if (wanted.isEmpty()) return null
        val top = ranked.firstOrNull() ?: return null
        return top.takeIf { Titles.normalize(it.show.title).startsWith(wanted) }
    }
}

/** Chaîne-group chip ([groupId], a ProviderGroups id; null is « Tout ») and Genre chip (null is « Tout »). */
data class SearchFilter(val groupId: String? = null, val genre: Genre? = null) {
    val isActive: Boolean get() = groupId != null || genre != null
}

/** The cached grid read for [filter] while the query is empty. */
data class BrowseResult(val filter: SearchFilter, val entries: List<SearchEntry>)

enum class SearchStatus { HINT, LOADING, UNAVAILABLE, EMPTY }

/** The right column under the chips: the best result, the poster grid, or one status line. */
data class SearchView(val best: SearchEntry?, val grid: List<SearchEntry>, val status: SearchStatus?) {
    companion object {
        fun status(status: SearchStatus) = SearchView(null, emptyList(), status)
        val HINT = status(SearchStatus.HINT)
    }
}

object SearchViews {

    /**
     * What Search shows. A non-empty [query] reads [hits] (the previous query's results stay on
     * screen while the next one runs, so typing never flashes a status); an empty query lists
     * [browse] when a filter is active, else the hint. Never « Aucun Programme trouvé. » while
     * a search or a read is pending. [channelIds] are the selected group's Chaînes.
     */
    fun of(
        query: String,
        filter: SearchFilter,
        channelIds: Set<String>?,
        hits: SearchHits?,
        browse: BrowseResult?,
        searching: Boolean,
    ): SearchView {
        val wanted = query.trim()
        if (wanted.isEmpty()) {
            if (!filter.isActive) return SearchView.HINT
            val entries = browse?.entries.orEmpty()
            if (entries.isEmpty()) {
                val pending = browse == null || browse.filter != filter
                return SearchView.status(if (pending) SearchStatus.LOADING else SearchStatus.EMPTY)
            }
            return SearchView(null, entries, null)
        }
        if (hits == null) return SearchView.status(SearchStatus.LOADING)
        val pending = searching || hits.query != wanted
        if (hits.failed) return SearchView.status(if (pending) SearchStatus.LOADING else SearchStatus.UNAVAILABLE)
        val entries = hits.filtered(channelIds, filter.genre)
        if (entries.isEmpty()) return SearchView.status(if (pending) SearchStatus.LOADING else SearchStatus.EMPTY)
        val best = BestResult.pick(hits.query, entries)
        return SearchView(best, if (best == null) entries else entries.drop(1), null)
    }
}

// ---- Focus ----

/** Focus keys of every Search target, so focus survives a trip to a Programme page. */
object SearchFocus {
    const val KEY_PREFIX = "key:"
    const val KEY_A = KEY_PREFIX + "A"
    const val BEST_RESUME = "best:resume"
    const val BEST_EPISODES = "best:episodes"
    const val RETRY = "retry"
    private const val GROUP_CHIP = "chip:group:"
    private const val GENRE_CHIP = "chip:genre:"
    private const val RESULT = "show:"

    fun groupChip(groupId: String?): String = GROUP_CHIP + (groupId ?: "all")

    fun genreChip(genre: Genre?): String = GENRE_CHIP + (genre?.name ?: "all")

    fun result(showId: String): String = RESULT + showId

    fun isKey(key: String): Boolean = key.startsWith(KEY_PREFIX)

    fun isChip(key: String): Boolean = key.startsWith(GROUP_CHIP) || key.startsWith(GENRE_CHIP)

    fun isResult(key: String): Boolean = key.startsWith(RESULT)
}

/**
 * Search state hoisted above the back stack (MainActivity keeps one per visit of Search):
 * opening a Programme and coming back restores the query, the filters, the results, the scroll
 * and the exact focus (a key, a chip, the best result or a poster).
 */
class SearchUiState {
    var query by mutableStateOf("")
    var filter by mutableStateOf(SearchFilter())

    /** The latest query's answers; null before the first search. */
    var hits by mutableStateOf<SearchHits?>(null)

    /** The latest cached grid read for an empty query with a filter. */
    var browse by mutableStateOf<BrowseResult?>(null)

    /** Captions: the Chaîne each result is listed on (cached listings only). */
    var listedChannels by mutableStateOf<Map<ShowId, String>>(emptyMap())

    /** « Reprendre » of the best result: its newest unfinished Épisode, if any. */
    var bestResume by mutableStateOf<ResumeItem?>(null)

    val grid = TvLazyGridState()

    /** The element focused last, anywhere on the screen. */
    var focusKey: String? = null
        private set

    /** The keyboard key focused last: LEFT and Back from the results column return to it. */
    var lastKey: String = SearchFocus.KEY_A
        private set

    /** The results-column element focused last; forgotten when the query changes. */
    private var rightKey: String? = null

    /** True while focus is in the results column: Back then returns to the keyboard. */
    var inRightColumn by mutableStateOf(false)
        private set

    private val requesters = HashMap<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun focus(key: String) {
        focusKey = key
        if (SearchFocus.isKey(key)) {
            lastKey = key
            inRightColumn = false
        } else {
            rightKey = key
            inRightColumn = true
        }
    }

    /** Applies [key]; true when the query changed (the results column then goes back to its top). */
    fun type(key: KeyboardKey): Boolean {
        val next = SearchKeyboard.type(query, key)
        if (next == query) return false
        query = next
        rightKey = null
        return true
    }

    /**
     * The native keyboard's text (mobile): kept as typed, cut at [SearchKeyboard.MAX_LENGTH];
     * true when the query changed, like [type].
     */
    fun setQuery(text: String): Boolean {
        val next = text.take(SearchKeyboard.MAX_LENGTH)
        if (next == query) return false
        query = next
        rightKey = null
        return true
    }

    /**
     * Mobile: a fresh visit opens the keyboard; coming back from a Programme does not, whether
     * it was found by typing (query kept) or by browsing with the chips (a result was tapped).
     */
    val opensKeyboardOnEntry: Boolean get() = query.isEmpty() && focusKey == null

    /** Coming back with the same query reuses the results; a new query or « Réessayer » searches. */
    fun needsSearch(fromRetry: Boolean): Boolean {
        val wanted = query.trim()
        return wanted.isNotEmpty() && (fromRetry || hits?.query != wanted)
    }

    /** Where focus lands on arrival: the element focused last when still shown, else « A ». */
    fun initialFocus(view: SearchView, resumeShown: Boolean): String =
        focusKey?.let { shown(it, view, resumeShown) } ?: SearchFocus.KEY_A

    /**
     * RIGHT from the keyboard's last column: the results-column element focused last (same
     * query, still shown), else the best result, else the first poster, else « Réessayer »,
     * else the selected Chaîne chip.
     */
    fun rightEntry(view: SearchView, resumeShown: Boolean): String {
        rightKey?.let { last -> shown(last, view, resumeShown)?.let { return it } }
        return when {
            view.best != null -> if (resumeShown) SearchFocus.BEST_RESUME else SearchFocus.BEST_EPISODES
            view.grid.isNotEmpty() -> SearchFocus.result(view.grid.first().show.id.value)
            view.status == SearchStatus.UNAVAILABLE -> SearchFocus.RETRY
            else -> SearchFocus.groupChip(filter.groupId)
        }
    }

    /** [key] if its element is on screen in [view] (« Reprendre » gone: « Épisodes »), else null. */
    private fun shown(key: String, view: SearchView, resumeShown: Boolean): String? = when {
        SearchFocus.isKey(key) || SearchFocus.isChip(key) -> key
        key == SearchFocus.BEST_RESUME -> when {
            view.best == null -> null
            resumeShown -> key
            else -> SearchFocus.BEST_EPISODES
        }
        key == SearchFocus.BEST_EPISODES -> key.takeIf { view.best != null }
        key == SearchFocus.RETRY -> key.takeIf { view.status == SearchStatus.UNAVAILABLE }
        SearchFocus.isResult(key) -> key.takeIf { view.grid.any { SearchFocus.result(it.show.id.value) == key } }
        else -> null
    }
}
