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

/** Tabs can be dragged sideways into a new order, like in a browser. */
@OptIn(ExperimentalTestApi::class)
class TabDragTest {
    private val dir = Files.createTempDirectory("rmc").toFile()
    private val model = AppModel(ServerStore(File(dir, "servers.dat")), SettingsStore(File(dir, "settings.json")), CommandHistory(File(dir, "h.json")), ServerExtras.inMemory())
    private val window = object : WindowControls {
        override val maximized = false
        override fun minimize() {}
        override fun toggleMaximize() {}
        override fun close() {}
        var moved = false
        override fun dragStart() { moved = true }
        @Composable
        override fun DragArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit) = Box(modifier, content = content)
    }

    @Test
    fun dragATabPastTheNextOne() = runDesktopComposeUiTest(width = 1000, height = 200) {
        listOf("Alpha", "Bravo", "Charlie").forEach { model.servers.upsert(ServerProfile(id = it, name = it, host = "h", token = "t")) }
        val tabs = Tabs(model, CoroutineScope(Dispatchers.Unconfined), persist = false, connect = false)
        listOf("Alpha", "Bravo", "Charlie").forEach(tabs::open)
        mainClock.autoAdvance = false
        setContent { RemoteMCTheme(model.settings.settings.value) { TitleBar(model, tabs, window) } }
        mainClock.advanceTimeBy(300)
        val a = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center
        val c = onNodeWithText("Charlie").fetchSemanticsNode().boundsInRoot.center
        onNodeWithText("Alpha").performMouseInput {
            moveTo(center)
            press()
            repeat(30) { moveBy(Offset((c.x - a.x + 60f) / 30, 0f), delayMillis = 16) }
            release()
        }
        mainClock.advanceTimeBy(300)
        assertEquals(listOf("Bravo", "Charlie", "Alpha"), tabs.open.toList())
        assertEquals(false, window.moved) // moving a tab does not move the window
    }
}
