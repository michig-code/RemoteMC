package de.remotemc.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.DashboardLayout
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.withTimeoutOrNull
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** A card of the overview; null when the server cannot show it (e.g. an older plugin). */
typealias DashCardContent = (@Composable (Modifier) -> Unit)?

/**
 * The overview as a grid the user can rearrange: hold any card to start, then hold and drag cards
 * to move them, change their width or hide them; "Done" saves the layout.
 */
@Composable
internal fun EditableDashboard(
    layout: DashboardLayout,
    defaults: DashboardLayout,
    columns: Int,
    onChange: (DashboardLayout) -> Unit,
    startEditing: Boolean = false, // screenshot tests
    header: LazyGridScope.() -> Unit = {},
    content: (String) -> DashCardContent,
) {
    val haptics = rememberHaptics()
    var editing by rememberSaveable { mutableStateOf(startEditing) }
    // While editing, changes apply here first and are saved with "Done".
    var working by remember(layout) { mutableStateOf(layout) }
    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to ->
        working = working.move(from.key as? String ?: return@rememberReorderableLazyGridState, to.key as? String ?: return@rememberReorderableLazyGridState)
        haptics.tap()
    }
    if (editing) BackHandler { editing = false; onChange(working) }

    val cards = working.cards.filter { content(it.id) != null && (editing || !it.hidden) }
    LazyVerticalGrid(
        GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (editing) {
            item(key = "edit-bar", span = { GridItemSpan(maxLineSpan) }) {
                EditBar(
                    onReset = { haptics.tap(); working = defaults },
                    onDone = { haptics.success(); editing = false; onChange(working) },
                )
            }
        } else {
            header()
        }
        items(cards, key = { it.id }, span = { GridItemSpan(it.size.span(columns).coerceAtMost(maxLineSpan)) }) { card ->
            val draw = content(card.id) ?: return@items
            ReorderableItem(reorder, key = card.id, enabled = editing) { dragging ->
                if (!editing) {
                    Box(Modifier.holdToEdit { haptics.heavy(); editing = true }) { draw(Modifier.fillMaxWidth()) }
                } else {
                    val lift by animateDpAsState(if (dragging) 10.dp else 0.dp, label = "lift")
                    Box(Modifier.shadow(lift, MaterialTheme.shapes.large)) {
                        Box(Modifier.alpha(if (card.hidden) 0.4f else 1f)) { draw(Modifier.fillMaxWidth()) }
                        // Covers the card so its own buttons don't react; hold and drag to move it.
                        Box(
                            Modifier.matchParentSize()
                                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 1f else 0.6f), MaterialTheme.shapes.large)
                                .longPressDraggableHandle(onDragStarted = { haptics.heavy() }, onDragStopped = { onChange(working) }),
                        ) {
                            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                CardButton(Icons.Outlined.SwapHoriz, stringResource(R.string.dashboard_card_width)) {
                                    haptics.tap()
                                    working = working.resize(card.id, allowThird = columns >= 6)
                                }
                                Spacer(Modifier.width(4.dp))
                                CardButton(
                                    if (card.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    stringResource(if (card.hidden) R.string.dashboard_card_show else R.string.dashboard_card_hide),
                                ) {
                                    haptics.tap()
                                    working = working.toggleHidden(card.id)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "end", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.size(8.dp)) }
    }
}

@Composable
private fun CardButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    // A plain round button: IconButton would reserve a 48 dp touch area and overlap its neighbour.
    Box(
        Modifier.size(32.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
private fun EditBar(onReset: () -> Unit, onDone: () -> Unit) {
    McCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_edit_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.dashboard_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.dashboard_edit_reset)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDone) { Text(stringResource(R.string.dashboard_edit_done)) }
            }
        }
    }
}

/**
 * Calls [onHold] when the card is held, without taking taps away from what is inside it. Watches
 * the touch before the card's own buttons do and swallows the rest of that touch once it fires.
 */
private fun Modifier.holdToEdit(onHold: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var ended = false
            while (!ended) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                // Lifting the finger or moving it (scrolling) is not holding.
                ended = change == null || !change.pressed ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
            true
        }
        if (released == null) {
            onHold()
            // Swallow the rest so the card's button does not also fire.
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}
