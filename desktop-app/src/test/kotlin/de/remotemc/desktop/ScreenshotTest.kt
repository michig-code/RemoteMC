package de.remotemc.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.Session
import de.remotemc.app.data.WorldInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.test.Test

/** Renders the screens without a display so they can be looked at (build/screenshots). */
class ScreenshotTest {
    private val dir = Files.createTempDirectory("rmc").toFile()
    private val out = File("build/screenshots").apply { mkdirs() }

    private val model = AppModel(ServerStore(File(dir, "servers.dat")), SettingsStore(File(dir, "settings.json")), CommandHistory(File(dir, "h.json")))
    private val survival = ServerProfile(name = "Survival", host = "mc.example.com", token = "x")

    init {
        LoadHeads = false
        Locale.setDefault(Locale.ENGLISH)
        model.servers.upsert(survival)
        model.servers.upsert(ServerProfile(name = "Creative world", host = "192.168.178.20", port = 8766, token = "x", role = Role.MODERATOR))
        model.servers.upsert(ServerProfile(name = "Test server", host = "10.0.0.5", token = "x", tls = false))
    }

    private val sample = ServerStatus(
        "Welcome to Michi's survival server!", "git-Paper-123", "1.21.4", "Paper", "1.0.0", 3, 20, listOf(19.8, 19.6, 17.2), 12.4,
        2850, 4096, 90_000, features = setOf("history", "moderation", "worlds", "plugins", "schedules", "teleport"), worlds = listOf(WorldInfo("world", "NORMAL", 2, 812, 1450), WorldInfo("world_nether", "NETHER", 1, 120, 230), WorldInfo("world_the_end", "THE_END", 0, 40, 12)),
    )
    private val samplePlayers = listOf(
        PlayerInfo("Steve", "1", "world", 120, 64, -340, 20.0, 20.0, 18, 30, 24, "SURVIVAL", true),
        PlayerInfo("Alex_Builds", "2", "world", -12, 70, 88, 14.0, 20.0, 12, 7, 96, "CREATIVE", false),
        PlayerInfo("Notch", "3", "world_nether", 5, 40, 5, 6.0, 20.0, 20, 2, 210, "SURVIVAL", false),
    )
    private val now = 1_760_000_000_000L
    private val sampleConsole = listOf(
        ConsoleEntry("1", "Starting minecraft server version 1.21.4", "INFO", now),
        ConsoleEntry("2", "§a[RemoteMC] §7API listening on port 8765", "INFO", now, "§a[RemoteMC] §7API listening on port 8765"),
        ConsoleEntry("3", "Can't keep up! Is the server overloaded?", "WARN", now),
        ConsoleEntry("4", "> /time set day", "COMMAND", now),
        ConsoleEntry("5", "Set the time to 1000", "OUTPUT", now),
    )
    private val sampleChat = listOf(
        ChatEntry(1, now, ChatEntry.Type.JOIN, "Alex_Builds", ""),
        ChatEntry(2, now, ChatEntry.Type.CHAT, "Steve", "anyone up for the dragon fight?"),
        ChatEntry(3, now, ChatEntry.Type.BROADCAST, "Server", "Restart in 10 minutes"),
    )

    private fun session() = ServerSession(survival, CoroutineScope(Dispatchers.Unconfined), model.servers, model.history).apply {
        showPreview(sample, samplePlayers, sampleConsole, sampleChat, Session("PC", Role.ADMIN, true, true), listOf("time set day", "weather clear", "save-all"))
    }

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        model.settings.update { it.copy(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        val scene = ImageComposeScene(1180, 780, Density(1f)) { RemoteMCTheme(model.settings.settings.value) { content() } }
        repeat(3) { scene.render(it * 500_000_000L) }
        val image = scene.render(2_000_000_000L)
        File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test fun servers() = shot("01_servers") { ServerListScreen(model, {}, {}, {}, probe = { if (it.port == 8765 && it.tls) sample else error("down") }) }
    @Test fun serversLight() = shot("02_servers_light", dark = false) { ServerListScreen(model, {}, {}, {}, probe = { sample }) }
    @Test fun overview() = shot("03_overview") { ServerContent(session(), {}, Pane.OVERVIEW) }
    @Test fun playersPane() = shot("04_players") { ServerContent(session(), {}, Pane.PLAYERS) }
    @Test fun consolePane() = shot("05_console") { ServerContent(session(), {}, Pane.CONSOLE) }
    @Test fun chatPane() = shot("06_chat") { ServerContent(session(), {}, Pane.CHAT) }
    @Test fun pair() = shot("07_pair") { PairScreen(model, "remotemc://mc.example.com:8765?code=123456&fp=abc", {}) }
    @Test fun settings() = shot("08_settings") { SettingsScreen(model, {}, "1.0.0") }
}
