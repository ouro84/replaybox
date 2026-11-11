package com.replaybox.app.ui

/**
 * When Back asks « Quitter Replaybox ? » instead of leaving: only from the top of Home.
 * Anywhere else Back goes back, and from deeper in Home it first scrolls to the top.
 */
object ExitConfirm {
    fun shows(stack: List<Screen>, homeAtTop: Boolean): Boolean =
        stack == listOf(Screen.Home) && homeAtTop
}
