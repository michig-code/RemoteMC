package de.remotemc.app.ui

import de.remotemc.app.data.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerDropTest {

    private fun server(name: String, folder: String = "", pinned: Boolean = false) =
        ServerProfile(id = name, name = name, host = "h", token = "t", folder = folder, pinned = pinned)

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

    private fun Drop.layout() = servers.joinToString(" ") { s ->
        s.id + when (val sec = s.section) {
            Section.Pinned -> "^"
            Section.Loose -> ""
            is Section.InFolder -> "@" + sec.name
        }
    }

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
        assertEquals("P L1 L2 A1@A A2@A B1@B", dropped("P", "title:${de.remotemc.app.R.string.servers_title}").layout())

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
}
