package de.remotemc.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerListTest {

    private fun server(name: String, folder: String = "", pinned: Boolean = false, folderPinned: Boolean = false) =
        ServerProfile(id = name, name = name, host = "h", token = "t", folder = folder, pinned = pinned, folderPinned = folderPinned)

    private val servers = listOf(
        server("P", pinned = true),
        server("L1"), server("L2"),
        server("A1", "A"), server("A2", "A"),
        server("B1", "B"),
    )

    private fun dropped(id: String, target: String, collapsed: List<String> = emptyList()): Drop {
        val rows = buildRows(servers, "", collapsed)
        return drop(servers, rows, id, target, collapsed)!!
    }

    private fun Drop.layout() = servers.layout()

    private fun List<ServerProfile>.layout() = joinToString(" ") { s ->
        s.id + when (val sec = s.section) {
            Section.Pinned -> "^"
            Section.Loose -> ""
            is Section.InFolder -> "@" + sec.name + if (s.folderPinned) "^" else ""
        }
    }

    private fun List<ListRow>.keys() = joinToString(" ") { it.key }

    @Test
    fun intoFolderFromAbove() =
        assertEquals("P^ L1 L2@A A1@A A2@A B1@B", dropped("L2", "folder:A").layout())

    @Test
    fun outOfFolderUpwards() =
        assertEquals("P^ L1 L2 A1 A2@A B1@B", dropped("A1", "folder:A").layout())

    @Test
    fun fromFolderToFolder() =
        assertEquals("P^ L1 L2 A1@A A2@B B1@B", dropped("A2", "folder:B").layout())

    @Test
    fun intoPinned() =
        assertEquals("L1^ P^ L2 A1@A A2@A B1@B", dropped("L1", "P").layout())

    @Test
    fun outOfPinned() =
        assertEquals("P L1 L2 A1@A A2@A B1@B", dropped("P", "title:servers").layout())

    @Test
    fun withinSection() =
        assertEquals("P^ L2 L1 A1@A A2@A B1@B", dropped("L1", "L2").layout())

    @Test
    fun intoClosedFolderOpensIt() {
        val drop = dropped("A2", "folder:B", collapsed = listOf("B"))
        assertEquals("P^ L1 L2 A1@A A2@B B1@B", drop.layout())
        assertEquals("B", drop.expand)
    }

    @Test
    fun closedFolderMembersAreKept() {
        val drop = dropped("L1", "L2", collapsed = listOf("A"))
        assertEquals("P^ L2 L1 A1@A A2@A B1@B", drop.layout())
        assertNull(drop.expand)
    }

    @Test
    fun pinnedFolderIsListedOnTop() {
        val list = servers.map { if (it.folder == "B") it.copy(folderPinned = true) else it }
        assertEquals(
            "title:pinned P folder:B B1 title:servers L1 L2 folder:A A1 A2",
            buildRows(list, "", emptyList()).keys(),
        )
    }

    @Test
    fun serverDroppedIntoPinnedFolderTakesItsPin() {
        val list = servers.map { if (it.folder == "B") it.copy(folderPinned = true) else it }
        val rows = buildRows(list, "", emptyList())
        // L1 dragged onto B1 (the row below the pinned folder's header) joins folder B.
        val drop = drop(list, rows, "L1", "B1", emptyList())!!
        assertEquals("P^ L1@B^ B1@B^ L2 A1@A A2@A", drop.layout())
    }

    @Test
    fun leavingAPinnedFolderDropsThePin() {
        val list = servers.map { if (it.folder == "A") it.copy(folderPinned = true) else it }
        val rows = buildRows(list, "", emptyList())
        val drop = drop(list, rows, "A2", "L2", emptyList())!!
        assertEquals("P^ A1@A^ L1 L2 A2 B1@B", drop.layout())
    }

    @Test
    fun droppingOntoAServerMakesAFolder() {
        assertNull(folderOf(servers, "L2"))
        // The new folder takes L2's place.
        assertEquals("P^ L2@New L1@New A1@A A2@A B1@B", mergeInto(servers, "L1", "L2", " New ").layout())
    }

    @Test
    fun droppingOntoAPinnedServerMakesAPinnedFolder() =
        assertEquals("P@Top^ L1@Top^ L2 A1@A A2@A B1@B", mergeInto(servers, "L1", "P", "Top").layout())

    @Test
    fun droppingOntoAServerInAFolderJoinsIt() {
        assertEquals("A", folderOf(servers, "A1"))
        assertEquals("P^ L2 A1@A L1@A A2@A B1@B", mergeInto(servers, "L1", "A1", "A").layout())
    }

    @Test
    fun blankNameChangesNothing() = assertEquals(servers, mergeInto(servers, "L1", "L2", "  "))
}
