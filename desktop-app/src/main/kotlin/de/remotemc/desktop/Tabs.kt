package de.remotemc.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope

/**
 * Servers open as tabs in the title bar. Each open server keeps its connection (console, chat,
 * status) while another tab is shown; closing the tab ends it. [active] null = the server list.
 */
class Tabs(
    private val model: AppModel,
    private val scope: CoroutineScope,
    private val persist: Boolean = true,
    private val connect: Boolean = true, // screenshot tests show tabs without connecting
) {
    val open = mutableStateListOf<String>()
    var active by mutableStateOf<String?>(null)
        private set
    private val sessions = mutableMapOf<String, ServerSession>()

    init {
        if (persist) {
            SplitPrefs.text("tabs").split(',').filter { it.isNotBlank() && model.servers.get(it) != null }.distinct().forEach {
                open += it
                session(it)
            }
        }
    }

    /** Shows the server, opening a tab for it if needed. */
    fun open(id: String) {
        if (model.servers.get(id) == null) return
        if (id !in open) open += id
        session(id)
        active = id
        save()
    }

    fun showHome() {
        active = null
    }

    fun close(id: String) {
        val index = open.indexOf(id)
        if (index < 0) return
        open.removeAt(index)
        sessions.remove(id)?.stop()
        if (active == id) active = open.getOrNull(index) ?: open.getOrNull(index - 1)
        save()
    }

    /** Next tab (the server list counts as the first one), for Ctrl+Tab. */
    fun next(step: Int = 1) {
        val all = listOf<String?>(null) + open
        val i = all.indexOf(active).coerceAtLeast(0)
        active = all[(i + step + all.size) % all.size]
    }

    fun move(from: Int, to: Int) {
        if (from !in open.indices || to !in open.indices) return
        open.add(to, open.removeAt(from))
        save()
    }

    fun existing(id: String): ServerSession? = sessions[id]

    /** The live connection of an open tab (started on first use). */
    fun session(id: String): ServerSession? {
        sessions[id]?.let { return it }
        val profile = model.servers.get(id) ?: return null
        return ServerSession(profile, scope, model.servers, model.history).also {
            sessions[id] = it
            if (connect) it.start()
        }
    }

    /** Closes tabs of servers that were removed from the list. */
    fun prune() {
        open.filter { model.servers.get(it) == null }.forEach(::close)
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.clear()
    }

    private fun save() {
        if (persist) SplitPrefs.setText("tabs", open.joinToString(","))
    }
}
