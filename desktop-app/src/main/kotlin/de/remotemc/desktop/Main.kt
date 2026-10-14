package de.remotemc.desktop

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import de.remotemc.desktop.res.Res
import de.remotemc.desktop.res.logo
import de.remotemc.desktop.res.tray_open
import de.remotemc.desktop.res.tray_quit
import de.remotemc.desktop.res.tray_running
import de.remotemc.desktop.res.dock_main
import de.remotemc.desktop.res.dock_undock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import javax.imageio.ImageIO

sealed interface Screen {
    data object Servers : Screen
    data class Pair(val link: String? = null, val repair: String? = null) : Screen
    data class Server(val id: String) : Screen
    data object Settings : Screen
}

/** Everything the screens share. */
class AppModel(
    val servers: ServerStore = ServerStore(),
    val settings: SettingsStore = SettingsStore(),
    val history: CommandHistory = CommandHistory(),
    /** Favorites, macros, player notes and snapshots per server. */
    val extras: ServerExtras = ServerExtras(),
) {
    /** Update check and installer (only in the running app, not in tests). */
    var updates: UpdateState? = null

    /** Ends RemoteMC completely (also when it sits in the tray), e.g. for an update. */
    var quit: () -> Unit = {}
}

fun main() = application {
    val model = remember { AppModel().also { AppLanguage.apply(it.settings.settings.value.language) } }
    val settings by model.settings.settings.collectAsState()
    val icon = remember {
        runCatching { BitmapPainter(ImageIO.read(AppModel::class.java.getResourceAsStream("/icon.png")).toComposeImageBitmap()) }.getOrNull()
    }
    val tray = rememberTrayState()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(true) }
    var trayHintShown by remember { mutableStateOf(false) }
    val tabs = remember { Tabs(model, scope) }
    var lastNotice by remember { mutableStateOf<DesktopNotice?>(null) }
    LaunchedEffect(Unit) {
        AlertCenter(model, scope) { n ->
            lastNotice = n
            tray.sendNotification(Notification(n.title, n.text.orEmpty(), if (n.warning) Notification.Type.Warning else Notification.Type.Info))
        }.start()
    }
    /** A click on the notification (or the tray icon) shows the window with that server. */
    val openFromTray = {
        visible = true
        lastNotice?.serverId?.let { id ->
            tabs.open(id)
            if (lastNotice?.chat == true) tabs.paneRequest = id to Pane.CHAT
        }
        lastNotice = null
    }
    // With notifications on, RemoteMC lives on in the tray when the window is closed.
    val inTray = settings.alerts.enabled
    if (inTray) {
        val trayIcon = icon ?: painterResource(Res.drawable.logo)
        val open = stringResource(Res.string.tray_open)
        val quit = stringResource(Res.string.tray_quit)
        Tray(trayIcon, state = tray, tooltip = "RemoteMC", onAction = openFromTray) {
            Item(open) { visible = true }
            Item(quit, onClick = ::exitApplication)
        }
    }
    val runningHint = stringResource(Res.string.tray_running)
    val windowState = rememberWindowState(size = DpSize(1180.dp, 780.dp))
    val close = {
        if (inTray) {
            visible = false
            if (!trayHintShown) {
                trayHintShown = true
                tray.sendNotification(Notification("RemoteMC", runningHint, Notification.Type.None))
            }
        } else {
            exitApplication()
        }
    }
    // On quitting, each open server saves what it shows (instant start next time).
    DisposableEffect(tabs) { onDispose { tabs.stopAll() } }
    LaunchedEffect(Unit) {
        model.quit = ::exitApplication
        model.updates = UpdateState(scope) { model.settings.settings.value.autoUpdate }.also { it.start() }
    }
    Window(
        onCloseRequest = close,
        visible = visible,
        title = "RemoteMC",
        icon = icon,
        state = windowState,
        // With the JetBrains Runtime the window keeps Windows' own frame (native dragging, snapping
        // and animations) and RemoteMC draws the tabs into its title bar; otherwise RemoteMC draws
        // the whole frame itself.
        undecorated = !NativeTitleBar.supported,
        onPreviewKeyEvent = { e -> tabShortcut(e, tabs) },
    ) {
        window.minimumSize = java.awt.Dimension(820, 560)
        LaunchedEffect(visible) { if (visible) window.toFront() }
        val dark = when (settings.theme) {
            ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val controls: WindowControls = remember {
            if (NativeTitleBar.supported) {
                NativeWindowControls(window, NativeTitleBar.install(window, TitleBarHeight, dark), close, quit = ::exitApplication)
            } else {
                AwtWindowControls(this, close, quit = ::exitApplication, animations = { model.settings.settings.value.animations })
            }
        }
        // Windows' own buttons follow the colour theme.
        LaunchedEffect(dark) { (controls as? NativeWindowControls)?.setDark(dark) }
        // A new language takes effect at once: everything is drawn again with the new texts.
        androidx.compose.runtime.key(settings.language) { App(model, tabs = tabs, controls = controls) }
        if (!controls.native) ResizeEdges(window)
    }
}

