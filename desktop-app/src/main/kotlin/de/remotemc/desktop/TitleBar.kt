package de.remotemc.desktop

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    /** The part of the bar that moves the window when dragged. */
    @Composable
    fun DragArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit)
}

val TitleBarHeight = 42.dp

/** RemoteMC's own title bar: logo, the server list, a tab per open server and the window buttons. */
@Composable
fun TitleBar(model: AppModel, tabs: Tabs, controls: WindowControls, onAdd: () -> Unit) {
    val servers = model.servers.servers.value
    Row(
        Modifier.fillMaxWidth().height(TitleBarHeight).background(MaterialTheme.colorScheme.surfaceContainerLowest),
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
            val profile = servers.firstOrNull { it.id == id } ?: return@forEach
            val session = tabs.existing(id)
            Spacer(Modifier.width(4.dp))
            Tab(
                title = profile.name,
                selected = tabs.active == id,
                leading = { session?.let { StatusDot(it.connection, 4.dp) } },
                onClick = { tabs.open(id) },
                onClose = { tabs.close(id) },
            )
        }
        Spacer(Modifier.width(4.dp))
        BarButton(Icons.Outlined.Add, stringResource(Res.string.servers_add), onClick = onAdd)
        // Empty space: drag to move the window, double-click to maximize.
        controls.DragArea(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onDoubleTap = { controls.toggleMaximize() }) })
        }
        BarButton(Icons.Outlined.Remove, stringResource(Res.string.window_minimize), wide = true, onClick = controls::minimize)
        BarButton(
            if (controls.maximized) Icons.Outlined.FilterNone else Icons.Outlined.CropSquare,
            stringResource(if (controls.maximized) Res.string.window_restore else Res.string.window_maximize),
            wide = true, onClick = controls::toggleMaximize,
        )
        BarButton(Icons.Outlined.Close, stringResource(Res.string.action_close), wide = true, danger = true, onClick = controls::close)
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
        Modifier.height(32.dp).widthIn(max = 220.dp).clip(RoundedCornerShape(10.dp)).background(background)
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
private fun BarButton(icon: ImageVector, label: String, wide: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.fillMaxHeight().width(if (wide) 46.dp else 36.dp)
            .background(if (hovered) (if (danger) Color(0xFFC42B1C) else MaterialTheme.colorScheme.surfaceContainerHigh) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Default),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(if (wide) 16.dp else 18.dp), tint = if (hovered && danger) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
