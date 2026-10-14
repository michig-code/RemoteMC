package de.remotemc.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.remotemc.app.data.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Dragging a server tab down into the window docks it next to the active tab. */
@OptIn(ExperimentalTestApi::class)
class DockTest {
    private val dir = Files.createTempDirectory("rmc").toFile()
    private val model = AppModel(ServerStore(File(dir, "servers.dat")), SettingsStore(File(dir, "settings.json")), CommandHistory(File(dir, "h.json")), ServerExtras.inMemory())
    private val window = object : WindowControls {
        override val maximized = false
        override fun minimize() {}
        override fun toggleMaximize() {}
        override fun close() {}
        @Composable
        override fun DragArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit) = Box(modifier, content = content)
    }

    init {
        SplitPrefs.save = false
        model.settings.update { it.copy(animations = false, welcomed = true) }
    }

    @Test
    fun dragATabToTheRightDocksIt() = runDesktopComposeUiTest(width = 1200, height = 800) {
        listOf("Alpha", "Bravo").forEach { model.servers.upsert(ServerProfile(id = it, name = it, host = "h", token = "t")) }
        val tabs = Tabs(model, CoroutineScope(Dispatchers.Unconfined), persist = false, connect = false)
        tabs.open("Alpha")
        tabs.open("Bravo")
        mainClock.autoAdvance = false
        setContent { App(model, tabs = tabs, controls = window) }
        mainClock.advanceTimeBy(500)
        val from = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center
        val target = Offset(1100f, 400f) // right edge of the window
        onNodeWithText("Alpha").performMouseInput {
            moveTo(center)
            press()
            repeat(30) { moveBy((target - from) / 30f, delayMillis = 16) }
            release()
        }
        mainClock.advanceTimeBy(500)
        assertEquals("Alpha" to DockSide.RIGHT, tabs.docked)
        assertNull(tabs.dockDrag)
        assertEquals("Bravo", tabs.active)
    }
}
