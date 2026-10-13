package de.remotemc.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** [feature]: only shown when the server's plugin has it (older plugins lack the newer endpoints). */
enum class Pane(val label: StringResource, val icon: ImageVector, val minRole: Role, val feature: String? = null) {
    OVERVIEW(Res.string.tab_overview, Icons.Outlined.Dashboard, Role.VIEWER),
    PLAYERS(Res.string.tab_players, Icons.Outlined.Group, Role.VIEWER),
    CONSOLE(Res.string.tab_console, Icons.Outlined.Terminal, Role.MODERATOR),
    CHAT(Res.string.tab_chat, Icons.AutoMirrored.Outlined.Chat, Role.VIEWER),
    WORLDS(Res.string.dashboard_worlds, Icons.Outlined.Public, Role.ADMIN, "worlds"),
    PLUGINS(Res.string.dashboard_plugins, Icons.Outlined.Extension, Role.MODERATOR, "plugins"),
    SCHEDULES(Res.string.schedules_title, Icons.Outlined.Schedule, Role.ADMIN, "schedules"),
    FILES(Res.string.files_title, Icons.Outlined.FolderOpen, Role.ADMIN, "files"),
}

@Composable
fun ServerScreen(model: AppModel, profile: ServerProfile, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val session = remember(profile.id) { ServerSession(profile, scope, model.servers, model.history) }
    DisposableEffect(session) {
        session.start()
        onDispose { session.stop() }
    }
    ServerContent(session, onBack)
}

/** Separate from the connection so screenshot tests can show it with fixed data. */
@Composable
fun ServerContent(session: ServerSession, onBack: () -> Unit, initialPane: Pane = Pane.OVERVIEW, playersView: Int = 0) {
    var pane by rememberSaveable { mutableStateOf(initialPane) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(session) { for (m in session.messages) snackbar.showSnackbar(m) }
    val panes = Pane.entries.filter { session.role.atLeast(it.minRole) && (it.feature == null || session.supports(it.feature)) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, header = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
            }) {
                Spacer(Modifier.weight(0.1f))
                panes.forEach { p ->
                    NavigationRailItem(
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        selected = pane == p,
                        onClick = { pane = p },
                        icon = { Icon(p.icon, null) },
                        label = { Text(stringResource(p.label), textAlign = TextAlign.Center, maxLines = 2) },
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Column(Modifier.fillMaxSize()) {
                TopBar(session)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val animate = LocalAnimations.current
                    // Switching areas slides up or down, following the order in the side bar.
                    AnimatedContent(
                        targetState = if (pane in panes) pane else Pane.OVERVIEW,
                        transitionSpec = {
                            if (!animate) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (slideInVertically(tween(260)) { it / 12 * dir } + fadeIn(tween(260)))
                                    .togetherWith(slideOutVertically(tween(180)) { -it / 12 * dir } + fadeOut(tween(180)))
                            }
                        },
                        label = "pane",
                    ) { shown ->
                    when (shown) {
                        Pane.OVERVIEW -> OverviewPane(session)
                        Pane.PLAYERS -> PlayersPane(session, playersView)
                        Pane.CONSOLE -> ConsolePane(session)
                        Pane.CHAT -> ChatPane(session)
                        Pane.WORLDS -> WorldsPane(session)
                        Pane.PLUGINS -> PluginsPane(session)
                        Pane.SCHEDULES -> SchedulesPane(session)
                        Pane.FILES -> FilesPane(session)
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(session: ServerSession) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(session.profile.name, style = MaterialTheme.typography.headlineSmall)
            Text(session.profile.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(onClick = session::retry, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(session.connection, 9.dp)
                val players = session.status?.onlinePlayers ?: 0
                val count = pluralStringResource(Res.plurals.players_count, players, players)
                Text(
                    when (session.connection) {
                        Conn.LIVE -> stringResource(Res.string.status_live, count)
                        Conn.POLLING -> stringResource(Res.string.status_online, count)
                        Conn.CONNECTING -> stringResource(Res.string.status_connecting)
                        Conn.OFFLINE -> stringResource(Res.string.status_offline_retry)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.Refresh, stringResource(Res.string.action_refresh), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

