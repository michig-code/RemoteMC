package de.remotemc.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.ui.components.ConnectionState
import de.remotemc.app.ui.components.StatusPill
import org.json.JSONObject

enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Übersicht", Icons.Outlined.Dashboard),
    Players("Spieler", Icons.Outlined.Group),
    Console("Konsole", Icons.Outlined.Terminal),
    Chat("Chat", Icons.AutoMirrored.Outlined.Chat),
}

/** Everything the server screen shows, independent of the ViewModel (used by previews/screenshot tests). */
data class ServerUiState(
    val name: String,
    val status: ServerStatus?,
    val players: List<PlayerInfo>,
    val console: List<ConsoleEntry>,
    val error: String?,
    val refreshing: Boolean,
)

class ServerActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onCommand: (String) -> Unit,
    val onBroadcast: (String) -> Unit,
    val onPlayerAction: (player: String, action: String, body: JSONObject, success: String) -> Unit,
)

@Composable
fun ServerScreen(vm: ServerViewModel, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Poll only while the app is in the foreground.
    LaunchedEffect(vm) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollForever() }
    }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    ServerScreenContent(
        state = ServerUiState(
            name = vm.profile.name,
            status = vm.status,
            players = vm.players,
            console = vm.console,
            error = vm.connectionError,
            refreshing = vm.refreshing,
        ),
        actions = ServerActions(
            onBack = onBack,
            onRefresh = vm::refresh,
            onCommand = vm::runCommand,
            onBroadcast = vm::broadcast,
            onPlayerAction = { p, a, b, s -> vm.playerAction(p, a, b, s) },
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
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    val connection = when {
        state.error != null -> ConnectionState.Offline
        state.status == null -> ConnectionState.Connecting
        else -> ConnectionState.Online
    }
    val statusText = when (connection) {
        ConnectionState.Offline -> "Offline · Tippen zum Wiederholen"
        ConnectionState.Connecting -> "Verbinde…"
        ConnectionState.Online -> "Online · ${state.status?.onlinePlayers ?: 0} Spieler"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusPill(
                            state = connection,
                            text = statusText,
                            refreshing = state.refreshing,
                            onClick = actions.onRefresh,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = {
                            if (it == Tab.Players && state.players.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text(state.players.size.toString()) } }) {
                                    Icon(it.icon, null)
                                }
                            } else {
                                Icon(it.icon, null)
                            }
                        },
                        label = { Text(it.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
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
                    // Pull-to-refresh only where the content is a scrolling list of server data.
                    Tab.Dashboard -> PullToRefreshBox(state.refreshing, actions.onRefresh) {
                        DashboardTab(state.status, state.error)
                    }
                    Tab.Players -> PullToRefreshBox(state.refreshing, actions.onRefresh) {
                        PlayersTab(state.players, actions.onPlayerAction)
                    }
                    Tab.Console -> ConsoleTab(state.console, actions.onCommand)
                    Tab.Chat -> ChatTab(state.console, actions.onBroadcast)
                }
            }
        }
    }
}
