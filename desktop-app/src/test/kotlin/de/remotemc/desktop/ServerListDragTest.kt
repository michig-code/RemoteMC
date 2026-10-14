package de.remotemc.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.remotemc.app.data.ServerProfile
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dragging servers on the server list: past another one they swap, onto one in a folder it joins. */
@OptIn(ExperimentalTestApi::class)
class ServerListDragTest {
    private val dir = Files.createTempDirectory("rmc").toFile()
    private val model = AppModel(ServerStore(File(dir, "servers.dat")), SettingsStore(File(dir, "settings.json")), CommandHistory(File(dir, "h.json")), ServerExtras.inMemory())

    init {
        model.settings.update { it.copy(animations = false) }
    }

    private fun servers(vararg list: ServerProfile) = list.forEach { model.servers.upsert(it) }
    private fun s(name: String, folder: String = "") = ServerProfile(id = name, name = name, host = "h", token = "t", folder = folder)
    private fun layout() = model.servers.servers.value.joinToString(" ") { if (it.folder.isBlank()) it.id else "${it.id}@${it.folder}" }

    private fun drag(from: String, onto: String, past: Offset = Offset.Zero) = runDesktopComposeUiTest(width = 500, height = 1000) {
        // The cards check their server every few seconds forever: time only moves when the test says so.
        mainClock.autoAdvance = false
        setContent { RemoteMCTheme(model.settings.settings.value) { ServerListScreen(model, {}, {}, {}, probe = { error("offline") }) } }
        mainClock.advanceTimeBy(500)
        val a = onNodeWithText(from).fetchSemanticsNode().boundsInRoot.center
        val b = onNodeWithText(onto).fetchSemanticsNode().boundsInRoot.center
        val delta = b - a + past
        val node = onNodeWithText(from)
        node.performMouseInput {
            moveTo(center)
            press()
            val steps = 40
            repeat(steps) { moveBy(delta / steps.toFloat(), delayMillis = 16) }
            release()
        }
        mainClock.advanceTimeBy(1000)
    }

    @Test
    fun dragPastAServerSwaps() {
        servers(s("Alpha"), s("Bravo"), s("Charlie"))
        drag("Charlie", "Alpha", past = Offset(0f, -60f))
        assertEquals("Charlie Alpha Bravo", layout())
    }

    @Test
    fun dropOntoAServerInAFolderJoinsIt() {
        servers(s("Alpha"), s("Bravo", "Team"), s("Charlie", "Team"))
        drag("Alpha", "Charlie")
        assertEquals("Bravo@Team Charlie@Team Alpha@Team", layout())
    }
}
