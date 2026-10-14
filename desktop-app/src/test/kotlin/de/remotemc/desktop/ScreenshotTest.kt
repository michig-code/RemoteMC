package de.remotemc.desktop

import kotlin.test.assertNotNull
import kotlinx.coroutines.cancel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import de.remotemc.app.ui.ConsoleEntry
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import de.remotemc.app.data.BanInfo
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.FileEntry
import de.remotemc.app.data.FileList
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
import de.remotemc.app.data.TextFile
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

    private val model = AppModel(ServerStore(File(dir, "servers.dat")), SettingsStore(File(dir, "settings.json")), CommandHistory(File(dir, "h.json")), ServerExtras.inMemory())
    private val survival = ServerProfile(name = "Survival", host = "mc.example.com", token = "x")

    init {
        LoadHeads = false
        SplitPrefs.save = false
        // Screenshots show the final state; AnimationTest covers the animations themselves.
        model.settings.update { it.copy(animations = false) }
        Locale.setDefault(Locale.ENGLISH)
        model.servers.upsert(survival)
        model.servers.upsert(ServerProfile(name = "Creative world", host = "192.168.178.20", port = 8766, token = "x", role = Role.MODERATOR))
        model.servers.upsert(ServerProfile(name = "Test server", host = "10.0.0.5", token = "x", tls = false))
    }

    private val sample = ServerStatus(
        "Welcome to the Survival server!", "git-Paper-123", "1.21.4", "Paper", "1.0.0", 3, 20, listOf(19.8, 19.6, 17.2), 12.4,
        2850, 4096, 90_000, features = setOf("history", "moderation", "worlds", "plugins", "schedules", "teleport", "files"), worlds = listOf(WorldInfo("world", "NORMAL", 2, 812, 1450), WorldInfo("world_nether", "NETHER", 1, 120, 230), WorldInfo("world_the_end", "THE_END", 0, 40, 12)),
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
    @Test fun serversFolders() = shot("01b_servers_folders") {
        val d = Files.createTempDirectory("rmc").toFile()
        val m = AppModel(ServerStore(File(d, "servers.dat")), model.settings, CommandHistory(File(d, "h.json")), ServerExtras.inMemory())
        listOf(
            ServerProfile(name = "Survival", host = "mc.example.com", token = "x", pinned = true),
            ServerProfile(name = "Lobby", host = "lobby.example.com", token = "x", folder = "Network", folderPinned = true),
            ServerProfile(name = "Minigames", host = "mini.example.com", token = "x", folder = "Network", folderPinned = true),
            ServerProfile(name = "Creative world", host = "192.168.178.20", port = 8766, token = "x", role = Role.MODERATOR),
            ServerProfile(name = "Test server", host = "10.0.0.5", token = "x", tls = false, folder = "Friends"),
        ).forEach { m.servers.upsert(it) }
        ServerListScreen(m, {}, {}, {}, probe = { if (it.host == "mc.example.com") sample else error("down") })
    }
    @Test fun serversEmpty() = shot("02b_servers_empty") {
        val empty = Files.createTempDirectory("rmc").toFile()
        ServerListScreen(AppModel(ServerStore(File(empty, "servers.dat")), model.settings, CommandHistory(File(empty, "h.json")), ServerExtras.inMemory()), {}, {}, {})
    }
    @Test fun demoServer() {
        // A real session against the built-in demo server: connects, loads and shows its sample data.
        val profile = de.remotemc.app.net.DemoServer.profile("Demo server")
        val scope = CoroutineScope(Dispatchers.Default)
        val live = ServerSession(profile, scope, model.servers, model.history)
        live.start()
        val deadline = System.currentTimeMillis() + 10_000
        while ((live.status == null || live.players.isEmpty()) && System.currentTimeMillis() < deadline) Thread.sleep(100)
        try {
            assertNotNull(live.status, "demo server did not answer")
            shot("25_demo_server") { ServerContent(live, {}, Pane.OVERVIEW) }
        } finally {
            live.stop()
            scope.cancel()
        }
    }
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
    @Test fun files() = shot("15_files") { ServerContent(session(), {}, Pane.FILES) }
    @Test fun filesEditor() = shot("16_file_editor") {
        val s = session()
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            FilesPane(s, initialFile = "server.properties")
        }
    }
    @Test fun overviewEdit() = shot("17_overview_edit") {
        val s = session()
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            OverviewPane(s, loadHistory = null, startEditing = true)
        }
    }
    private val fakeWindow = object : WindowControls {
        override val maximized = false
        override fun minimize() {}
        override fun toggleMaximize() {}
        override fun close() {}
        @androidx.compose.runtime.Composable
        override fun DragArea(modifier: androidx.compose.ui.Modifier, content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) =
            androidx.compose.foundation.layout.Box(modifier, content = content)
    }

    @Test fun tabs() = shot("18_tabs") {
        val tabs = androidx.compose.runtime.remember {
            Tabs(model, CoroutineScope(Dispatchers.Unconfined), persist = false, connect = false).apply {
                model.servers.servers.value.forEach { open(it.id) }
                openSpecial(Tabs.SETTINGS)
                open(model.servers.servers.value.first().id)
            }
        }
        androidx.compose.foundation.layout.Column {
            TitleBar(model, tabs, fakeWindow)
            ServerContent(session(), {}, Pane.OVERVIEW)
        }
    }
    @Test fun playersSelected() = shot("22_players_selected") {
        val s = session()
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            PlayersPane(s, initialSelection = setOf("Steve", "Notch"))
        }
    }
    @Test fun updateToast() = shot("23_update_toast") {
        val updates = androidx.compose.runtime.remember {
            UpdateState(CoroutineScope(Dispatchers.Unconfined)) { false }.apply {
                preview(UpdateInfo("1.1.0", "", "https://github.com", "https://github.com/x.msi", 1, null))
            }
        }
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            ServerContent(session(), {}, Pane.OVERVIEW)
            UpdateToast(updates, {}, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd))
        }
    }
    @Test fun changelog() = shot("24_changelog") {
        androidx.compose.material3.Surface { ChangelogContent("1.0.0", androidx.compose.ui.Modifier.padding(24.dp)) }
    }
    @Test fun consoleSearch() = shot("19_console_search") {
        androidx.compose.material3.Surface { ConsolePane(session(), initialQuery = "server") }
    }

    @Test fun commandMenu() = shot("20_command_menu") {
        val s = session().apply {
            updateShortcuts {
                de.remotemc.app.data.Shortcuts(
                    favorites = listOf(de.remotemc.app.data.Favorite("f1", "tp {player} 0 100 0"), de.remotemc.app.data.Favorite("f2", "stop", confirm = true)),
                    macros = listOf(de.remotemc.app.data.Macro("m1", "Restart", listOf(de.remotemc.app.data.MacroStep("say Restart in 10 s"), de.remotemc.app.data.MacroStep("restart", 10)))),
                )
            }
        }
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(620.dp, 560.dp)) {
            CommandMenuSheet(
                CommandMenuActions(
                    listOf(
                        de.remotemc.app.data.CommandInfo("save-all", "Saves the server to disk", null, emptyList(), "Minecraft"),
                        de.remotemc.app.data.CommandInfo("tps", "Gets the current ticks per second", null, emptyList(), "Paper"),
                        de.remotemc.app.data.CommandInfo("lp", "Manage permissions", "/lp user <name> permission set <node>", listOf("perms"), "LuckPerms"),
                    ),
                    s.shortcuts, listOf("Steve"), null, s::updateShortcuts, s::runMacro, s::stopMacro,
                ),
                "", {}, {}, {}, inSheet = false,
            )
        }
    }

    @Test fun inventory() = shot("21_inventory") {
        val inv = de.remotemc.app.data.PlayerInventory.fromJson(org.json.JSONObject(SAMPLE_INVENTORY))
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(560.dp, 760.dp)) {
            androidx.compose.material3.Surface { InventoryContent("Steve", { inv }, initial = inv) }
        }
    }

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
        PluginInfo("RemoteMC", "1.0.0", true, "Manage your server from your phone and PC", listOf("michig-code"), "plugins/RemoteMC-1.0.0.jar"),
        PluginInfo("EssentialsX", "2.21.0", true, "Provides an essential, core set of commands for Bukkit.", listOf("zenexer", "md_5"), "plugins/EssentialsX-2.21.0.jar"),
        PluginInfo("LuckPerms", "5.4.145", true, "A permissions plugin", listOf("Luck"), "plugins/LuckPerms-Bukkit-5.4.145.jar"),
        PluginInfo("WorldEdit", "7.3.9", true, "In-game Minecraft map editor", listOf("EngineHub"), "plugins/worldedit-bukkit-7.3.9.jar"),
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
    override suspend fun files(path: String) = FileList(
        path,
        listOf(
            FileEntry("logs", "logs", true, 0, now), FileEntry("plugins", "plugins", true, 0, now),
            FileEntry("world", "world", true, 0, now), FileEntry("world_nether", "world_nether", true, 0, now),
            FileEntry("banned-players.json", "banned-players.json", false, 312, now), FileEntry("bukkit.yml", "bukkit.yml", false, 1_204, now),
            FileEntry("paper-1.21.4.jar", "paper-1.21.4.jar", false, 52_428_800, now), FileEntry("server.properties", "server.properties", false, 1_402, now),
            FileEntry("spigot.yml", "spigot.yml", false, 5_120, now),
        ),
    )
    override suspend fun readFile(path: String) = TextFile(
        path,
        "#Minecraft server properties\nmotd=Welcome to the Survival server!\nmax-players=20\ndifficulty=normal\npvp=true\n" +
            "view-distance=10\nsimulation-distance=8\nwhite-list=true\nonline-mode=true\nspawn-protection=16\n",
        now,
    )
    override suspend fun writeFile(path: String, content: String) {}
    override suspend fun deleteFile(path: String): String? = null
    override suspend fun restoreFile(trash: String) {}
    override suspend fun ban(name: String, reason: String) {}
    override suspend fun inventory(player: String) = de.remotemc.app.data.PlayerInventory.fromJson(org.json.JSONObject(SAMPLE_INVENTORY))
    override suspend fun createFolder(path: String) {}
    override suspend fun uploadFile(path: String, file: java.io.File) {}
}

