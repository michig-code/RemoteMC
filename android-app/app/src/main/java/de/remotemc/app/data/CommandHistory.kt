package de.remotemc.app.data

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray

/** Recently run console commands per server, newest first. */
class CommandHistory(private val prefs: SharedPreferences) {

    fun load(serverId: String): List<String> {
        val raw = prefs.getString(key(serverId), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** Moves [command] to the front and returns the new list. */
    fun add(serverId: String, command: String): List<String> {
        val list = (listOf(command) + load(serverId).filterNot { it == command }).take(MAX)
        prefs.edit { putString(key(serverId), JSONArray(list).toString()) }
        return list
    }

    fun remove(serverId: String, command: String): List<String> {
        val list = load(serverId).filterNot { it == command }
        prefs.edit { putString(key(serverId), JSONArray(list).toString()) }
        return list
    }

    fun clear(serverId: String) = prefs.edit { remove(key(serverId)) }

    private fun key(serverId: String) = "history.$serverId"

    private companion object {
        const val MAX = 15
    }
}
