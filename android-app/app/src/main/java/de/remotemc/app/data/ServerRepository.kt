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

    fun move(from: Int, to: Int) {
        val list = _servers.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        save(list)
    }

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