/** An inventory as the plugin sends it (Paper 1.21.4). */
internal const val SAMPLE_INVENTORY = """
{"selectedSlot":1,
 "items":[{"id":"minecraft:diamond_sword","amount":1,"enchants":["sharpness 3","unbreaking 2"],"durability":900,"maxDurability":1561,"slot":0},
          {"id":"minecraft:diamond_pickaxe","amount":1,"durability":200,"maxDurability":1561,"slot":1},
          {"id":"minecraft:cooked_beef","amount":24,"slot":2},{"id":"minecraft:torch","amount":64,"slot":3},
          {"id":"minecraft:oak_log","amount":32,"slot":9},{"id":"minecraft:cobblestone","amount":64,"slot":10},
          {"id":"minecraft:iron_ingot","amount":17,"slot":11},{"id":"minecraft:gold_ingot","amount":5,"slot":12},
          {"id":"minecraft:redstone","amount":40,"slot":20},{"id":"minecraft:name_tag","amount":1,"name":"Bello","slot":27}],
 "armor":[{"id":"minecraft:iron_boots","amount":1,"slot":0},{"id":"minecraft:diamond_chestplate","amount":1,"enchants":["protection 4"],"slot":2},
          {"id":"minecraft:iron_helmet","amount":1,"slot":3}],
 "offhand":[{"id":"minecraft:shield","amount":1,"durability":300,"maxDurability":336,"slot":0}],
 "enderChest":[{"id":"minecraft:emerald","amount":5,"slot":0}]}
"""
