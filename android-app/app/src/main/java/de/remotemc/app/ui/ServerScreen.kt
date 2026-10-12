package de.remotemc.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.remotemc.app.R
import de.remotemc.app.data.BanInfo
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.PluginInfo
import de.remotemc.app.data.ScheduleInfo
import de.remotemc.app.data.ScheduleList
import de.remotemc.app.data.HistoryRange
import de.remotemc.app.data.PerformanceHistory
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.Whitelist
import de.remotemc.app.data.WorldDetails
import de.remotemc.app.net.ApiError
import de.remotemc.app.ui.components.ConnectionState
import de.remotemc.app.ui.components.StatusPill
import de.remotemc.app.ui.util.UiText
import de.remotemc.app.ui.util.errorText
import de.remotemc.app.ui.util.rememberHaptics
import org.json.JSONObject

enum class Tab(@StringRes val label: Int, val icon: ImageVector, val minRole: Role) {
    Dashboard(R.string.tab_overview, Icons.Outlined.Dashboard, Role.VIEWER),
    Players(R.string.tab_players, Icons.Outlined.Group, Role.VIEWER),
    Console(R.string.tab_console, Icons.Outlined.Terminal, Role.MODERATOR),
    Chat(R.string.tab_chat, Icons.AutoMirrored.Outlined.Chat, Role.VIEWER),
}

/** Everything the server screen shows, independent of the ViewModel (used by screenshot tests). */
data class ServerUiState(
    val name: String,
    val status: ServerStatus?,
    val players: List<PlayerInfo>,
    val console: List<ConsoleEntry>,
    val chat: List<ChatEntry>,
    val connection: Connection,
    val error: Throwable?,
    val refreshing: Boolean,
    val role: Role,
    val commandsEnabled: Boolean = true,
    val history: List<String> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    val usageHint: String? = null,
)

class ServerActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onCommand: (String) -> Unit,
    val onBroadcast: (String) -> Unit,
    val onPlayerAction: (player: String, action: String, body: JSONObject, success: UiText) -> Unit,
    val onRepair: () -> Unit,
    val onCommandInput: (String) -> Unit = {},
    val onForgetCommand: (String) -> Unit = {},
    /** Loads the performance history; null hides the chart. */
    val loadHistory: (suspend (HistoryRange) -> PerformanceHistory)? = null,
    /** Whitelist and ban list (moderators and admins); null hides them. */
    val moderation: ModerationActions? = null,
    /** World controls (admins); null hides them. */
    val worlds: WorldActions? = null,
    /** Installed plugins (moderators and admins); null hides them. */
    val loadPlugins: (suspend () -> List<PluginInfo>)? = null,
    /** Scheduled commands (admins); null hides them. */
    val schedules: ScheduleActions? = null,
)

class ScheduleActions(
    val load: suspend () -> ScheduleList,
    val save: suspend (ScheduleInfo) -> ScheduleList,
    val run: suspend (String) -> ScheduleList,
    val delete: suspend (String) -> ScheduleList,
)

class WorldActions(
    val load: suspend (String) -> WorldDetails,
    val change: suspend (world: String, action: String, body: JSONObject) -> WorldDetails,
)

/** Calls for the whitelist and ban list; each returns the updated list. */
class ModerationActions(
    val whitelist: suspend () -> Whitelist,
    val setWhitelist: suspend (Boolean) -> Whitelist,
    val addToWhitelist: suspend (String) -> Whitelist,
    val removeFromWhitelist: suspend (String) -> Whitelist,
    val bans: suspend () -> List<BanInfo>,
    val pardon: suspend (String) -> List<BanInfo>,
)