/** Ctrl+W closes the current server tab, Ctrl+Tab / Ctrl+Shift+Tab switch tabs. */
private fun tabShortcut(e: androidx.compose.ui.input.key.KeyEvent, tabs: Tabs): Boolean {
    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return false
    // F1 opens the help page, like in most Windows programs.
    if (e.key == androidx.compose.ui.input.key.Key.F1) { tabs.openSpecial(Tabs.HELP); return true }
    if (!e.isCtrlPressed) return false
    return when (e.key) {
        androidx.compose.ui.input.key.Key.W -> tabs.active?.let { tabs.close(it); true } ?: false
        androidx.compose.ui.input.key.Key.Tab -> { tabs.next(if (e.isShiftPressed) -1 else 1); true }
        else -> false
    }
}

@Composable
fun App(model: AppModel, start: Screen = Screen.Servers, tabs: Tabs? = null, controls: WindowControls? = null) {
    val settings by model.settings.settings.collectAsState()
    var screen by remember { mutableStateOf(start) }
    val servers by model.servers.servers.collectAsState()
    LaunchedEffect(servers) { tabs?.prune() }
    val saveable = rememberSaveableStateHolder()
    // First start: welcome pages (not for people who already added servers).
    val welcome = !settings.welcomed && servers.isEmpty()
    RemoteMCTheme(settings) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (tabs != null && controls != null) {
                TitleBar(model, tabs, controls)
            }
            if (welcome) {
                // Below the title bar, so the window can still be moved and closed.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    OnboardingPane { addServer ->
                        model.settings.update { it.copy(welcomed = true) }
                        if (addServer) { if (tabs != null) tabs.openSpecial(Tabs.PAIR) else screen = Screen.Pair() }
                    }
                }
                return@Column
            }
            val animate = LocalAnimations.current
            // A server tab or one of the screens (server list, pairing, settings); switching fades over.
            val target: Any = tabs?.active?.let { "tab:$it" } ?: screen
            val mainContent = @Composable { modifier: Modifier ->
                AnimatedContent(
                    targetState = target,
                    modifier = modifier,
                    transitionSpec = {
                        if (!animate) EnterTransition.None togetherWith ExitTransition.None
                        else (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f)) togetherWith fadeOut(tween(120))
                    },
                    label = "screen",
                ) { shown ->
                    Box(Modifier.fillMaxSize()) {
                        if (shown is String && tabs != null) {
                            val id = shown.removePrefix("tab:")
                            // Each tab keeps its own state (area, scroll position) while others are shown.
                            saveable.SaveableStateProvider(id) {
                                when (id) {
                                    Tabs.SETTINGS -> SettingsScreen(model, onBack = { tabs.close(Tabs.SETTINGS) }, onHelp = { tabs.openSpecial(Tabs.HELP) })
                                    Tabs.HELP -> HelpPane(onBack = { tabs.close(Tabs.HELP) })
                                    Tabs.PAIR -> PairScreen(model, tabs.pairLink, repair = tabs.pairRepair?.let(model.servers::get), onDone = { newId ->
                                        tabs.close(Tabs.PAIR)
                                        if (newId != null) {
                                            tabs.restart(newId)
                                            tabs.open(newId)
                                        }
                                    })
                                    else -> tabs.session(id)?.let {
                                        ServerContent(
                                            it, onBack = tabs::showHome, onRepair = { tabs.openSpecial(Tabs.PAIR, repair = id) },
                                            requestedPane = tabs.paneRequest?.takeIf { r -> r.first == id }?.second,
                                            onPaneShown = { tabs.paneRequest = null },
                                        )
                                    }
                                }
                            }
                            return@Box
                        }
                        when (val s = shown as Screen) {
                            Screen.Servers -> ServerListScreen(
                                model,
                                onOpen = { id -> if (tabs != null) tabs.open(id) else screen = Screen.Server(id) },
                                onAdd = { link -> if (tabs != null) tabs.openSpecial(Tabs.PAIR, link) else screen = Screen.Pair(link) },
                                onSettings = { if (tabs != null) tabs.openSpecial(Tabs.SETTINGS) else screen = Screen.Settings },
                                onRepair = { id -> if (tabs != null) tabs.openSpecial(Tabs.PAIR, repair = id) else screen = Screen.Pair(repair = id) },
                            )
                            is Screen.Pair -> PairScreen(model, s.link, repair = s.repair?.let(model.servers::get), onDone = { id ->
                                screen = Screen.Servers
                                if (id != null) { if (tabs != null) tabs.open(id) else screen = Screen.Server(id) }
                            })
                            is Screen.Server -> {
                                val profile = model.servers.get(s.id)
                                if (profile == null) screen = Screen.Servers
                                else ServerScreen(model, profile, onBack = { screen = Screen.Servers }, onRepair = { screen = Screen.Pair(repair = s.id) })
                            }
                            Screen.Settings -> SettingsScreen(model, onBack = { screen = Screen.Servers })
                        }
                    }
                }
            }
            // A docked server tab is shown next to the main one (dragged from the title bar).
            val dock = tabs?.docked?.takeIf { it.first != tabs.active && tabs.session(it.first) != null }
            Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { tabs?.contentBounds = it.boundsInRoot() }) {
                if (tabs == null || dock == null) {
                    mainContent(Modifier.fillMaxSize())
                } else {
                    val (dockId, side) = dock
                    val first = side == DockSide.LEFT || side == DockSide.TOP
                    val docked = @Composable { DockedPane(tabs, dockId, saveable) }
                    val main = @Composable { mainContent(Modifier.fillMaxSize()) }
                    SplitView(
                        "dock", 0.5f, Modifier.fillMaxSize(),
                        minLeft = 320.dp, minRight = 320.dp,
                        vertical = side == DockSide.TOP || side == DockSide.BOTTOM,
                        left = { if (first) docked() else main() },
                        right = { if (first) main() else docked() },
                    )
                }
                if (tabs != null) DockZones(tabs)
            }
        }
        // New version: small pop-up in the bottom right corner (installer version only replaces itself).
        model.updates?.let { updates ->
            UpdateToast(updates, onQuit = { controls?.quit() ?: model.quit() }, modifier = Modifier.align(Alignment.BottomEnd))
        }
        }
    }
}

