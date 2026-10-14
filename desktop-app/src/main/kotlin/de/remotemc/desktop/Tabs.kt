package de.remotemc.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope

/**
 * Everything opens as a tab in the title bar: servers (by id), the settings ([SETTINGS]) and
 * adding a server ([PAIR]). Each open server keeps its connection (console, chat, status) while
 * another tab is shown; closing the tab ends it. [active] null = the server list.
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
    /** remotemc:// link to start the "add server" tab with (pasted or from the clipboard). */
    var pairLink by mutableStateOf<String?>(null)
        private set
    /** An area to show in a server tab once it is open (e.g. the chat after clicking a mention). */
    var paneRequest by mutableStateOf<Pair<String, Pane>?>(null)

    /** Server to pair again in the "add server" tab (keeps its name, folder and place). */
    var pairRepair by mutableStateOf<String?>(null)
        private set

    init {
        if (persist) {
            SplitPrefs.text("tabs").split(',').filter { it == SETTINGS || (it.isNotBlank() && model.servers.get(it) != null) }.distinct().forEach {
                open += it
                session(it)
            }
        }
    }

    /** Opens (or shows) the settings or the "add server" tab. */
    fun openSpecial(key: String, link: String? = null, repair: String? = null) {
        if (key == PAIR) {
            pairLink = link
            pairRepair = repair
        }
        if (key !in open) open += key
        active = key
        save()
    }

    /** Shows the server, opening a tab for it if needed. */
    fun open(id: String) {
        if (isSpecial(id)) return openSpecial(id)
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

    /** Reconnects an open tab, e.g. after the server was paired again (new token). */
    fun restart(id: String) {
        sessions.remove(id)?.stop() ?: return
        session(id)
    }

    /** The live connection of an open tab (started on first use). */
    fun session(id: String): ServerSession? {
        if (isSpecial(id)) return null
        sessions[id]?.let { return it }
        val profile = model.servers.get(id) ?: return null
        return ServerSession(profile, scope, model.servers, model.history, model.extras).also {
            sessions[id] = it
            if (connect) it.start()
        }
    }

    /** Closes tabs of servers that were removed from the list. */
    fun prune() {
        open.filter { !isSpecial(it) && model.servers.get(it) == null }.forEach(::close)
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.clear()
    }

    private fun save() {
        // The "add server" tab is not worth bringing back after a restart.
        if (persist) SplitPrefs.setText("tabs", open.filter { it != PAIR }.joinToString(","))
    }

    companion object {
        const val SETTINGS = "@settings"
        const val PAIR = "@pair"
        const val HELP = "@help"
        fun isSpecial(key: String) = key.startsWith("@")
    }
}
