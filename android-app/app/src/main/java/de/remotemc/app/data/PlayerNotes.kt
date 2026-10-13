package de.remotemc.app.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Private notes about players, per server, kept only on this device. */
class PlayerNotes(private val prefs: SharedPreferences) {
    private fun key(serverId: String, uuid: String) = "$serverId/$uuid"

    fun get(serverId: String, uuid: String): String = prefs.getString(key(serverId, uuid), null).orEmpty()

    fun set(serverId: String, uuid: String, text: String) = prefs.edit {
        val clean = text.take(MAX_LENGTH)
        if (clean.isBlank()) remove(key(serverId, uuid)) else putString(key(serverId, uuid), clean)
    }

    /** Removes the notes of a deleted server. */
    fun deleteServer(serverId: String) = prefs.edit {
        prefs.all.keys.filter { it.startsWith("$serverId/") }.forEach(::remove)
    }

    companion object {
        const val MAX_LENGTH = 1000
    }
}
