package de.remotemc.app.data

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** A saved command, run with one tap from the command menu. */
data class Favorite(
    val id: String = UUID.randomUUID().toString(),
    /** Without the leading "/"; may hold placeholders like {player} and {text}. */
    val command: String,
    /** Ask before running (e.g. for /stop). */
    val confirm: Boolean = false,
)

/** One command of a macro, run [waitSeconds] after the previous one. */
data class MacroStep(val command: String, val waitSeconds: Int = 0)

/** Several commands in a row, e.g. announce a restart, wait, save, restart. */
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MacroStep>,
    val confirm: Boolean = false,
) {
    /** All placeholders of all steps, each once. */
    val placeholders: List<String> get() = Placeholders.find(steps.joinToString(" ") { it.command })

    /** Total waiting time in seconds. */
    val duration: Int get() = steps.sumOf { it.waitSeconds }
}

data class Shortcuts(val favorites: List<Favorite> = emptyList(), val macros: List<Macro> = emptyList()) {
    fun toJson(): JSONObject = JSONObject()
        .put("favorites", JSONArray(favorites.map { JSONObject().put("id", it.id).put("command", it.command).put("confirm", it.confirm) }))
        .put("macros", JSONArray(macros.map { m ->
            JSONObject().put("id", m.id).put("name", m.name).put("confirm", m.confirm)
                .put("steps", JSONArray(m.steps.map { JSONObject().put("command", it.command).put("wait", it.waitSeconds) }))
        }))

    fun upsert(favorite: Favorite) = copy(favorites = favorites.replaceOrAdd(favorite) { it.id == favorite.id })
    fun upsert(macro: Macro) = copy(macros = macros.replaceOrAdd(macro) { it.id == macro.id })
    fun removeFavorite(id: String) = copy(favorites = favorites.filterNot { it.id == id })
    fun removeMacro(id: String) = copy(macros = macros.filterNot { it.id == id })
    fun moveFavorite(from: Int, to: Int) = copy(favorites = favorites.moved(from, to))
    fun moveMacro(from: Int, to: Int) = copy(macros = macros.moved(from, to))

    companion object {
        fun fromJson(o: JSONObject): Shortcuts {
            fun objects(arr: JSONArray?) = if (arr == null) emptyList() else List(arr.length()) { arr.getJSONObject(it) }
            return Shortcuts(
                favorites = objects(o.optJSONArray("favorites")).mapNotNull { f ->
                    val command = f.optString("command").trim()
                    if (command.isEmpty()) null else Favorite(f.optString("id").ifBlank { UUID.randomUUID().toString() }, command, f.optBoolean("confirm"))
                },
                macros = objects(o.optJSONArray("macros")).mapNotNull { m ->
                    val steps = objects(m.optJSONArray("steps"))
                        .map { MacroStep(it.optString("command").trim(), it.optInt("wait").coerceIn(0, MAX_WAIT)) }
                        .filter { it.command.isNotEmpty() }
                    if (steps.isEmpty()) null else Macro(m.optString("id").ifBlank { UUID.randomUUID().toString() }, m.optString("name"), steps, m.optBoolean("confirm"))
                },
            )
        }

        /** Longest wait between two macro steps (1 hour). */
        const val MAX_WAIT = 3600
    }
}

private fun <T> List<T>.replaceOrAdd(item: T, same: (T) -> Boolean): List<T> =
    if (any(same)) map { if (same(it)) item else it } else this + item

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Placeholders in saved commands: {player} (also {spieler}) is picked from the online players,
 * {text} is typed in. Numbered ones ({player2}, {text2}) ask separately.
 */
object Placeholders {
    private val PATTERN = Regex("\\{(player|spieler|text)(\\d*)\\}", RegexOption.IGNORE_CASE)

    /** Distinct placeholders in order of appearance, normalised ("{spieler}" → "{player}"). */
    fun find(command: String): List<String> =
        PATTERN.findAll(command).map { normalise(it) }.distinct().toList()

    fun isPlayer(placeholder: String) = placeholder.startsWith("{player")

    fun fill(command: String, values: Map<String, String>): String =
        PATTERN.replace(command) { values[normalise(it)] ?: it.value }

    private fun normalise(match: MatchResult): String {
        val kind = match.groupValues[1].lowercase(Locale.ROOT).let { if (it == "spieler") "player" else it }
        return "{$kind${match.groupValues[2]}}"
    }
}

/** Favorites and macros per server, kept on the device. */
class ShortcutStore(private val prefs: SharedPreferences) {
    fun load(serverId: String): Shortcuts =
        prefs.getString(serverId, null)?.let { runCatching { Shortcuts.fromJson(JSONObject(it)) }.getOrNull() } ?: Shortcuts()

    fun save(serverId: String, shortcuts: Shortcuts) = prefs.edit { putString(serverId, shortcuts.toJson().toString()) }

    fun delete(serverId: String) = prefs.edit { remove(serverId) }
}

/** The server's commands for the command menu. */
object CommandCatalog {
    /**
     * Commands the app already offers as buttons (players, whitelist, bans, world, chat, plugins);
     * the menu leaves them out so it shows what is not reachable otherwise.
     */
    val FEATURES = setOf(
        "kick", "ban", "ban-ip", "banlist", "pardon", "pardon-ip", "op", "deop", "whitelist",
        "time", "weather", "difficulty", "gamerule", "gamemode", "say", "tell", "msg", "w", "list", "plugins", "pl",
    )

    private val CORE = listOf("Minecraft", "Paper", "Spigot", "Bukkit", "Server")

    /** Commands without app features, filtered by [query], grouped by plugin (Minecraft first). */
    fun grouped(commands: List<CommandInfo>, query: String): List<Pair<String, List<CommandInfo>>> {
        val q = query.trim().removePrefix("/")
        return commands
            .filter { c -> c.name !in FEATURES && c.aliases.none { it in FEATURES } }
            .filter { c ->
                q.isEmpty() || c.name.contains(q, ignoreCase = true) || c.aliases.any { it.contains(q, ignoreCase = true) } ||
                    c.description?.contains(q, ignoreCase = true) == true || c.plugin?.contains(q, ignoreCase = true) == true
            }
            .groupBy { it.plugin ?: "Server" }
            .map { (plugin, list) -> plugin to list.sortedBy { it.name } }
            .sortedWith(compareBy({ CORE.indexOf(it.first).let { i -> if (i < 0) CORE.size else i } }, { it.first.lowercase(Locale.ROOT) }))
    }
}
