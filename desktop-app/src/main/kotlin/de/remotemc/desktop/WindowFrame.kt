package de.remotemc.desktop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Cursor
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window

/**
 * Window buttons for the undecorated window. Maximizing fills the screen without covering the
 * taskbar (a plain maximized undecorated window would hide it).
 */
class AwtWindowControls(
    private val scope: FrameWindowScope,
    private val onClose: () -> Unit,
    private val quit: () -> Unit,
) : WindowControls {
    override var maximized by mutableStateOf(false)
        private set
    private var restore: Rectangle? = null

    override fun minimize() {
        scope.window.extendedState = scope.window.extendedState or Frame.ICONIFIED
    }

    override fun toggleMaximize() {
        val w = scope.window
        if (maximized) {
            restore?.let { w.bounds = it }
            maximized = false
        } else {
            restore = w.bounds
            val config = w.graphicsConfiguration
            val screen = config.bounds
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
            w.bounds = Rectangle(
                screen.x + insets.left, screen.y + insets.top,
                screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom,
            )
            maximized = true
        }
    }

    override fun close() = onClose()

    override fun quit() = quit.invoke()

    @Composable
    override fun DragArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        with(scope) {
            WindowDraggableArea(modifier) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

/** Invisible borders around the undecorated window for resizing it with the mouse. */
@Composable
fun ResizeEdges(window: Window, thickness: androidx.compose.ui.unit.Dp = 5.dp) {
    Box(Modifier.fillMaxSize()) {
        Edge(window, Modifier.align(Alignment.CenterStart).width(thickness).fillMaxHeight(), Cursor.W_RESIZE_CURSOR, left = true)
        Edge(window, Modifier.align(Alignment.CenterEnd).width(thickness).fillMaxHeight(), Cursor.E_RESIZE_CURSOR, right = true)
        Edge(window, Modifier.align(Alignment.BottomCenter).height(thickness).fillMaxWidth(), Cursor.S_RESIZE_CURSOR, bottom = true)
        Edge(window, Modifier.align(Alignment.TopCenter).height(3.dp).fillMaxWidth(), Cursor.N_RESIZE_CURSOR, top = true)
        Edge(window, Modifier.align(Alignment.BottomEnd).size(thickness * 3), Cursor.SE_RESIZE_CURSOR, right = true, bottom = true)
        Edge(window, Modifier.align(Alignment.BottomStart).size(thickness * 3), Cursor.SW_RESIZE_CURSOR, left = true, bottom = true)
    }
}

@Composable
private fun Edge(
    window: Window,
    modifier: Modifier,
    cursor: Int,
    left: Boolean = false,
    right: Boolean = false,
    top: Boolean = false,
    bottom: Boolean = false,
) {
    Box(
        modifier.pointerHoverIcon(PointerIcon(Cursor(cursor))).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                // Screen coordinates: the window moves under the mouse while resizing.
                val start = MouseInfo.getPointerInfo().location
                val bounds = window.bounds
                val min = window.minimumSize
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                    event.changes.forEach { it.consume() }
                    val now = MouseInfo.getPointerInfo()?.location ?: continue
                    val dx = now.x - start.x
                    val dy = now.y - start.y
                    var x = bounds.x
                    var y = bounds.y
                    var w = bounds.width
                    var h = bounds.height
                    if (right) w = maxOf(min.width, bounds.width + dx)
                    if (bottom) h = maxOf(min.height, bounds.height + dy)
                    if (left) {
                        w = maxOf(min.width, bounds.width - dx)
                        x = bounds.x + bounds.width - w
                    }
                    if (top) {
                        h = maxOf(min.height, bounds.height - dy)
                        y = bounds.y + bounds.height - h
                    }
                    window.setBounds(x, y, w, h)
                }
            }
        },
    )
}
