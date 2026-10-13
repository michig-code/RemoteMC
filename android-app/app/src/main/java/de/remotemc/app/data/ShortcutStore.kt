package de.remotemc.app.data

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject

/** Favorites and macros per server, kept on the device. */
class ShortcutStore(private val prefs: SharedPreferences) {
    fun load(serverId: String): Shortcuts =
        prefs.getString(serverId, null)?.let { runCatching { Shortcuts.fromJson(JSONObject(it)) }.getOrNull() } ?: Shortcuts()

    fun save(serverId: String, shortcuts: Shortcuts) = prefs.edit { putString(serverId, shortcuts.toJson().toString()) }

    fun delete(serverId: String) = prefs.edit { remove(serverId) }
}
