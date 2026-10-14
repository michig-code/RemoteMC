package de.remotemc.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.awt.Cursor
import java.io.File

/** Layout choices on this PC: divider positions and the overview cards (layout.json next to the settings). */
object SplitPrefs {
    /** Screenshot tests turn saving off. */
    var save = true
    private val file get() = File(AppDirs.dir, "layout.json")
    private val data: JSONObject by lazy { runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) }

    fun get(key: String, default: Float): Float = data.optDouble(key, default.toDouble()).toFloat()

    fun set(key: String, value: Float) {
        data.put(key, value.toDouble())
        write()
    }

    fun text(key: String): String = data.optString(key)

    fun setText(key: String, value: String) {
        data.put(key, value)
        write()
    }

    private fun write() {
        if (save) runCatching { file.writeText(data.toString()) }
    }
}

/**
 * Two areas side by side (or one above the other with [vertical]) with a divider the user can drag
 * (double-click resets it). [fraction] is the first area's share; each keeps at least its minimum size.
 */
@Composable
fun SplitView(
    key: String,
    defaultFraction: Float,
    modifier: Modifier = Modifier,
    minLeft: Dp = 240.dp,
    minRight: Dp = 300.dp,
    vertical: Boolean = false,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    var fraction by remember(key) { mutableFloatStateOf(SplitPrefs.get(key, defaultFraction)) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val handle = 16.dp
        val available = ((if (vertical) maxHeight else maxWidth) - handle).coerceAtLeast(1.dp)
        val min = (minLeft / available).coerceIn(0.1f, 0.5f)
        val max = (1f - minRight / available).coerceIn(0.5f, 0.9f)
        val shown = fraction.coerceIn(min, max)
        val drag = rememberDraggableState { delta ->
            fraction = (fraction.coerceIn(min, max) + with(density) { delta.toDp() } / available).coerceIn(min, max)
        }
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val dragged by interaction.collectIsDraggedAsState()
        val divider = @Composable {
            Box(
                (if (vertical) Modifier.height(handle).fillMaxWidth() else Modifier.width(handle).fillMaxHeight())
                    .pointerHoverIcon(PointerIcon(Cursor(if (vertical) Cursor.N_RESIZE_CURSOR else Cursor.E_RESIZE_CURSOR)))
                    .hoverable(interaction)
                    .draggable(
                        drag, if (vertical) Orientation.Vertical else Orientation.Horizontal,
                        interactionSource = interaction, onDragStopped = { SplitPrefs.set(key, fraction) },
                    )
                    .pointerInput(key) {
                        detectTapGestures(onDoubleTap = {
                            fraction = defaultFraction
                            SplitPrefs.set(key, defaultFraction)
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                val active = hovered || dragged
                val thick = if (active) 4.dp else 3.dp
                val long = if (active) 64.dp else 40.dp
                Box(
                    (if (vertical) Modifier.height(thick).width(long) else Modifier.width(thick).height(long)).clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        if (vertical) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.height(available * shown).fillMaxWidth()) { left() }
                divider()
                Box(Modifier.weight(1f).fillMaxWidth()) { right() }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(available * shown).fillMaxHeight()) { left() }
                divider()
                Box(Modifier.weight(1f).fillMaxHeight()) { right() }
            }
        }
    }
}
