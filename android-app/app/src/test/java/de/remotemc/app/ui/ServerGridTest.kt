package de.remotemc.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.ui.theme.RemoteMCTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cards of the server list get a blue frame whose handles change their size in grid cells. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerGridTest {
    @get:Rule
    val rule = createComposeRule()

    private val servers = listOf("Alpha", "Bravo").map { ServerProfile(id = it, name = it, host = "h", token = "t") }
    private val sizes = mutableListOf<String>()

    private fun show(editing: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            RemoteMCTheme {
                ServerListScreen(
                    servers, {}, {}, { _, _ -> }, {}, {}, {},
                    probe = { throw java.io.IOException("offline") },
                    onResize = { s, w, h -> sizes += "${s.id} ${w}x$h" },
                    startEditing = editing,
                )
            }
        }
        rule.mainClock.advanceTimeBy(500)
    }

    /** Drags the [index]th "Change size" handle by [by] in small steps. */
    private fun dragHandle(index: Int, by: Offset) {
        val handle = rule.onAllNodesWithContentDescription("Change size")[index]
        handle.performTouchInput { down(center) }
        repeat(20) {
            handle.performTouchInput { moveBy(by / 20f) }
            rule.mainClock.advanceTimeBy(16)
        }
        handle.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
    }

    @Test
    fun rightHandleMakesTheCardNarrower() {
        show(editing = true)
        // Each card has three handles (right, bottom, corner); a cell is about 90 dp wide here.
        val cell = rule.onNodeWithText("Alpha").fetchSemanticsNode().let { 90f * it.layoutInfo.density.density }
        dragHandle(0, Offset(-2 * cell, 0f))
        assertEquals(listOf("Alpha 2x2"), sizes)
    }

    @Test
    fun cornerHandleChangesWidthAndHeight() {
        show(editing = true)
        val cell = rule.onNodeWithText("Alpha").fetchSemanticsNode().let { 90f * it.layoutInfo.density.density }
        dragHandle(2, Offset(-3 * cell, 76f * 1.2f * rule.density.density))
        assertTrue(sizes.single(), sizes.single().startsWith("Alpha 1x"))
    }

    @Test
    fun holdingACardWithoutMovingShowsItsFrame() {
        show(editing = false)
        assertEquals(0, rule.onAllNodesWithContentDescription("Change size").fetchSemanticsNodes().size)
        val card = rule.onNodeWithText("Bravo")
        card.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1000)
        card.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(3, rule.onAllNodesWithContentDescription("Change size").fetchSemanticsNodes().size)
    }
}
