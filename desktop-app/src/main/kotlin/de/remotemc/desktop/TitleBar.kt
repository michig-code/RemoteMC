package de.remotemc.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.stringResource

/** What the title bar needs from the window (a fake in screenshot tests). */
interface WindowControls {
    val maximized: Boolean
    fun minimize()
    fun toggleMaximize()
    fun close()

    /** Ends RemoteMC completely (also from the tray), e.g. before an update is installed. */
    fun quit() = close()

    /** The mouse was pressed on the title bar and starts moving: remember where. */
    fun dragStart() {}

    /** The mouse moved while dragging: move the window along. */
    fun dragMove() {}

    /** Dragging ended (the window may snap to a screen edge). */
    fun dragEnd() {}

    /** True when Windows draws the frame, its buttons and handles dragging (JetBrains Runtime). */
    val native: Boolean get() = false

    /** Room to leave on the right for Windows' own buttons. */
    val rightInset: Dp get() = 0.dp

    /** Tells Windows whether the mouse is over our content (true) or free title bar (false). */
    fun hitTest(client: Boolean) {}

    /** The empty part of the bar (double-click maximizes; the whole bar drags the window). */
    @Composable
    fun DragArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit)
}

val TitleBarHeight = 42.dp

/** RemoteMC's own title bar: logo, the server list, a tab per open server and the window buttons. */
@Composable
fun TitleBar(model: AppModel, tabs: Tabs, controls: WindowControls) {
    val servers = model.servers.servers.value
    Row(
        Modifier.fillMaxWidth().height(TitleBarHeight).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .then(if (controls.native) Modifier.nativeCaption(controls) else Modifier.windowDrag(controls)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp))
        AppLogo(22.dp)
        Spacer(Modifier.width(10.dp))
        Tab(
            title = stringResource(Res.string.servers_title),
            selected = tabs.active == null,
            leading = { Icon(Icons.Outlined.GridView, null, Modifier.size(16.dp)) },
            onClick = tabs::showHome,
        )
        tabs.open.forEach { id ->
            Spacer(Modifier.width(4.dp))
            when (id) {
                Tabs.SETTINGS -> Tab(
                    title = stringResource(Res.string.settings_title),
                    selected = tabs.active == id,
                    leading = { Icon(Icons.Outlined.Settings, null, Modifier.size(16.dp)) },
                    onClick = { tabs.open(id) },
                    onClose = { tabs.close(id) },
                )
                Tabs.PAIR -> Tab(
                    title = stringResource(Res.string.servers_add),
                    selected = tabs.active == id,
                    leading = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) },
                    onClick = { tabs.open(id) },
                    onClose = { tabs.close(id) },
                )
                else -> {
                    val profile = servers.firstOrNull { it.id == id } ?: return@forEach
                    val session = tabs.existing(id)
                    Tab(
                        title = profile.name,
                        selected = tabs.active == id,
                        leading = { session?.let { StatusDot(it.connection, 4.dp) } },
                        onClick = { tabs.open(id) },
                        onClose = { tabs.close(id) },
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        BarButton(Icons.Outlined.Add, stringResource(Res.string.servers_add), onClick = { tabs.openSpecial(Tabs.PAIR) })
        // Empty space: drag to move the window, double-click to maximize.
        controls.DragArea(Modifier.weight(1f).fillMaxHeight()) {
            // With Windows' frame, a double-click on the free title bar is handled by Windows itself.
            if (!controls.native) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onDoubleTap = { controls.toggleMaximize() }) })
            }
        }
        model.updates?.let { updates ->
            updates.available?.let { info ->
                var open by remember { mutableStateOf(false) }
                UpdatePill(info.version) { open = true }
                if (open) UpdateDialog(updates, info, onQuit = controls::quit) { open = false }
                Spacer(Modifier.width(8.dp))
            }
        }
        BarButton(Icons.Outlined.Settings, stringResource(Res.string.settings_title), selected = tabs.active == Tabs.SETTINGS) {
            tabs.openSpecial(Tabs.SETTINGS)
        }
        Spacer(Modifier.width(6.dp))
        if (controls.native) {
            Spacer(Modifier.width(controls.rightInset))
        } else {
            BarButton(Icons.Outlined.Remove, stringResource(Res.string.window_minimize), wide = true, onClick = controls::minimize)
            BarButton(
                if (controls.maximized) Icons.Outlined.FilterNone else Icons.Outlined.CropSquare,
                stringResource(if (controls.maximized) Res.string.window_restore else Res.string.window_maximize),
                wide = true, onClick = controls::toggleMaximize,
            )
            BarButton(Icons.Outlined.Close, stringResource(Res.string.action_close), wide = true, danger = true, onClick = controls::close)
        }
    }
}

/**
 * Holding the mouse anywhere on the bar and moving it drags the window (also on a tab, like in a
 * browser); a click without moving still reaches the tab or button.
 */
