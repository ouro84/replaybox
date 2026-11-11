package com.replaybox.app.ui

/** What Back does on Home, the only screen where the stack is `[Home]`. */
enum class HomeBack { SCROLL_TO_TOP, ASK }

/**
 * When Back asks « Quitter Replaybox ? » instead of leaving: only from the top of Home.
 * From deeper in Home it first scrolls to the top; anywhere else Back goes back (root handler).
 */
object ExitConfirm {
    fun onHomeBack(atTop: Boolean): HomeBack = if (atTop) HomeBack.ASK else HomeBack.SCROLL_TO_TOP
}
