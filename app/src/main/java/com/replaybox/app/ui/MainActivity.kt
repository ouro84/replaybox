package com.replaybox.app.ui

import android.app.UiModeManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ShowId

sealed interface Screen {
    object Home : Screen
    data class ShowDetail(val showId: ShowId, val title: String) : Screen
    data class Player(
        val episodeId: EpisodeId,
        val title: String,
        val progressContext: ProgressContext? = null,
        /** True when the user pressed « Reprendre »: no second prompt. */
        val autoResume: Boolean = false,
    ) : Screen
    object Settings : Screen
    data class ProviderDetail(val groupId: String) : Screen
    object About : Screen
    object SubtitleSettings : Screen
    object Search : Screen
    object Genres : Screen
    data class GenreGrid(val genre: Genre) : Screen
}

class MainActivity : ComponentActivity() {

    /** Phone or tablet: anything the system does not report as a TV (never the box). */
    private val mobile by lazy {
        DeviceKind.isMobile(getSystemService(UiModeManager::class.java).currentModeType)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) ReplayboxApplication.checkForUpdate()
        if (mobile) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // Android 8-10 only report the IME inset to Compose with adjustResize (Search hides
            // its chips while the keyboard is up). The box keeps its window mode.
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        setContent { CompositionLocalProvider(LocalMobile provides mobile, LocalInsets provides if (mobile) MobileInsets else TvInsets) {
            val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
            // Survives trips to a Programme or the player: scroll + focus restored.
            val homeState = remember { HomeUiState() }
            // One state per visit of Search: kept while a Programme opened from it is on top.
            var searchState by remember { mutableStateOf(SearchUiState()) }
            // One state per Programme: Back from the player lands on the Épisode just watched.
            val showStates = remember { HashMap<String, ShowUiState>() }
            // Back from a Provider page lands on its row.
            val settingsState = remember { SettingsUiState() }
            // One state per visit of Genres: Back from a grid lands on the tile that opened it.
            var genresState by remember { mutableStateOf(GenresUiState()) }
            // One state per opened grid: kept while a Programme opened from it is on top.
            var gridState by remember { mutableStateOf<GenreGridUiState?>(null) }
            fun openGrid(genre: Genre) {
                gridState = GenreGridUiState(genre)
                backStack.add(Screen.GenreGrid(genre))
            }
            // The rail (Home; on mobile also Recherche, Genres, Paramètres): a fresh visit on top of
            // Home, or back to the destination's root with its state. From Home the stack is [Home],
            // so this is the former add.
            fun navigate(destination: RailDestination) {
                // The destination already on screen: nothing moves, its state stays.
                val next = RailNavigation.next(backStack.toList(), destination) ?: return
                // Reset only for a fresh visit; back to the destination's root keeps its state.
                if (!RailNavigation.keepsState(backStack.toList(), destination)) when (destination) {
                    RailDestination.GENRES -> genresState = GenresUiState()
                    RailDestination.SEARCH -> searchState = SearchUiState()
                    RailDestination.HOME, RailDestination.SETTINGS -> Unit
                }
                backStack.clear()
                backStack.addAll(next)
            }
            BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }
            // Mutually exclusive with the two above: Home's own Back (scroll to top) is enabled
            // when Home is not at its top, this one only when it is.
            var exitAsked by remember { mutableStateOf(false) }
            BackHandler(enabled = ExitConfirm.shows(backStack.toList(), homeState.atTop)) { exitAsked = true }
            ReplayboxThemeSurface { CompactDensity(mobile) {
                // One Box for every screen: the mobile rail draws over any of them.
                Box(Modifier.fillMaxSize()) {
                    val current = backStack.last()
                    when (current) {
                        is Screen.Home -> HomeScreen(
                            state = homeState,
                            onOpenShow = { id, title -> backStack.add(Screen.ShowDetail(id, title)) },
                            onPlay = { id, title, progressContext ->
                                backStack.add(Screen.Player(id, title, progressContext, autoResume = true))
                            },
                            onNavigate = ::navigate,
                            onOpenGenre = ::openGrid,
                        )
                        is Screen.ShowDetail -> ShowScreen(
                            showId = current.showId,
                            title = current.title,
                            state = showStates.getOrPut(current.showId.value) { ShowUiState() },
                            onOpenEpisode = { episode, progressContext, autoResume ->
                                backStack.add(Screen.Player(episode.id, episode.title, progressContext, autoResume))
                            },
                        )
                        // Keyed by Épisode: Épisode suivant swaps the entry in place, and the
                        // player state (Flux, ExoPlayer) must start over, not stay on the ended one.
                        is Screen.Player -> key(current.episodeId) { PlayerScreen(
                            episodeId = current.episodeId,
                            title = current.title,
                            progressContext = current.progressContext,
                            autoResume = current.autoResume,
                            onPlayNext = { next ->
                                // Back from the player lands on the Épisode now playing, in its Saison.
                                current.progressContext?.showId?.let { showId ->
                                    showStates[showId.value]?.focusEpisode(next)
                                }
                                // Replaces the ended Épisode: Back returns to the Programme page.
                                backStack[backStack.lastIndex] = current.copy(
                                    episodeId = next.id,
                                    title = next.title,
                                    progressContext = current.progressContext?.copy(
                                        episodeTitle = next.title,
                                        thumbnailUrl = next.thumbnailUrl,
                                    ),
                                    autoResume = false,
                                )
                            },
                            onFinished = {
                                // The Épisode ended with nothing to play next: back where it was opened.
                                if (backStack.size > 1 && backStack.last() == current) backStack.removeAt(backStack.lastIndex)
                            },
                            onBack = {
                                if (backStack.size > 1 && backStack.last() == current) backStack.removeAt(backStack.lastIndex)
                            },
                        ) }
                        Screen.Settings -> SettingsScreen(
                            state = settingsState,
                            onOpenSubtitles = { backStack.add(Screen.SubtitleSettings) },
                            onOpenProvider = { groupId -> backStack.add(Screen.ProviderDetail(groupId)) },
                            onOpenAbout = { backStack.add(Screen.About) },
                        )
                        Screen.About -> AboutScreen()
                        Screen.SubtitleSettings -> SubtitleSettingsScreen()
                        is Screen.ProviderDetail -> key(current.groupId) { ProviderScreen(groupId = current.groupId) }
                        Screen.Search -> SearchScreen(
                            state = searchState,
                            onOpenShow = { id, title -> backStack.add(Screen.ShowDetail(id, title)) },
                            // « Reprendre » on the best result resumes at once, like Home.
                            onPlay = { id, title, progressContext ->
                                backStack.add(Screen.Player(id, title, progressContext, autoResume = true))
                            },
                        )
                        Screen.Genres -> GenresScreen(state = genresState, onOpenGenre = ::openGrid)
                        is Screen.GenreGrid -> key(current.genre) {
                            GenreGridScreen(
                                state = remember { gridState?.takeIf { it.genre == current.genre } ?: GenreGridUiState(current.genre) },
                                onOpenShow = { id, title -> backStack.add(Screen.ShowDetail(id, title)) },
                            )
                        }
                    }
                    // Home draws its own rail (expand and dim on the box); the player has none.
                    if (current !is Screen.Home) MobileRail(backStack.toList(), ::navigate)
                }
                // Mobile safety net: the window is portrait anyway (split screen, override).
                if (portraitBlocked()) RotatePrompt()
                if (exitAsked) ActionDialog(
                    title = stringResource(R.string.exit_title),
                    hint = null,
                    actions = listOf(DialogAction(stringResource(R.string.exit_confirm)) { finish() }),
                    onDismiss = { exitAsked = false },
                )
            } }
        } }
    }

    override fun onResume() {
        super.onResume()
        if (mobile) hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The bars come back after the IME or a dialog: hide them again.
        if (hasFocus && mobile) hideSystemBars()
    }

    /** Immersive on mobile: no status or navigation bar; a swipe from the edge shows them for a while. */
    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Landscape phones (LayoutClass.COMPACT): the screens see a 540 dp-tall window (CompactCanvas).
 * The class is read from the real window; the box and tablets keep the system density.
 */
@Composable
private fun CompactDensity(mobile: Boolean, content: @Composable () -> Unit) {
    // The box never reads the configuration here: no root recomposition on its config changes.
    val heightDp = if (mobile) LocalConfiguration.current.screenHeightDp else 0
    // One provider for every class, no branch: switching between an `if` that calls content()
    // and an `else` that provides the scaled density rebuilt every screen when the window crossed
    // 480 dp (a foldable folded mid-playback restarted the player). Never `return content()`
    // either: Compose compiler 1.4.3 closes the groups before the returned call (blank screens).
    CompositionLocalProvider(
        LocalDensity provides CompactCanvas.density(LocalDensity.current, mobile, heightDp),
        LocalTextRatio provides CompactCanvas.textRatio(mobile, heightDp),
        LocalRail provides RailMetrics.of(mobile, heightDp),
        content = content,
    )
}
