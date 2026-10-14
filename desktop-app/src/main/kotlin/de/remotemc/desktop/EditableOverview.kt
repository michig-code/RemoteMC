package de.remotemc.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import de.remotemc.app.data.DashCard
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.DashboardLayout
import de.remotemc.desktop.res.*
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.awt.Cursor

/** An overview card; null when the server cannot show it. */
typealias OverviewCard = (@Composable (Modifier) -> Unit)?

/**
 * The overview as a 6-column grid the user can rearrange: hold any card to start, then drag cards
 * with the mouse, change their width with the blue handle on the right edge or hide them; "Done" saves the layout.
 */
@Composable
fun EditableOverview(
    layout: DashboardLayout,
    defaults: DashboardLayout,
    onChange: (DashboardLayout) -> Unit,
    startEditing: Boolean = false,
    header: LazyGridScope.() -> Unit = {},
    content: (String) -> OverviewCard,
) {
    var editing by remember { mutableStateOf(startEditing) }
    var working by remember(layout) { mutableStateOf(layout) }
    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to ->
        val a = from.key as? String ?: return@rememberReorderableLazyGridState
        val b = to.key as? String ?: return@rememberReorderableLazyGridState
        working = working.move(a, b)
    }
    val cards = working.cards.filter { content(it.id) != null && (editing || !it.hidden) }
    // Width of one of the 6 columns (with the gap), for the handle that changes a card's width.
    var gridWidth by remember { mutableIntStateOf(0) }
    val cellW = with(LocalDensity.current) { (gridWidth - (48 - 14).dp.toPx()) / 6 }
    var preview by remember { mutableStateOf<Pair<String, Int>?>(null) }
    fun spanOf(card: DashCard) = preview?.takeIf { it.first == card.id }?.second ?: card.span(6)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(6),
            state = gridState,
            modifier = Modifier.fillMaxSize().onSizeChanged { gridWidth = it.width },
            // While editing, room at the bottom so the last card can scroll above the buttons.
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = if (editing) 130.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!editing) header()
            items(cards, key = { it.id }, span = { GridItemSpan(spanOf(it)) }) { card ->
                val draw = content(card.id) ?: return@items
                ReorderableItem(reorder, key = card.id, enabled = editing) { dragging ->
                    if (!editing) {
                        Box(Modifier.holdToEdit { editing = true }) { draw(Modifier.fillMaxWidth()) }
                    } else {
                        val lift by animateDpAsState(if (dragging) 12.dp else 0.dp, label = "lift")
                        val shape = RoundedCornerShape(20.dp)
                        Box(Modifier.shadow(lift, shape)) {
                            Box(Modifier.alpha(if (card.hidden) 0.4f else 1f)) { draw(Modifier.fillMaxWidth()) }
                            // Covers the card so its own buttons don't react; drag it to move it.
                            Box(
                                Modifier.matchParentSize()
                                    .border(2.dp, MaterialTheme.colorScheme.primary, shape)
                                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
                                    .draggableHandle(onDragStopped = { onChange(working) }),
                            ) {
                                WidthHandle(
                                    span = spanOf(card),
                                    cellW = cellW,
                                    onPreview = { preview = card.id to it },
                                    onDone = {
                                        preview?.let { (id, span) -> working = working.setSpan(id, span, 6) }
                                        preview = null
                                        onChange(working)
                                    },
                                )
                                Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                    CardButton(
                                        if (card.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        stringResource(if (card.hidden) Res.string.dashboard_card_show else Res.string.dashboard_card_hide),
                                    ) { working = working.toggleHidden(card.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
        // "Reset" and "Done" stay at the bottom of the window while the cards scroll beneath them.
        AnimatedVisibility(
            visible = editing,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(
                Modifier.padding(20.dp).widthIn(max = 900.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The short how-to sits here, so no card above the overview takes up room.
                    Column(Modifier.weight(2f)) {
                        Text(stringResource(Res.string.dashboard_edit_title), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(Res.string.desktop_dashboard_edit_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { working = defaults }, modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand)) {
                        Text(stringResource(Res.string.dashboard_edit_reset))
                    }
                    Button(onClick = { editing = false; onChange(working) }, modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand)) {
                        Text(stringResource(Res.string.dashboard_edit_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun CardButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    // A plain round button: IconButton would reserve a 48 dp touch area and overlap its neighbour.
    Box(
        Modifier.size(32.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

/** Calls [onHold] when the card is held down, without taking clicks away from what is inside it. */
private fun Modifier.holdToEdit(onHold: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var ended = false
            while (!ended) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ended = change == null || !change.pressed ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
            true
        }
        if (released == null) {
            onHold()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}

/** Blue handle on the right edge of a card in edit mode; dragging it changes the width in columns. */
@Composable
private fun BoxScope.WidthHandle(span: Int, cellW: Float, onPreview: (Int) -> Unit, onDone: () -> Unit) {
    val current by rememberUpdatedState(span)
    val label = stringResource(Res.string.servers_resize)
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier.align(Alignment.CenterEnd).size(32.dp)
            .semantics { contentDescription = label }
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(cellW) {
                var start = current
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { start = current; dx = 0f },
                    onDragEnd = onDone,
                    onDragCancel = onDone,
                ) { change, amount ->
                    change.consume()
                    dx += amount
                    if (cellW > 0) onPreview((start + (dx / cellW).roundToInt()).coerceIn(1, 6))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 10.dp, height = 28.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(3.dp, primary, CircleShape))
    }
}
