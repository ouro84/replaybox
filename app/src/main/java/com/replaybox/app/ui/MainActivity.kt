package com.replaybox.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ShowId

sealed interface Screen {
    object Home : Screen
    data class ShowDetail(val showId: ShowId, val title: String) : Screen
    data class Player(
        val episodeId: EpisodeId,
        val title: String,
        val progressContext: ProgressContext? = null,
    ) : Screen
    object Login : Screen
    object Search : Screen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) ReplayboxApplication.checkForUpdate()
        setContent {
            val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
            // Survives trips to a Programme or the player: scroll + focus restored.
            val homeState = remember { HomeUiState() }
            BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }
            ReplayboxThemeSurface {
                when (val current = backStack.last()) {
                    is Screen.Home -> HomeScreen(
                        state = homeState,
                        onOpenShow = { id, title -> backStack.add(Screen.ShowDetail(id, title)) },
                        onPlay = { id, title, progressContext ->
                            backStack.add(Screen.Player(id, title, progressContext))
                        },
                        onNavigate = { destination ->
                            when (destination) {
                                RailDestination.HOME -> Unit
                                RailDestination.SEARCH -> backStack.add(Screen.Search)
                                RailDestination.SETTINGS -> backStack.add(Screen.Login)
                            }
                        },
                    )
                    is Screen.ShowDetail -> ShowScreen(
                        showId = current.showId,
                        title = current.title,
                        onOpenEpisode = { episode, progressContext ->
                            backStack.add(Screen.Player(episode.id, episode.title, progressContext))
                        },
                    )
                    is Screen.Player -> PlayerScreen(
                        episodeId = current.episodeId,
                        title = current.title,
                        progressContext = current.progressContext,
                    )
                    Screen.Login -> LoginScreen(onDone = { backStack.removeAt(backStack.lastIndex) })
                    Screen.Search -> SearchScreen(
                        onOpenShow = { id, title -> backStack.add(Screen.ShowDetail(id, title)) },
                    )
                }
            }
        }
    }
}
