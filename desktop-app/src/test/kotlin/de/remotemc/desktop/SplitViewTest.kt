package de.remotemc.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class SplitViewTest {
    @Test
    fun dividerCanBeDragged() {
        SplitPrefs.save = false
        // 1016 px wide: 1000 px for the two sides plus the 16 px handle; the handle starts at 500.
        val scene = ImageComposeScene(1016, 400, Density(1f)) {
            SplitView("test-split", 0.5f, Modifier.fillMaxSize(), left = { Box(Modifier.fillMaxSize()) }, right = { Box(Modifier.fillMaxSize()) })
        }
        scene.render()
        var x = 508f
        scene.sendPointerEvent(PointerEventType.Press, Offset(x, 200f))
        repeat(10) {
            x -= 20f
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, 200f))
            scene.render()
        }
        scene.sendPointerEvent(PointerEventType.Release, Offset(x, 200f))
        scene.render()
        val f = SplitPrefs.get("test-split", 0.5f)
        assertTrue(f in 0.29f..0.31f, "moved 200 px of 1000 to the left: $f")

        // Never narrower than the minimum (240 px of 1000).
        scene.sendPointerEvent(PointerEventType.Press, Offset(308f, 200f))
        scene.sendPointerEvent(PointerEventType.Move, Offset(10f, 200f))
        scene.render()
        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 200f))
        scene.render()
        assertEquals(0.24f, SplitPrefs.get("test-split", 0.5f), 0.001f)
        scene.close()
    }
}
