package de.remotemc.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.ui.theme.RemoteMCTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Holding a server and dropping it onto another one asks for a folder name. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
class ServerFolderDragTest {
    @get:Rule
    val rule = createComposeRule()

    private val start = listOf("Alpha", "Bravo", "Charlie").map { ServerProfile(id = it, name = it, host = "h", token = "t") }
    private var servers by mutableStateOf(start)

    private fun show() = rule.setContent {
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
        val steps = 12
        repeat(steps) {
            node.performTouchInput { moveBy(Offset(0f, (b.y - a.y + past) / steps)) }
            rule.mainClock.advanceTimeBy(50)
        }
        rule.mainClock.advanceTimeBy(300)
        node.performTouchInput { up() }
        rule.waitForIdle()
    }

    private fun layout() = servers.joinToString(" ") { if (it.folder.isBlank()) it.id else "${it.id}@${it.folder}" }

    @Test
    fun dropOntoAServerAndNameTheFolder() {
        show()
        dragOnto("Charlie", "Alpha")
        rule.onNodeWithText("What should the folder be called?").assertExists()
        rule.onNode(hasSetTextAction()).performTextInput("Friends")
        rule.onNodeWithText("Create").performClick()
        rule.waitForIdle()
        assertEquals("Alpha@Friends Charlie@Friends Bravo", layout())
    }

    @Test
    fun zzDebugIdle() {
        show()
        dragOnto("Charlie", "Alpha")
        println("DEBUG after " + layout() + " dialog=" + rule.onAllNodesWithText("What should the folder be called?").fetchSemanticsNodes().size)
    }

    @Test
    fun cancelPutsTheServerBack() {
        show()
        dragOnto("Charlie", "Alpha")
        rule.onNodeWithText("What should the folder be called?").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertEquals("Alpha Bravo Charlie", layout())
    }
}
