package de.remotemc.app.notify

/**
 * Joins of the last [windowMs] per server, so several joins become one notification
 * ("3 players joined Survival: Alex, Steve, Sam") instead of one each.
 */
class JoinBundle(private val windowMs: Long = 15 * 60_000L) {
    private val joins = mutableMapOf<String, MutableList<Pair<String, Long>>>()

    /** Adds [player] and returns everyone who joined [server] recently, oldest first. */
    @Synchronized
    fun add(server: String, player: String, now: Long): List<String> {
        val list = joins.getOrPut(server) { mutableListOf() }
        list.removeAll { (name, time) -> now - time > windowMs || name.equals(player, ignoreCase = true) }
        list += player to now
        return list.map { it.first }
    }

    /** Forgets the joins of [server] (the notification was opened or swiped away). */
    @Synchronized
    fun clear(server: String) {
        joins.remove(server)
    }
}
