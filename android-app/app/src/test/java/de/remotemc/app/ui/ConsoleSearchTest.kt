package de.remotemc.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleSearchTest {
    private val lines = listOf(
        ConsoleEntry("1", "Starting minecraft server", "INFO", 0),
        ConsoleEntry("2", "Can't keep up! Is the server overloaded?", "WARN", 1000),
        ConsoleEntry("3", "Could not pass event to Essentials", "ERROR", 2000),
        ConsoleEntry("4", "> /gamemode creative Steve", "COMMAND", 3000),
        ConsoleEntry("5", "Plugin [Spark] is deprecated", "WARNING", 4000),
    )

    @Test
    fun levelFilter() {
        assertEquals(lines, ConsoleSearch.filter(lines, LevelFilter.ALL))
        assertEquals(listOf("2", "3", "5"), ConsoleSearch.filter(lines, LevelFilter.WARNINGS).map { it.key })
        assertEquals(listOf("3"), ConsoleSearch.filter(lines, LevelFilter.ERRORS).map { it.key })
    }

    @Test
    fun matchesIgnoreCase() {
        assertEquals(listOf(0, 1), ConsoleSearch.matches(lines, "SERVER"))
        assertEquals(emptyList<Int>(), ConsoleSearch.matches(lines, "  "))
        assertEquals(listOf(3), ConsoleSearch.matches(lines, " steve "))
    }

    @Test
    fun highlightRanges() {
        assertEquals(listOf(0..1, 4..5), ConsoleSearch.ranges("abxxAB", "ab"))
        assertEquals(emptyList<IntRange>(), ConsoleSearch.ranges("abc", ""))
    }

    @Test
    fun shareText() {
        val text = ConsoleSearch.shareText(lines.subList(1, 4))
        val rows = text.lines()
        assertEquals(3, rows.size)
        assertTrue(rows[0], rows[0].endsWith(" WARN] Can't keep up! Is the server overloaded?"))
        assertTrue(rows[2], rows[2].matches(Regex("\\[\\d\\d:\\d\\d:\\d\\d] > /gamemode creative Steve")))
        assertEquals(2, ConsoleSearch.shareText(lines, max = 2).lines().size)
    }
}
