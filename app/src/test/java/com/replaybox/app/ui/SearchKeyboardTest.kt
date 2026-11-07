package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchKeyboardTest {

    private fun letter(char: Char) = KeyboardKey.Letter(char)

    @Test
    fun layoutIsSixColumnsOfLettersThenDigitsThenTheWideKeys() {
        val rows = SearchKeyboard.rows

        assertThat(rows).hasSize(7)
        assertThat(rows.take(6).map { it.size }.distinct()).containsExactly(SearchKeyboard.COLUMNS)
        val characters = rows.take(6).flatten().map { (it as KeyboardKey.Letter).char }.joinToString("")
        assertThat(characters).isEqualTo("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
        assertThat(rows.last()).containsExactly(KeyboardKey.Space, KeyboardKey.Delete).inOrder()
    }

    @Test
    fun theLastColumnHoldsFLRX39AndEffacer() {
        val last = SearchKeyboard.rows.flatten().filter(SearchKeyboard::isLastColumn)

        assertThat(last).containsExactly(
            letter('F'), letter('L'), letter('R'), letter('X'), letter('3'), letter('9'), KeyboardKey.Delete,
        ).inOrder()
    }

    @Test
    fun lettersAppendInLowerCase() {
        val typed = listOf('P', 'R', 'O', '2').fold("") { query, char -> SearchKeyboard.type(query, letter(char)) }

        assertThat(typed).isEqualTo("pro2")
    }

    @Test
    fun spaceNeverLeadsNorDoubles() {
        assertThat(SearchKeyboard.type("", KeyboardKey.Space)).isEqualTo("")
        assertThat(SearchKeyboard.type("koh", KeyboardKey.Space)).isEqualTo("koh ")
        assertThat(SearchKeyboard.type("koh ", KeyboardKey.Space)).isEqualTo("koh ")
    }

    @Test
    fun effacerDeletesOneCharacter() {
        assertThat(SearchKeyboard.type("pro", KeyboardKey.Delete)).isEqualTo("pr")
        assertThat(SearchKeyboard.type("", KeyboardKey.Delete)).isEqualTo("")
    }

    @Test
    fun typingStopsAtTheMaximumLength() {
        val full = "a".repeat(SearchKeyboard.MAX_LENGTH)

        assertThat(SearchKeyboard.type(full, letter('B'))).isEqualTo(full)
        assertThat(SearchKeyboard.type(full, KeyboardKey.Space)).isEqualTo(full)
    }

    @Test
    fun everyKeyHasItsOwnFocusKey() {
        val keys = SearchKeyboard.rows.flatten().map { it.focusKey }

        assertThat(keys).containsNoDuplicates()
        assertThat(SearchKeyboard.first.focusKey).isEqualTo(SearchFocus.KEY_A)
        assertThat(keys.all(SearchFocus::isKey)).isTrue()
    }
}
