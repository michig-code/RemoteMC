package de.remotemc.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import de.remotemc.app.data.BanInfo
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.GameRuleInfo
import de.remotemc.app.data.PluginInfo
import de.remotemc.app.data.ScheduleInfo
import de.remotemc.app.data.ScheduleList
import de.remotemc.app.data.Whitelist
import de.remotemc.app.data.WorldDetails
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.Session
import de.remotemc.app.data.WorldInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONObject
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

    private fun session(chat: List<ChatEntry> = sampleChat) = ServerSession(survival, CoroutineScope(Dispatchers.Unconfined), model.servers, model.history).apply {
        showPreview(sample, samplePlayers, sampleConsole, chat, Session("PC", Role.ADMIN, true, true), listOf("time set day", "weather clear", "save-all"))
        manage = SampleManagement(now)
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
    @Test fun whitelist() = shot("09_whitelist") { ServerContent(session(), {}, Pane.PLAYERS, playersView = 1) }
    @Test fun bans() = shot("10_bans", dark = false) { ServerContent(session(), {}, Pane.PLAYERS, playersView = 2) }
    @Test fun worlds() = shot("11_worlds") { ServerContent(session(), {}, Pane.WORLDS) }
    @Test fun plugins() = shot("12_plugins") { ServerContent(session(), {}, Pane.PLUGINS) }
    @Test fun schedules() = shot("13_schedules") { ServerContent(session(), {}, Pane.SCHEDULES) }
    @Test fun chatShort() = shot("14_chat_short") { ServerContent(session(sampleChat.takeLast(1)), {}, Pane.CHAT) }
    @Test fun pair() = shot("07_pair") { PairScreen(model, "remotemc://mc.example.com:8765?code=123456&fp=abc", {}) }
    @Test fun settings() = shot("08_settings") { SettingsScreen(model, {}, "1.0.0") }
}

/** Fixed management data for the screenshots. */
private class SampleManagement(private val now: Long) : Management {
    private val list = Whitelist(true, listOf("Alex_Builds", "Notch", "Steve", "jeb_"))
    override suspend fun whitelist() = list
    override suspend fun setWhitelist(enabled: Boolean) = list.copy(enabled = enabled)
    override suspend fun whitelistAdd(name: String) = list.copy(players = list.players + name)
    override suspend fun whitelistRemove(name: String) = list.copy(players = list.players - name)
    override suspend fun bans() = listOf(
        BanInfo("Griefer42", "Destroyed the spawn", "Steve", now - 3 * 86_400_000L, null),
        BanInfo("xX_Hacker_Xx", "Fly hacks", "Server", now - 86_400_000L, now + 6 * 86_400_000L),
    )
    override suspend fun pardon(name: String) = bans().filter { it.name != name }
    override suspend fun world(name: String) = WorldDetails(
        name, "NORMAL", 13_000, storm = true, thunder = false, difficulty = "NORMAL",
        gameRules = listOf(
            GameRuleInfo("doDaylightCycle", true, "true"), GameRuleInfo("doFireTick", true, "false"),
            GameRuleInfo("doMobSpawning", true, "true"), GameRuleInfo("keepInventory", true, "true"),
            GameRuleInfo("mobGriefing", true, "false"), GameRuleInfo("playersSleepingPercentage", false, "50"),
            GameRuleInfo("randomTickSpeed", false, "3"), GameRuleInfo("spawnRadius", false, "10"),
        ),
    )
    override suspend fun worldAction(name: String, action: String, body: JSONObject) = world(name)
    override suspend fun plugins() = listOf(
        PluginInfo("RemoteMC", "1.0.0", true, "Manage your server from your phone and PC", listOf("michig-code")),
        PluginInfo("EssentialsX", "2.21.0", true, "Provides an essential, core set of commands for Bukkit.", listOf("zenexer", "md_5")),
        PluginInfo("LuckPerms", "5.4.145", true, "A permissions plugin", listOf("Luck")),
        PluginInfo("WorldEdit", "7.3.9", true, "In-game Minecraft map editor", listOf("EngineHub")),
        PluginInfo("OldPlugin", "0.3", false, null, emptyList()),
    )
    override suspend fun schedules() = ScheduleList(
        listOf(
            ScheduleInfo("a", "Nightly restart", "restart", daily = true, minutes = 0, time = "04:00", warn = 5, enabled = true, nextRun = now + 5 * 3_600_000L),
            ScheduleInfo("b", "Save worlds", "save-all", daily = false, minutes = 30, time = "", warn = 0, enabled = true, nextRun = now + 12 * 60_000L),
            ScheduleInfo("c", "Discord reminder", "say Join our Discord!", daily = false, minutes = 60, time = "", warn = 0, enabled = false),
        ),
        "Europe/Berlin", now,
    )
    override suspend fun saveSchedule(task: ScheduleInfo) = schedules()
    override suspend fun scheduleAction(id: String, action: String) = schedules()
}
