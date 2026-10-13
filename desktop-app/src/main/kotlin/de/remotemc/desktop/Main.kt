package de.remotemc.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
    val icon = remember {
        runCatching { BitmapPainter(ImageIO.read(AppModel::class.java.getResourceAsStream("/icon.png")).toComposeImageBitmap()) }.getOrNull()
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "RemoteMC",
        icon = icon,
        state = rememberWindowState(size = DpSize(1180.dp, 780.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(820, 560)
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
