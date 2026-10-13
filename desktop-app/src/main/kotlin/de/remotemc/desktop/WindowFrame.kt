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

/** Where a window dropped at the screen edge snaps to, like Aero Snap; null = it stays put. */
enum class Snap { MAXIMIZE, LEFT, RIGHT }

/** Snap for a drag that ended with the mouse at [mouse] on a screen whose usable area is [work]. */
fun snapFor(mouse: java.awt.Point, work: Rectangle, edge: Int = 6): Snap? = when {
    mouse.y <= work.y + edge -> Snap.MAXIMIZE
    mouse.x <= work.x + edge -> Snap.LEFT
    mouse.x >= work.x + work.width - 1 - edge -> Snap.RIGHT
    else -> null
}

fun snapBounds(snap: Snap, work: Rectangle): Rectangle = when (snap) {
    Snap.MAXIMIZE -> Rectangle(work)
    Snap.LEFT -> Rectangle(work.x, work.y, work.width / 2, work.height)
    Snap.RIGHT -> Rectangle(work.x + work.width - work.width / 2, work.y, work.width / 2, work.height)
}

/** Bounds [t] (0..1, eased) of the way from [from] to [to]. */
fun lerpBounds(from: Rectangle, to: Rectangle, t: Float): Rectangle {
    val e = 1f - (1f - t.coerceIn(0f, 1f)).let { it * it * it } // ease out, like Windows
    fun mix(a: Int, b: Int) = (a + (b - a) * e).toInt()
    return Rectangle(mix(from.x, to.x), mix(from.y, to.y), mix(from.width, to.width), mix(from.height, to.height))
}

/**
 * Window buttons for the undecorated window, with Windows-like motion: maximizing and restoring
 * glide, minimizing shrinks and fades out (and fades back in), dropping the window at the top or a
 * side edge of the screen snaps it (Aero Snap). Maximized windows leave the taskbar visible.
 */
class AwtWindowControls(
    private val scope: FrameWindowScope,
    private val onClose: () -> Unit,
    private val quit: () -> Unit,
    private val animations: () -> Boolean = { true },
) : WindowControls {
    override var maximized by mutableStateOf(false)
        private set
    /** Size and place before maximizing or snapping, for restoring. */
    private var restore: Rectangle? = null
    private var timer: javax.swing.Timer? = null
    private val window get() = scope.window

    init {
        // Coming back from the taskbar fades in again.
        window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowDeiconified(e: java.awt.event.WindowEvent) {
                if (animations()) fade(from = 0f, to = 1f, millis = 160) else setOpacity(1f)
            }
        })
    }

    /** Usable area of the screen the window is on (without the taskbar). */
    private fun workArea(): Rectangle {
        val config = window.graphicsConfiguration
        val screen = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        return Rectangle(
            screen.x + insets.left, screen.y + insets.top,
            screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom,
        )
    }

    private fun setOpacity(value: Float) {
        runCatching { window.opacity = value.coerceIn(0f, 1f) }
    }

    /** Runs [step] with 0..1 over [millis] on the UI thread, then [done]. */
    private fun animate(millis: Int, step: (Float) -> Unit, done: () -> Unit = {}) {
        timer?.stop()
        if (!animations()) {
            step(1f)
            done()
            return
        }
        val start = System.nanoTime()
        timer = javax.swing.Timer(8) { e ->
            val t = ((System.nanoTime() - start) / 1_000_000f / millis).coerceAtMost(1f)
            step(t)
            if (t >= 1f) {
                (e.source as javax.swing.Timer).stop()
                done()
            }
        }.apply { start() }
    }

    private fun glideTo(target: Rectangle, millis: Int = 200) {
        val from = window.bounds
        animate(millis, { t -> window.bounds = lerpBounds(from, target, t) })
    }

    private fun fade(from: Float, to: Float, millis: Int, done: () -> Unit = {}) {
        animate(millis, { t -> setOpacity(from + (to - from) * t) }, done)
    }

    override fun minimize() {
        val before = window.bounds
        val shrunk = Rectangle(
            before.x + before.width / 20, before.y + before.height / 10,
            before.width - before.width / 10, before.height - before.height / 10,
        )
        animate(150, { t ->
            window.bounds = lerpBounds(before, shrunk, t)
            setOpacity(1f - t)
        }) {
            window.extendedState = window.extendedState or Frame.ICONIFIED
            // Back to the real size while hidden; it fades in when restored.
            window.bounds = before
        }
    }

    override fun toggleMaximize() {
        if (maximized) {
            maximized = false
            restore?.let { glideTo(it) }
        } else {
            restore = window.bounds
            maximized = true
            glideTo(workArea())
        }
    }

    override fun close() = onClose()

    override fun quit() = quit.invoke()

    private var dragMouse: java.awt.Point? = null
    private var dragWindow: java.awt.Point? = null
    private var snapped = false

    override fun dragStart() {
        timer?.stop()
        val mouse = MouseInfo.getPointerInfo()?.location ?: return
        if (maximized || snapped) {
            // Like Windows: pulling a maximized or snapped window gives it back its size, under the mouse.
            val old = restore ?: Rectangle(window.x, window.y, 1180, 780)
            val ratio = (mouse.x - window.x).toDouble() / window.width.coerceAtLeast(1)
            window.setBounds((mouse.x - old.width * ratio).toInt(), mouse.y - 18, old.width, old.height)
            maximized = false
            snapped = false
        }
        dragMouse = mouse
        dragWindow = window.location
    }

    override fun dragMove() {
        val startMouse = dragMouse ?: return
        val startWindow = dragWindow ?: return
        val mouse = MouseInfo.getPointerInfo()?.location ?: return
        window.setLocation(startWindow.x + mouse.x - startMouse.x, startWindow.y + mouse.y - startMouse.y)
    }

    override fun dragEnd() {
        val mouse = MouseInfo.getPointerInfo()?.location ?: return
        dragMouse = null
        val work = workArea()
        val snap = snapFor(mouse, work) ?: return
        restore = Rectangle(window.x, window.y, window.width, window.height)
        if (snap == Snap.MAXIMIZE) maximized = true else snapped = true
        glideTo(snapBounds(snap, work), millis = 180)
    }

    @Composable
    override fun DragArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Box(modifier, content = content)
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
