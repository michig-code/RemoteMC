package de.remotemc.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which console lines are shown. */
enum class LevelFilter { ALL, WARNINGS, ERRORS }

/** Searching and filtering the console. */
object ConsoleSearch {
    private val ERRORS = setOf("ERROR", "FATAL", "SEVERE")
    private val WARNINGS = setOf("WARN", "WARNING")

    fun isError(level: String) = level.uppercase(Locale.ROOT) in ERRORS

    /** Warnings and worse. */
    fun isWarning(level: String) = level.uppercase(Locale.ROOT).let { it in WARNINGS || it in ERRORS }

    fun filter(entries: List<ConsoleEntry>, level: LevelFilter): List<ConsoleEntry> = when (level) {
        LevelFilter.ALL -> entries
        LevelFilter.WARNINGS -> entries.filter { isWarning(it.level) }
        LevelFilter.ERRORS -> entries.filter { isError(it.level) }
    }

    /** Positions in [entries] of the lines containing [query] (ignoring case), oldest first. */
    fun matches(entries: List<ConsoleEntry>, query: String): List<Int> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return entries.indices.filter { entries[it].text.contains(q, ignoreCase = true) }
    }

    /** Where [query] occurs in [text] (ignoring case), for highlighting. */
    fun ranges(text: String, query: String): List<IntRange> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val result = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val i = text.indexOf(q, from, ignoreCase = true)
            if (i < 0) break
            result += i until i + q.length
            from = i + q.length
        }
        return result
    }

    /** Plain text for sharing: "[12:00:01 WARN] message", at most [max] newest lines. */
    fun shareText(entries: List<ConsoleEntry>, max: Int = 500): String {
        val format = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        return entries.takeLast(max).joinToString("\n") { e ->
            val level = e.level.takeIf { it in setOf("INFO", "WARN", "ERROR", "FATAL", "WARNING", "SEVERE") }
            "[" + format.format(Date(e.time)) + (level?.let { " $it" } ?: "") + "] " + e.text
        }
    }
}
