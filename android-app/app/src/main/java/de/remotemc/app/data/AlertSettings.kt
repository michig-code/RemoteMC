package de.remotemc.app.data

/** What to be notified about while the app is in the background. */
data class AlertSettings(
    val enabled: Boolean = false,
    val offline: Boolean = true,
    val lowTps: Boolean = true,
    val tpsThreshold: Int = 15,
    val joins: Boolean = false,
    /** Comma-separated player names for join alerts; empty = everyone. */
    val players: String = "",
    val mentions: Boolean = true,
    /** Comma-separated words that count as a mention, e.g. your name. */
    val words: String = "",
    /** Quiet hours: notifications still arrive, but without sound or vibration. */
    val quiet: Boolean = false,
    /** Start and end of the quiet hours in minutes after midnight (may span midnight). */
    val quietFrom: Int = 22 * 60,
    val quietTo: Int = 7 * 60,
) {
    /** True when [minuteOfDay] (0–1439) lies in the quiet hours. */
    fun isQuiet(minuteOfDay: Int): Boolean {
        if (!quiet || quietFrom == quietTo) return false
        return if (quietFrom < quietTo) minuteOfDay in quietFrom until quietTo
        else minuteOfDay >= quietFrom || minuteOfDay < quietTo
    }

    val playerList get() = players.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val wordList get() = words.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
