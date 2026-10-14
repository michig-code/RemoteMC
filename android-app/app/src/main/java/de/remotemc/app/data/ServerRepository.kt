package de.remotemc.app.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Saved servers. The whole list (including tokens) is stored encrypted. */
class ServerRepository(private val prefs: SharedPreferences, private val box: SecureBox) {

    private val _servers = MutableStateFlow(load())
    val servers: StateFlow<List<ServerProfile>> = _servers.asStateFlow()

    fun get(id: String): ServerProfile? = _servers.value.firstOrNull { it.id == id }

    fun upsert(profile: ServerProfile) {
        val list = _servers.value.toMutableList()
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) list[index] = profile else list += profile
        save(list)
    }

    fun delete(id: String) = save(_servers.value.filterNot { it.id == id })

    /** Moves [fromId] to the position of [toId] (manual sorting). */
    fun move(fromId: String, toId: String) {
        val list = _servers.value.toMutableList()
        val from = list.indexOfFirst { it.id == fromId }
        val to = list.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        list.add(to, list.removeAt(from))
        save(list)
    }

    /** Saves a new order (and folders / pins) after a drag; ignored if it does not hold the same servers. */
    fun setOrder(list: List<ServerProfile>) {
        if (list.map { it.id }.sorted() != _servers.value.map { it.id }.sorted()) return
        save(list)
    }

    fun setPinned(id: String, pinned: Boolean) = update(id) { it.copy(pinned = pinned) }

    /** Moves a server into [folder] (blank = out of any folder); it takes over the folder's pin. */
    fun setFolder(id: String, folder: String) =
        save(_servers.value.map { if (it.id == id) it.copy(folder = folder.trim(), folderPinned = false) else it }.withFolderPins())

    fun renameFolder(old: String, new: String) = save(
        _servers.value.map {
            if (it.folder == old) it.copy(folder = new.trim(), folderPinned = it.folderPinned && new.isNotBlank()) else it
        }.withFolderPins(),
    )

    /** Pins or unpins a whole folder. */
    fun setFolderPinned(folder: String, pinned: Boolean) =
        save(_servers.value.map { if (it.folder == folder) it.copy(folderPinned = pinned) else it })

    /** Removes the folder; its servers stay, without a folder. */
    fun removeFolder(name: String) = renameFolder(name, "")

    private fun update(id: String, change: (ServerProfile) -> ServerProfile) =
        save(_servers.value.map { if (it.id == id) change(it) else it })

    private fun load(): List<ServerProfile> {
        val encrypted = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(box.decrypt(encrypted))
            List(arr.length()) { ServerProfile.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<ServerProfile>) {
        _servers.value = list
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit { putString(KEY, box.encrypt(arr.toString())) }
    }

    private companion object {
        const val KEY = "servers.v2"
    }
}
