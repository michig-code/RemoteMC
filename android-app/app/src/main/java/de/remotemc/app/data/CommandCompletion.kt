package de.remotemc.app.data

import java.util.Locale

/**
 * Suggestions the app can make without asking the server, so they appear at once (and also
 * without a connection): online players, game modes, worlds and the like for well-known
 * commands. The server's own tab completion adds the rest when it answers.
 */
object CommandCompletion {

    private enum class Kind { PLAYER, GAMEMODE, DIFFICULTY, WEATHER, TIME_ACTION, TIME_VALUE, BOOLEAN, WORLD, WHITELIST, EFFECT_ACTION }

    private val VALUES = mapOf(
        Kind.GAMEMODE to listOf("survival", "creative", "adventure", "spectator"),
        Kind.DIFFICULTY to listOf("peaceful", "easy", "normal", "hard"),
        Kind.WEATHER to listOf("clear", "rain", "thunder"),
        Kind.TIME_ACTION to listOf("set", "add", "query"),
        Kind.TIME_VALUE to listOf("day", "night", "noon", "midnight"),
        Kind.BOOLEAN to listOf("true", "false"),
        Kind.WHITELIST to listOf("add", "remove", "list", "on", "off", "reload"),
        Kind.EFFECT_ACTION to listOf("give", "clear"),
    )

    /** What the arguments of well-known commands are, by position. */
    private val ARGS: Map<String, List<Kind>> = buildMap {
        put("gamemode", listOf(Kind.GAMEMODE, Kind.PLAYER))
        put("defaultgamemode", listOf(Kind.GAMEMODE))
        put("difficulty", listOf(Kind.DIFFICULTY))
        put("weather", listOf(Kind.WEATHER))
        put("time", listOf(Kind.TIME_ACTION, Kind.TIME_VALUE))
        put("whitelist", listOf(Kind.WHITELIST, Kind.PLAYER))
        put("effect", listOf(Kind.EFFECT_ACTION, Kind.PLAYER))
        put("tp", listOf(Kind.PLAYER, Kind.PLAYER))
        put("teleport", listOf(Kind.PLAYER, Kind.PLAYER))
        put("mv", listOf(Kind.WORLD))
        for (cmd in listOf("kick", "ban", "op", "deop", "msg", "tell", "w", "give", "kill", "clear", "spawnpoint", "xp", "experience", "heal", "feed", "fly", "god", "invsee", "ec", "enderchest")) {
            put(cmd, listOf(Kind.PLAYER))
        }
    }

    /**
     * Local suggestions for the last word of [buffer] (without the leading "/"); empty while the
     * command name itself is typed (that comes from the command list).
     */
    fun suggest(buffer: String, players: List<String>, worlds: List<String>): List<String> {
        val text = buffer.removePrefix("/")
        if (' ' !in text) return emptyList()
        val parts = text.split(' ')
        val label = parts.first().lowercase(Locale.ROOT).substringAfter(':')
        val index = parts.size - 2 // position of the word being typed among the arguments
        val word = parts.last()
        val kind = ARGS[label]?.getOrNull(index)
        val candidates = when (kind) {
            null -> if (word.isNotEmpty()) players else emptyList() // a player name fits most commands
            Kind.PLAYER -> players
            Kind.WORLD -> worlds
            else -> VALUES[kind].orEmpty()
        }
        return filter(candidates, word)
    }

    /** Candidates starting with [word] (ignoring case), without the word itself, sorted, distinct. */
    fun filter(candidates: List<String>, word: String): List<String> =
        candidates
            .filter { it.startsWith(word, ignoreCase = true) && !it.equals(word, ignoreCase = true) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Server results first (they know best), then local ones they lack. */
    fun merge(server: List<String>, local: List<String>): List<String> =
        (server + local.filter { l -> server.none { it.equals(l, ignoreCase = true) } })
}

/**
 * The server's answers for recent inputs. An answer for "gamemode " also serves "gamemode cr"
 * (filtered), so typing on shows suggestions at once and going back needs no new request.
 */
class CompletionCache(private val maxEntries: Int = 100) {
    private val entries = object : LinkedHashMap<String, List<String>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > maxEntries
    }

    @Synchronized
    fun put(buffer: String, suggestions: List<String>) {
        entries[buffer] = suggestions
    }

    /** Best known answer for [buffer]: exact, or from a shorter input of the same argument. */
    @Synchronized
    fun lookup(buffer: String): List<String>? {
        val start = buffer.lastIndexOf(' ') + 1
        val word = buffer.substring(start)
        for (end in buffer.length downTo start) {
            val hit = entries[buffer.substring(0, end)] ?: continue
            return CommandCompletion.filter(hit, word)
        }
        return null
    }
}
