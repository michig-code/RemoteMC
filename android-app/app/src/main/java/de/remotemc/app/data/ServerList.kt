package de.remotemc.app.data

/*
 * How the server list is arranged: pinned servers and folders on top, then the other servers and
 * folders. Shared by the phone and the Windows app.
 */

/** The two section titles of the list. */
enum class ListTitle { PINNED, SERVERS }

/** Where a server is listed. */
sealed interface Section {
    data object Pinned : Section
    data object Loose : Section
    data class InFolder(val name: String) : Section
}

val ServerProfile.section: Section
    get() = when {
        pinned -> Section.Pinned
        folder.isBlank() -> Section.Loose
        else -> Section.InFolder(folder)
    }

/** The server moved into [section]; pinning keeps the folder so unpinning puts it back there. */
fun ServerProfile.movedTo(section: Section) = when (section) {
    Section.Pinned -> copy(pinned = true)
    Section.Loose -> copy(pinned = false, folder = "", folderPinned = false)
    // Whether the folder is pinned comes from its other servers (see [withFolderPins]).
    is Section.InFolder -> copy(pinned = false, folder = section.name, folderPinned = false)
}

/** One row of the server list. */
sealed interface ListRow {
    val key: String
    val section: Section

    data class Title(val title: ListTitle, override val section: Section) : ListRow {
        override val key get() = "title:${title.name.lowercase()}"
    }
    data class Folder(val name: String, val count: Int, val expanded: Boolean, val pinned: Boolean = false) : ListRow {
        override val key get() = "folder:$name"
        override val section get() = Section.InFolder(name)
    }
    data class Server(val server: ServerProfile) : ListRow {
        override val key get() = server.id
        override val section get() = server.section
    }
}

/** Folders whose servers are listed on top ("Pinned"). */
fun pinnedFolders(servers: List<ServerProfile>): Set<String> =
    servers.filter { it.section is Section.InFolder && it.folderPinned }.mapTo(LinkedHashSet()) { it.folder }

/**
 * Makes every server of a folder agree on whether the folder is pinned: it is pinned when one of
 * its servers says so. Servers outside a folder are never "in a pinned folder".
 */
fun List<ServerProfile>.withFolderPins(): List<ServerProfile> {
    val pinned = pinnedFolders(this)
    return map {
        val want = it.folder.isNotBlank() && it.folder in pinned
        if (it.folderPinned != want) it.copy(folderPinned = want) else it
    }
}

/**
 * Pinned servers and pinned folders first, then servers without a folder, then the other folders
 * (each in the order of its first server). "My servers" is shown whenever there is anything else
 * in the list, so servers can be dragged out of a folder or out of "Pinned".
 */
fun buildRows(servers: List<ServerProfile>, query: String, collapsed: Collection<String>): List<ListRow> {
    if (query.isNotBlank()) return servers.filter { it.matches(query) }.map { ListRow.Server(it) }
    val rows = mutableListOf<ListRow>()
    val folders = servers.filter { it.section is Section.InFolder }.groupBy { it.folder }
    val pinnedFolders = pinnedFolders(servers)
    fun addFolder(name: String, items: List<ServerProfile>) {
        val expanded = name !in collapsed
        rows += ListRow.Folder(name, items.size, expanded, pinned = name in pinnedFolders)
        if (expanded) items.mapTo(rows) { ListRow.Server(it) }
    }
    val pinned = servers.filter { it.pinned }
    if (pinned.isNotEmpty() || pinnedFolders.isNotEmpty()) {
        rows += ListRow.Title(ListTitle.PINNED, Section.Pinned)
        pinned.mapTo(rows) { ListRow.Server(it) }
        folders.filterKeys { it in pinnedFolders }.forEach { (name, items) -> addFolder(name, items) }
    }
    val loose = servers.filter { it.section == Section.Loose }
    val others = folders.filterKeys { it !in pinnedFolders }
    if (loose.isNotEmpty() || others.isNotEmpty() || rows.isNotEmpty()) {
        rows += ListRow.Title(ListTitle.SERVERS, Section.Loose)
        loose.mapTo(rows) { ListRow.Server(it) }
        others.forEach { (name, items) -> addFolder(name, items) }
    }
    return rows
}

/** Result of dropping a dragged server: the new order of all servers and a folder to open, if any. */
data class Drop(val servers: List<ServerProfile>, val expand: String?)

/**
 * Moves server [id] to the row of [targetKey] (the row it is dragged over). The server joins the section
 * of the row above its new place: a server, a folder header or a section title. This puts it into
 * folders, out of them, from folder to folder or into "Pinned".
 */
fun drop(servers: List<ServerProfile>, rows: List<ListRow>, id: String, targetKey: Any?, collapsed: Collection<String>): Drop? {
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
    return Drop(out.withFolderPins(), folder?.takeIf { it in collapsed })
}

/** The folder a server dropped onto [targetId] joins without asking: the target's own folder, if any. */
fun folderOf(servers: List<ServerProfile>, targetId: String): String? =
    servers.firstOrNull { it.id == targetId }?.takeIf { it.section is Section.InFolder }?.folder

/**
 * Server [id] dropped onto server [targetId]: both go into [folder], at the target's place. A new
 * folder made from a pinned server stays on top (pinned); an existing folder keeps its pin.
 */
fun mergeInto(servers: List<ServerProfile>, id: String, targetId: String, folder: String): List<ServerProfile> {
    val name = folder.trim()
    val dragged = servers.firstOrNull { it.id == id } ?: return servers
    val target = servers.firstOrNull { it.id == targetId } ?: return servers
    if (name.isEmpty() || id == targetId) return servers
    val pin = target.pinned || name in pinnedFolders(servers)
    val list = servers.filter { it.id != id }.toMutableList()
    val at = list.indexOfFirst { it.id == targetId }
    list[at] = target.copy(pinned = false, folder = name)
    list.add(at + 1, dragged.copy(pinned = false, folder = name))
    return list.map { if (it.folder == name) it.copy(folderPinned = pin) else it }.withFolderPins()
}
