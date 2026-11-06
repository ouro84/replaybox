package com.replaybox.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ShowId

sealed interface Screen {
    object Home : Screen
    data class ShowDetail(val showId: ShowId, val title: String) : Screen
    data class Player(val episodeId: EpisodeId, val title: String) : Screen
    object Login : Screen
    object Search : Screen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) ReplayboxApplication.checkForUpdate()
        setContent {
            val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
            BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }
            ReplayboxThemeSurface {
                when (val current = backStack.last()) {
                    is Screen.Home -> HomeScreen(
                        onOpenShow = { id, title -> backStack.add(Screen.ShowDetail(id, title)) },
                        onOpenLogin = { backStack.add(Screen.Login) },
                        onOpenSearch = { backStack.add(Screen.Search) },
                    )
                    is Screen.ShowDetail -> ShowScreen(
                        showId = current.showId,
                        title = current.title,
                        onOpenEpisode = { id, title -> backStack.add(Screen.Player(id, title)) },
                    )
                    is Screen.Player -> PlayerScreen(
                        episodeId = current.episodeId,
                        title = current.title,
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
