package de.remotemc.app.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class CompleteAtTest {
    private fun complete(text: String, cursor: Int, suggestion: String): Pair<String, Int> =
        completeAt(TextFieldValue(text, TextRange(cursor)), suggestion).let { it.text to it.selection.end }

    @Test
    fun lastWordGetsASpaceAfter() {
        assertEquals("gamemode creative " to 18, complete("gamemode cr", 11, "creative"))
        assertEquals("gamemode " to 9, complete("gam", 3, "gamemode"))
        assertEquals("/gamemode " to 10, complete("/gam", 4, "gamemode"))
    }

    @Test
    fun wordAtTheCursorIsReplacedInsideTheText() {
        // Cursor after "cr", the player name behind it stays.
        assertEquals("gamemode creative Steve" to 18, complete("gamemode cr Steve", 11, "creative"))
        // Cursor in the middle of a word: the whole word is replaced.
        assertEquals("gamemode creative Steve" to 18, complete("gamemode crxx Steve", 11, "creative"))
        // First word with the cursor at its start.
        assertEquals("kick Steve" to 5, complete("kic Steve", 0, "kick"))
    }

    @Test
    fun emptyWordAfterASpace() {
        assertEquals("gamemode creative Steve " to 24, complete("gamemode creative ", 18, "Steve"))
    }
}