@Composable
fun ServerScreen(vm: ServerViewModel, onBack: () -> Unit, onRepair: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Stay connected only while the screen is visible.
    LaunchedEffect(vm) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.runConnection() }
    }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it.resolve(context)) }
    }

    ServerScreenContent(
        state = ServerUiState(
            name = vm.profile.name,
            status = vm.status,
            players = vm.players,
            console = vm.console,
            chat = vm.chat,
            connection = vm.connection,
            error = vm.error,
            refreshing = vm.refreshing,
            role = vm.role,
            commandsEnabled = vm.session?.commandsEnabled ?: true,
            history = vm.history,
            suggestions = vm.suggestions,
            usageHint = vm.usageHint,
        ),
        actions = ServerActions(
            onBack = onBack,
            onRefresh = vm::refresh,
            onCommand = vm::runCommand,
            onBroadcast = vm::broadcast,
            onPlayerAction = { p, a, b, s -> vm.playerAction(p, a, b, s) },
            onRepair = onRepair,
            onCommandInput = vm::onCommandInput,
            onForgetCommand = vm::forgetCommand,
            loadHistory = vm::performance,
            moderation = vm.moderation,
            worlds = vm.worldActions,
            loadPlugins = vm::plugins,
            schedules = vm.scheduleActions,
        ),
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreenContent(
    state: ServerUiState,
    actions: ServerActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    initialTab: Tab = Tab.Dashboard,
) {
    val tabs = Tab.entries.filter { state.role.atLeast(it.minRole) }
    var selected by rememberSaveable { mutableStateOf(initialTab) }
    val tab = if (selected in tabs) selected else Tab.Dashboard
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val haptics = rememberHaptics()
    val context = LocalContext.current

    val connection = when (state.connection) {
        Connection.LIVE, Connection.POLLING -> ConnectionState.Online
        Connection.OFFLINE -> ConnectionState.Offline
        Connection.CONNECTING -> ConnectionState.Connecting
    }
    val players = state.status?.onlinePlayers ?: 0
    val statusText = when (state.connection) {
        Connection.LIVE -> stringResource(R.string.status_live, pluralStringResource(R.plurals.players_count, players, players))
        Connection.POLLING -> stringResource(R.string.status_online, pluralStringResource(R.plurals.players_count, players, players))
        Connection.OFFLINE -> stringResource(R.string.status_offline_retry)
        Connection.CONNECTING -> stringResource(R.string.status_connecting)
    }

    val broadcast: (String) -> Unit = { message ->
        actions.onBroadcast(message)
        haptics.success()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Name + status chip need more room than one line, especially with large fonts.
                expandedHeight = (56 * LocalDensity.current.fontScale + 16).dp.coerceAtLeast(64.dp),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusPill(
                            state = connection,
                            text = statusText,
                            refreshing = state.refreshing,
                            onClick = {
                                haptics.tap()
                                actions.onRefresh()
                            },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (!wide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEach {
                        NavigationBarItem(
                            selected = tab == it,
                            onClick = { selected = it; haptics.tap() },
                            icon = { TabIcon(it, state.players.size) },
                            label = { Text(stringResource(it.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // consumeWindowInsets: the keyboard covers the bottom bar, so its height must not be added twice.
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (wide) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                    Spacer(Modifier.padding(top = 8.dp))
                    tabs.forEach {
                        NavigationRailItem(
                            selected = tab == it,
                            onClick = { selected = it; haptics.tap() },
                            icon = { TabIcon(it, state.players.size) },
                            label = { Text(stringResource(it.label)) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                AccessBanner(state.error, actions.onRepair)
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(tween(280)) { it / 6 * dir } + fadeIn(tween(280)))
                                .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(200)))
                        },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            Tab.Dashboard -> PullToRefreshBox(state.refreshing, actions.onRefresh) {
                                DashboardTab(
                                    state.status, state.error?.let { context.errorText(it) }, wide, actions.loadHistory,
                                    actions.worlds?.takeIf { state.role.atLeast(Role.ADMIN) },
                                    actions.loadPlugins?.takeIf { state.role.atLeast(Role.MODERATOR) },
                                    actions.schedules?.takeIf { state.role.atLeast(Role.ADMIN) && state.commandsEnabled },
                                )
                            }
                            Tab.Players -> PlayersSection(state, actions)
                            Tab.Console -> ConsoleTab(
                                entries = state.console,
                                canRunCommands = state.role.atLeast(Role.ADMIN) && state.commandsEnabled,
                                history = state.history,
                                suggestions = state.suggestions,
                                usageHint = state.usageHint,
                                onInput = actions.onCommandInput,
                                onCommand = actions.onCommand,
                                onForget = actions.onForgetCommand,
                            )
                            Tab.Chat -> ChatTab(
                                state.chat, state.role.atLeast(Role.MODERATOR), broadcast,
                                skins = state.players.mapNotNull { p -> p.skin?.let { p.name to it } }.toMap(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: Tab, playerCount: Int) {
    if (tab == Tab.Players && playerCount > 0) {
        BadgedBox(badge = { Badge { Text(playerCount.toString()) } }) { Icon(tab.icon, null) }
    } else {
        Icon(tab.icon, null)
    }
}

/** Explains problems the user has to act on: changed certificate or revoked access. */
@Composable
private fun AccessBanner(error: Throwable?, onRepair: () -> Unit) {
    val message = when {
        error is ApiError.CertificateChanged -> R.string.banner_certificate_changed
        error is ApiError.Http && error.code == "invalid_token" -> R.string.banner_token_revoked
        else -> null
    }
    AnimatedVisibility(message != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GppMaybe, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    message?.let { stringResource(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onRepair,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.action_pair_again)) }
            }
        }
    }
}
