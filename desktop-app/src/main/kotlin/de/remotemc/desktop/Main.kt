package de.remotemc.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import javax.imageio.ImageIO

sealed interface Screen {
    data object Servers : Screen
    data class Pair(val link: String? = null) : Screen
    data class Server(val id: String) : Screen
    data object Settings : Screen
}

/** Everything the screens share. */
class AppModel(
    val servers: ServerStore = ServerStore(),
    val settings: SettingsStore = SettingsStore(),
    val history: CommandHistory = CommandHistory(),
)

fun main() = application {
    val model = remember { AppModel() }
    val settings by model.settings.settings.collectAsState()
    val icon = remember {
        runCatching { BitmapPainter(ImageIO.read(AppModel::class.java.getResourceAsStream("/icon.png")).toComposeImageBitmap()) }.getOrNull()
    }
    val tray = rememberTrayState()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(true) }
    var trayHintShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        AlertCenter(model, scope) { n ->
            tray.sendNotification(Notification(n.title, n.text.orEmpty(), if (n.warning) Notification.Type.Warning else Notification.Type.Info))
        }.start()
    }
    // With notifications on, RemoteMC lives on in the tray when the window is closed.
    val inTray = settings.alerts.enabled
    if (inTray) {
        val trayIcon = icon ?: painterResource(Res.drawable.logo)
        val open = stringResource(Res.string.tray_open)
        val quit = stringResource(Res.string.tray_quit)
        Tray(trayIcon, state = tray, tooltip = "RemoteMC", onAction = { visible = true }) {
            Item(open) { visible = true }
            Item(quit, onClick = ::exitApplication)
        }
    }
    val runningHint = stringResource(Res.string.tray_running)
    Window(
        onCloseRequest = {
            if (inTray) {
                visible = false
                if (!trayHintShown) {
                    trayHintShown = true
                    tray.sendNotification(Notification("RemoteMC", runningHint, Notification.Type.None))
                }
            } else {
                exitApplication()
            }
        },
        visible = visible,
        title = "RemoteMC",
        icon = icon,
        state = rememberWindowState(size = DpSize(1180.dp, 780.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(820, 560)
        LaunchedEffect(visible) { if (visible) window.toFront() }
        App(model)
    }
}

@Composable
fun App(model: AppModel, start: Screen = Screen.Servers) {
    val settings by model.settings.settings.collectAsState()
    var screen by remember { mutableStateOf(start) }
    RemoteMCTheme(settings) {
        when (val s = screen) {
            Screen.Servers -> ServerListScreen(
                model,
                onOpen = { screen = Screen.Server(it) },
                onAdd = { screen = Screen.Pair(it) },
                onSettings = { screen = Screen.Settings },
            )
            is Screen.Pair -> PairScreen(model, s.link, onDone = { id -> screen = if (id != null) Screen.Server(id) else Screen.Servers })
            is Screen.Server -> {
                val profile = model.servers.get(s.id)
                if (profile == null) screen = Screen.Servers
                else ServerScreen(model, profile, onBack = { screen = Screen.Servers })
            }
            Screen.Settings -> SettingsScreen(model, onBack = { screen = Screen.Servers })
        }
    }
}
