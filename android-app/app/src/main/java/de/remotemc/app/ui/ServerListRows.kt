package de.remotemc.app.ui

import de.remotemc.app.R
import de.remotemc.app.data.ServerProfile

/** Where a server is listed. */
internal sealed interface Section {
    data object Pinned : Section
    data object Loose : Section
    data class InFolder(val name: String) : Section
}

internal val ServerProfile.section: Section
    get() = when {
        pinned -> Section.Pinned
        folder.isBlank() -> Section.Loose
        else -> Section.InFolder(folder)
    }

/** The server moved into [section]; pinning keeps the folder so unpinning puts it back there. */
internal fun ServerProfile.movedTo(section: Section) = when (section) {
    Section.Pinned -> copy(pinned = true)
    Section.Loose -> copy(pinned = false, folder = "")
    is Section.InFolder -> copy(pinned = false, folder = section.name)
}

/** One row of the server list. */
internal sealed interface ListRow {
    val key: String
    val section: Section

    data class Title(val text: Int, override val section: Section) : ListRow {
        override val key get() = "title:$text"
    }
    data class Folder(val name: String, val count: Int, val expanded: Boolean) : ListRow {
        override val key get() = "folder:$name"
        override val section get() = Section.InFolder(name)
    }
    data class Server(val server: ServerProfile) : ListRow {
        override val key get() = server.id
        override val section get() = server.section
    }
}

/**
 * Pinned servers first, then servers without a folder, then each folder (in the order of its first server).
 * "My servers" is shown whenever there are other sections, so servers can be dragged out of a folder.
 */
internal fun buildRows(servers: List<ServerProfile>, query: String, collapsed: List<String>): List<ListRow> {
    if (query.isNotBlank()) return servers.filter { it.matches(query) }.map { ListRow.Server(it) }
    val rows = mutableListOf<ListRow>()
    val pinned = servers.filter { it.pinned }
    if (pinned.isNotEmpty()) {
        rows += ListRow.Title(R.string.servers_pinned, Section.Pinned)
        pinned.mapTo(rows) { ListRow.Server(it) }
    }
    val loose = servers.filter { it.section == Section.Loose }
    val folders = servers.filter { it.section is Section.InFolder }.groupBy { it.folder }
    if (loose.isNotEmpty() || folders.isNotEmpty()) {
        rows += ListRow.Title(R.string.servers_title, Section.Loose)
        loose.mapTo(rows) { ListRow.Server(it) }
    }
    folders.forEach { (folder, items) ->
        val expanded = folder !in collapsed
        rows += ListRow.Folder(folder, items.size, expanded)
        if (expanded) items.mapTo(rows) { ListRow.Server(it) }
    }
    return rows
}

/** Result of dropping a dragged server: the new order of all servers and a folder to open, if any. */
internal data class Drop(val servers: List<ServerProfile>, val expand: String?)

/**
 * Moves server [id] to the row of [targetKey] (the row it is dragged over). The server joins the section
 * of the row above its new place: a server, a folder header or a section title. This puts it into
 * folders, out of them, from folder to folder or into "Pinned".
 */
internal fun drop(servers: List<ServerProfile>, rows: List<ListRow>, id: String, targetKey: Any?, collapsed: List<String>): Drop? {
    val from = rows.indexOfFirst { it.key == id }
    val to = rows.indexOfFirst { it.key == targetKey }
    if (from < 0 || to < 0 || from == to) return null
    val dragged = (rows[from] as ListRow.Server).server
    val virtual = rows.toMutableList().apply { add(to, removeAt(from)) }
    val anchor = virtual.getOrNull(to - 1) ?: return null // nothing above the first title
    val moved = dragged.movedTo(anchor.section)

    // New order of all servers: as the rows show them, plus the members of collapsed folders.
    val out = mutableListOf<ServerProfile>()
    var placed = false
    virtual.forEachIndexed { i, row ->
        when (row) {
            is ListRow.Server -> when {
                row.server.id != id -> out += row.server
                !placed -> out += moved
            }
            is ListRow.Folder -> if (!row.expanded) {
                val draggedNext = (virtual.getOrNull(i + 1) as? ListRow.Server)?.server?.id == id
                if (draggedNext && moved.section == row.section) {
                    // Dropped right below a closed folder: it goes in first, the folder opens.
                    out += moved
                    placed = true
                }
                out += servers.filter { it.id != id && it.section == row.section }
            }
            is ListRow.Title -> Unit
        }
    }
    val seen = out.mapTo(HashSet()) { it.id }
    servers.filterTo(out) { it.id !in seen } // nothing may get lost
    val folder = (moved.section as? Section.InFolder)?.name
    return Drop(out, folder?.takeIf { it in collapsed })
}