/** A server tab docked next to the main one: its name, buttons to make it the main tab or undock it. */
@Composable
private fun DockedPane(tabs: Tabs, id: String, saveable: androidx.compose.runtime.saveable.SaveableStateHolder) {
    val session = tabs.session(id) ?: return
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(session.connection, 4.dp)
            Spacer(Modifier.width(8.dp))
            Text(session.profile.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.IconButton(onClick = { tabs.open(id) }) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.OpenInFull, org.jetbrains.compose.resources.stringResource(Res.string.dock_main))
            }
            androidx.compose.material3.IconButton(onClick = tabs::undock) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Close, org.jetbrains.compose.resources.stringResource(Res.string.dock_undock))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            saveable.SaveableStateProvider("dock:$id") {
                ServerContent(session, onBack = tabs::undock)
            }
        }
    }
}

/** While a tab is dragged into the window: where it would be docked (or opened, in the middle). */
@Composable
private fun DockZones(tabs: Tabs) {
    val drag = tabs.dockDrag ?: return
    val bounds = tabs.contentBounds ?: return
    val side = tabs.dropZone(drag.second)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val (l, t, r, b) = when (side) {
            DockSide.LEFT -> listOf(0f, 0f, w / 2, h)
            DockSide.RIGHT -> listOf(w / 2, 0f, w, h)
            DockSide.TOP -> listOf(0f, 0f, w, h / 2)
            DockSide.BOTTOM -> listOf(0f, h / 2, w, h)
            null -> listOf(0f, 0f, w, h)
        }
        val pad = with(density) { 8.dp.toPx() }
        drawRoundRect(
            color.copy(alpha = 0.18f),
            topLeft = androidx.compose.ui.geometry.Offset(l + pad, t + pad),
            size = androidx.compose.ui.geometry.Size(r - l - 2 * pad, b - t - 2 * pad),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(pad * 2),
        )
        drawRoundRect(
            color,
            topLeft = androidx.compose.ui.geometry.Offset(l + pad, t + pad),
            size = androidx.compose.ui.geometry.Size(r - l - 2 * pad, b - t - 2 * pad),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(pad * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(with(density) { 2.dp.toPx() }),
        )
    }
    if (bounds.isEmpty) return
}