private fun Modifier.windowDrag(controls: WindowControls): Modifier = pointerInput(controls) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (dragging) controls.dragEnd()
                break
            }
            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                controls.dragStart()
            }
            if (dragging) {
                controls.dragMove()
                // Moving means dragging: the tab under the mouse is not clicked afterwards.
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * With Windows' own frame: everything on the bar that nobody handled (free space, the logo)
 * counts as title bar, so Windows drags, snaps and maximizes the window natively.
 */
private fun Modifier.nativeCaption(controls: WindowControls): Modifier = pointerInput(controls) {
    awaitPointerEventScope {
        var inControl = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val handled = event.changes.any { it.isConsumed }
            if (event.type == PointerEventType.Press && handled) inControl = true
            controls.hitTest(handled || inControl)
            if (event.type == PointerEventType.Release) inControl = false
        }
    }
}

/** Marks tabs and buttons as our content (they take the mouse instead of Windows' title bar). */
private fun Modifier.titleBarControl(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            // Runs after the tab's own click handling (outer modifier), then claims the event.
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun Tab(
    title: String,
    selected: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.surfaceContainerHigh
        hovered -> MaterialTheme.colorScheme.surfaceContainer
        else -> Color.Transparent
    }
    Row(
        Modifier.titleBarControl().height(32.dp).widthIn(max = 220.dp).clip(RoundedCornerShape(10.dp)).background(background)
            .hoverable(interaction)
            // Middle click closes the tab, like in a browser.
            .pointerInput(onClose) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    if (onClose != null && currentEvent.buttons.isTertiaryPressed) {
                        down.consume()
                        onClose()
                    }
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = if (onClose != null) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(8.dp))
        Text(
            title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClose != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close, stringResource(Res.string.action_close), Modifier.size(14.dp),
                    tint = if (selected || hovered) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, wide: Boolean = false, danger: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.titleBarControl().fillMaxHeight().width(if (wide) 46.dp else 36.dp)
            .background(
                when {
                    hovered && danger -> Color(0xFFC42B1C)
                    hovered || selected -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Default),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(if (wide) 16.dp else 18.dp), tint = when {
            hovered && danger -> Color.White
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        })
    }
}

@Composable
private fun UpdatePill(version: String, onClick: () -> Unit) {
    Row(
        Modifier.titleBarControl().height(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(6.dp))
        Text("${stringResource(Res.string.update_button)} $version", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Release notes and "Install and restart" (or the download page for the portable version). */
@Composable
fun UpdateDialog(updates: UpdateState, info: UpdateInfo, onQuit: () -> Unit, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val installable = Updater.canInstall && info.msiUrl != null
    AlertDialog(
        onDismissRequest = { if (updates.progress == null) onDismiss() },
        icon = { Icon(Icons.Outlined.SystemUpdate, null) },
        title = { Text(stringResource(Res.string.update_title, info.version)) },
        text = {
            Column(Modifier.widthIn(min = 420.dp, max = 560.dp)) {
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    )
                }
                if (!installable) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(Res.string.update_portable_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                updates.progress?.let { p ->
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.update_downloading), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
                updates.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(Res.string.update_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (installable) {
                Button(onClick = { updates.install(onQuit) }, enabled = updates.progress == null) { Text(stringResource(Res.string.update_install)) }
            } else {
                Button(onClick = { uri.openUri(info.page); onDismiss() }) { Text(stringResource(Res.string.update_page)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = updates.progress == null) { Text(stringResource(Res.string.update_later)) }
        },
    )
}

/** Small pop-up in the bottom right corner when a new version is available. */
@Composable
fun UpdateToast(updates: UpdateState, onQuit: () -> Unit, modifier: Modifier = Modifier) {
    val info = updates.available
    val animate = LocalAnimations.current
    val uri = LocalUriHandler.current
    androidx.compose.animation.AnimatedVisibility(
        visible = info != null && !updates.dismissed,
        enter = if (animate) androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn() else androidx.compose.animation.EnterTransition.None,
        exit = if (animate) androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut() else androidx.compose.animation.ExitTransition.None,
        modifier = modifier,
    ) {
        if (info == null) return@AnimatedVisibility
        val installable = Updater.canInstall && info.msiUrl != null
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 10.dp,
            modifier = Modifier.padding(16.dp).width(380.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.update_title, info.version), style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                updates.progress != null -> stringResource(Res.string.update_downloading)
                                updates.error != null -> stringResource(Res.string.update_failed, updates.error.orEmpty())
                                installable -> stringResource(Res.string.update_toast_text)
                                else -> stringResource(Res.string.update_portable_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updates.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val progress = updates.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = updates::later) { Text(stringResource(Res.string.update_later)) }
                        Spacer(Modifier.width(8.dp))
                        if (installable) {
                            Button(onClick = { updates.install(onQuit) }) { Text(stringResource(Res.string.update_install)) }
                        } else {
                            Button(onClick = { uri.openUri(info.page) }) { Text(stringResource(Res.string.update_page)) }
                        }
                    }
                }
            }
        }
    }
}

