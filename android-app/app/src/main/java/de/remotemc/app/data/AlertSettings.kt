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
) {
    val playerList get() = players.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val wordList get() = words.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
