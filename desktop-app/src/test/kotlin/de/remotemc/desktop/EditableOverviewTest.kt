package de.remotemc.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.CardSize
import de.remotemc.app.data.DashCard
import de.remotemc.app.data.DashboardLayout
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class EditableOverviewTest {
    @Test
    fun cardsCanBeDraggedIntoANewOrder() {
        val defaults = DashboardLayout(listOf("a", "b", "c").map { DashCard(it, CardSize.FULL) })
        var saved: DashboardLayout? = null
        val scene = ImageComposeScene(800, 900, Density(1f)) {
            RemoteMCTheme(DesktopSettings()) {
                EditableOverview(defaults, defaults, onChange = { saved = it }, startEditing = true) { _ ->
                    { m -> McCard(m.height(120.dp)) { Box(Modifier) } }
                }
            }
        }
        var t = 0L
        fun frame() { t += 16_000_000; scene.render(t) }
        repeat(5) { frame() }
        // Edit bar ~80 px + top padding; card "a" starts around y = 110. Grab it on the left side.
        var y = 160f
        scene.sendPointerEvent(PointerEventType.Press, Offset(100f, y))
        frame()
        repeat(30) {
            y += 10f
            scene.sendPointerEvent(PointerEventType.Move, Offset(100f, y))
            frame()
        }
        scene.sendPointerEvent(PointerEventType.Release, Offset(100f, y))
        repeat(20) { frame() }
        assertEquals(listOf("b", "c", "a"), saved?.cards?.map { it.id })
        scene.close()
    }
}
