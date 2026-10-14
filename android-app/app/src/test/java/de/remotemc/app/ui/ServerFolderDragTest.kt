package de.remotemc.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.ui.theme.RemoteMCTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Holding a server and dropping it onto another one puts both into a folder. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerFolderDragTest {
    @get:Rule
    val rule = createComposeRule()

    private val start = listOf("Alpha", "Bravo", "Charlie").map { ServerProfile(id = it, name = it, host = "h", token = "t") }
    private var servers by mutableStateOf(start)

    private fun show() {
        // The status dots pulse forever: time only moves when the test says so.
        rule.mainClock.autoAdvance = false
        show2()
    }

    private fun show2() = rule.setContent {
        RemoteMCTheme {
            ServerListScreen(
                servers, {}, {}, { _, _ -> }, {}, {}, {},
                onReorder = { servers = it },
                probe = { throw java.io.IOException("offline") },
            )
        }
    }

    /** Holds [from] and drags it until its middle is over the middle of [onto], then lets go. */
    private fun dragOnto(from: String, onto: String, past: Float = 0f) {
        val a = rule.onNodeWithText(from).fetchSemanticsNode().boundsInRoot.center
        val b = rule.onNodeWithText(onto).fetchSemanticsNode().boundsInRoot.center
        val node = rule.onNodeWithText(from)
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1000) // holding starts the drag
        val steps = 48 // a finger moves in small steps
        repeat(steps) {
            node.performTouchInput { moveBy(Offset(0f, (b.y - a.y + past) / steps)) }
            rule.mainClock.advanceTimeBy(16)
        }
        rule.mainClock.advanceTimeBy(300)
        node.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1000)
    }

    private fun layout() = servers.joinToString(" ") { if (it.folder.isBlank()) it.id else "${it.id}@${it.folder}" }

    @Test
    fun dropOntoAServerInAFolderJoinsIt() {
        servers = listOf(
            ServerProfile(id = "Alpha", name = "Alpha", host = "h", token = "t"),
            ServerProfile(id = "Bravo", name = "Bravo", host = "h", token = "t", folder = "Team"),
            ServerProfile(id = "Charlie", name = "Charlie", host = "h", token = "t", folder = "Team"),
        )
        show()
        dragOnto("Alpha", "Charlie")
        assertEquals("Bravo@Team Charlie@Team Alpha@Team", layout())
    }

    @Test
    fun dragPastAServerSwapsInstead() {
        show()
        // Up to above the middle part of Alpha: the two swap, no folder.
        dragOnto("Charlie", "Alpha", past = -70f)
        assertEquals("Charlie Alpha Bravo", layout())
    }

    @Test
    fun dropOntoTheMiddleChangesNothingYet() {
        show()
        // Charlie passes Bravo (they swap) and stops over the middle of Alpha: nothing else moves,
        // the app asks for a folder name first.
        dragOnto("Charlie", "Alpha")
        assertEquals("Alpha Charlie Bravo", layout())
    }
}
