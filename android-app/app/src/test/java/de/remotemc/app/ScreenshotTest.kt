package de.remotemc.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import de.remotemc.app.data.AppSettings
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.WorldInfo
import de.remotemc.app.net.PingInfo
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import de.remotemc.app.ui.Connection
import de.remotemc.app.ui.HistoryCard
import de.remotemc.app.ui.ConsoleEntry
import de.remotemc.app.ui.OnboardingScreen
import de.remotemc.app.ui.ServerActions
import de.remotemc.app.ui.ServerListScreen
import de.remotemc.app.ui.ServerScreenContent
import de.remotemc.app.ui.ServerUiState
import de.remotemc.app.ui.SettingsScreen
import de.remotemc.app.ui.Tab
import de.remotemc.app.ui.components.LocalLoadPlayerHeads
import de.remotemc.app.ui.pair.PairScreen
import de.remotemc.app.ui.pair.PairStep
import de.remotemc.app.ui.pair.PairViewModel
import de.remotemc.app.ui.theme.RemoteMCTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens with sample data. Images live in src/test/screenshots.
 * Record: ./gradlew testDebugUnitTest -Proborazzi.test.record=true
 * Verify: ./gradlew testDebugUnitTest -Proborazzi.test.verify=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val status = ServerStatus(
        motd = "Welcome to Michi's survival server!", version = "1.21.4-232 (MC: 1.21.4)",
        minecraftVersion = "1.21.4", platform = "Paper", pluginVersion = "1.0.0",
        onlinePlayers = 3, maxPlayers = 20, tps = listOf(19.8, 19.6, 17.2), mspt = 12.4,
        memoryUsedMb = 2860, memoryMaxMb = 4096, uptimeSeconds = 93_000,
        features = setOf("history", "moderation", "worlds", "plugins", "schedules", "teleport"),
        worlds = listOf(
            WorldInfo("world", "NORMAL", 2, 812, 1530),
            WorldInfo("world_nether", "NETHER", 1, 210, 340),
            WorldInfo("world_the_end", "THE_END", 0, 49, 12),
        ),
    )
    private val players = listOf(
        PlayerInfo("Steve", "1", "world", 120, 64, -340, 20.0, 20.0, 18, 31, 24, "SURVIVAL", true),
        PlayerInfo("Alex_Builds", "2", "world", -12, 70, 88, 13.5, 20.0, 12, 8, 96, "CREATIVE", false),
        PlayerInfo("Notch", "3", "world_nether", 5, 40, 5, 6.0, 20.0, 4, 54, 210, "SURVIVAL", false),
    )
    private val console = listOf(
        ConsoleEntry("1", "Starting minecraft server version 1.21.4", "INFO", 0),
        ConsoleEntry("2", "Done (15.928s)! For help, type \"help\"", "INFO", 1000),
        ConsoleEntry("3", "Steve joined the game", "INFO", 2000),
        ConsoleEntry("4", "<Steve> hi everyone!", "INFO", 3000),
        ConsoleEntry("5", "Can't keep up! Is the server overloaded? Running 2034ms or 40 ticks behind", "WARN", 4000),
        ConsoleEntry("6", "[EssentialsX] Loaded 42 kits", "INFO", 4500),
        ConsoleEntry("7", "[RemoteMC] Pixel 8 paired as admin.", "INFO", 4600, "§8[§aRemote§2MC§8] §aPixel 8 paired as admin."),
        ConsoleEntry("8", "> /time set day", "COMMAND", 5000),
        ConsoleEntry("9", "Set the time to 1000", "OUTPUT", 5000),
        ConsoleEntry("10", "> /gamemode creative Nobody", "COMMAND", 6000),
        ConsoleEntry("11", "No player was found", "OUTPUT", 6000, "§cNo player was found"),
        ConsoleEntry("12", "--------- Help: Index (1/21) --------------------", "OUTPUT", 7000, "§e--------- §fHelp: Index (1/21) §e--------------------"),
    )
    private val chat = listOf(
        ChatEntry(1, 0, ChatEntry.Type.JOIN, "Steve", ""),
        ChatEntry(2, 1000, ChatEntry.Type.CHAT, "Steve", "hi everyone!"),
        ChatEntry(3, 2000, ChatEntry.Type.CHAT, "Alex_Builds", "who wants to help with the castle?"),
        ChatEntry(4, 3000, ChatEntry.Type.BROADCAST, "Pixel 8", "Restart at 4:00 am"),
        ChatEntry(5, 4000, ChatEntry.Type.QUIT, "Notch", ""),
    )
    private val actions = ServerActions({}, {}, {}, {}, { _, _, _, _ -> }, {})
    private val servers = listOf(
        ServerProfile(name = "Survival", host = "mc.example.com", token = "x", pinned = true),
        ServerProfile(name = "Creative world", host = "192.168.178.20", port = 8766, token = "x", role = Role.MODERATOR),
        ServerProfile(name = "Test server", host = "10.0.0.5", token = "x", tls = false, folder = "Friends"),
        ServerProfile(name = "Skyblock", host = "sky.example.net", token = "x", folder = "Friends"),
    )

    private fun shot(name: String, dark: Boolean = true, hue: Float? = null, content: @Composable () -> Unit) {
        rule.setContent {
            RemoteMCTheme(darkTheme = dark, accentHue = hue) {
                CompositionLocalProvider(LocalLoadPlayerHeads provides false) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(3000)
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    @Suppress("ComposableNaming")
    private fun server(tab: Tab, role: Role = Role.ADMIN, error: Throwable? = null, st: ServerStatus? = status, connection: Connection = Connection.LIVE) =
        ServerScreenContent(
            ServerUiState("Survival", st, players, console, chat, connection, error, false, role),
            actions, initialTab = tab,
        )

    private fun list(query: String = "") = @Composable {
        ServerListScreen(
            servers, {}, {}, { _, _ -> }, {}, {}, {},
            probe = { if (it.port == 8765 && it.tls && it.folder.isEmpty()) status else throw java.io.IOException("down") },
            initialQuery = query,
        )
    }

    @Test fun serverList() = shot("01_servers", content = list())
    @Test fun serverListLight() = shot("02_servers_light", dark = false, content = list())
    @Test fun serverSearch() = shot("02b_servers_search", content = list("192.168"))
    @Test fun dashboard() = shot("03_dashboard") { server(Tab.Dashboard) }
    @Test fun dashboardLight() = shot("04_dashboard_light", dark = false) { server(Tab.Dashboard) }
    @Test fun players() = shot("05_players") { server(Tab.Players) }
    @Test fun playersSelected() = shot("05b_players_selected") {
        Scaffoldless {
            de.remotemc.app.ui.PlayersTab(
                players, Role.ADMIN, { _, _, _, _ -> },
                onBatch = { _, _, _ -> }, canWhitelist = true,
                initialSelection = setOf(players[0].name, players[2].name),
            )
        }
    }

    @Test fun inventory() = shot("05c_inventory") {
        val inv = de.remotemc.app.data.PlayerInventory.fromJson(org.json.JSONObject("""
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
             "enderChest":[]}
        """.trimIndent()))
        Scaffoldless { de.remotemc.app.ui.InventoryContent("Steve", { inv }, initial = inv) }
    }

    @Test fun consoleTab() = shot("06_console") {
        ServerScreenContent(
            ServerUiState("Survival", status, players, console, chat, Connection.LIVE, null, false, Role.ADMIN,
                history = listOf("time set day", "weather clear", "whitelist add Alex_Builds", "save-all")),
            actions, initialTab = Tab.Console,
        )
    }

    @Test fun consoleSuggestions() = shot("06b_console_suggestions") {
        Scaffoldless {
            de.remotemc.app.ui.ConsoleTab(
                entries = console,
                canRunCommands = true,
                suggestions = listOf(
                    de.remotemc.app.ui.Suggestion("gamemode", "Sets a player's game mode"),
                    de.remotemc.app.ui.Suggestion("gamerule", "Sets or queries a game rule value"),
                ),
                usageHint = null,
                onCommand = {},
                initialInput = "gam",
            )
        }
    }

    @Test fun consoleArguments() = shot("06c_console_arguments") {
        Scaffoldless {
            de.remotemc.app.ui.ConsoleTab(
                entries = console,
                canRunCommands = true,
                suggestions = listOf("adventure", "creative", "spectator", "survival").map { de.remotemc.app.ui.Suggestion(it) },
                usageHint = "/gamemode <gamemode> [target]",
                onCommand = {},
                initialInput = "gamemode ",
            )
        }
    }

    private val menuCommands = listOf(
        de.remotemc.app.data.CommandInfo("gamemode", "Sets a player's game mode", null, emptyList(), "Minecraft"),
        de.remotemc.app.data.CommandInfo("save-all", "Saves the server to disk", null, emptyList(), "Minecraft"),
        de.remotemc.app.data.CommandInfo("seed", "Displays the world seed", null, emptyList(), "Minecraft"),
        de.remotemc.app.data.CommandInfo("tps", "Gets the current ticks per second", null, emptyList(), "Paper"),
        de.remotemc.app.data.CommandInfo("lp", "Manage permissions", "/lp user <name> permission set <node>", listOf("perms"), "LuckPerms"),
        de.remotemc.app.data.CommandInfo("remotemc", "RemoteMC admin command", "/remotemc pair|info|token|reload", listOf("rmc"), "RemoteMC"),
    )
    private val menuShortcuts = de.remotemc.app.data.Shortcuts(
        favorites = listOf(
            de.remotemc.app.data.Favorite("f1", "tp {player} 0 100 0"),
            de.remotemc.app.data.Favorite("f2", "lp user {player} parent set vip"),
            de.remotemc.app.data.Favorite("f3", "stop", confirm = true),
        ),
        macros = listOf(
            de.remotemc.app.data.Macro("m1", "Restart", listOf(
                de.remotemc.app.data.MacroStep("say Restart in 10 seconds"),
                de.remotemc.app.data.MacroStep("save-all", 10),
                de.remotemc.app.data.MacroStep("restart", 2),
            ), confirm = true),
            de.remotemc.app.data.Macro("m2", "Morning", listOf(de.remotemc.app.data.MacroStep("time set day"), de.remotemc.app.data.MacroStep("weather clear"))),
        ),
    )

    private fun menu(running: String? = null) = de.remotemc.app.ui.CommandMenuActions(
        menuCommands, menuShortcuts, players.map { it.name }, running, {}, { _, _ -> }, {},
    )

    @Test fun consoleSearch() = shot("06h_console_search") {
        Scaffoldless {
            de.remotemc.app.ui.ConsoleTab(entries = console, canRunCommands = true, onCommand = {}, initialSearch = "server")
        }
    }

    @Test fun consoleWarnings() = shot("06i_console_warnings", dark = false) {
        Scaffoldless {
            de.remotemc.app.ui.ConsoleTab(entries = console, canRunCommands = true, onCommand = {}, initialLevel = de.remotemc.app.ui.LevelFilter.WARNINGS)
        }
    }

    @Test fun commandMenu() = shot("06d_command_menu") {
        de.remotemc.app.ui.CommandMenuSheet(menu(), "", {}, {}, {}, inSheet = false)
    }

    @Test fun commandFavorites() = shot("06e_command_favorites") {
        de.remotemc.app.ui.CommandMenuSheet(menu(), "", {}, {}, {}, inSheet = false, initialTab = 1)
    }

    @Test fun commandMacros() = shot("06f_command_macros", dark = false) {
        de.remotemc.app.ui.CommandMenuSheet(menu(running = "Restart"), "", {}, {}, {}, inSheet = false, initialTab = 2)
    }

    @Composable
    @Suppress("FunctionName")
    private fun Scaffoldless(content: @Composable () -> Unit) =
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
    @Test fun chatTab() = shot("07_chat") { server(Tab.Chat) }
    @Test fun viewerRole() = shot("08_viewer") { server(Tab.Dashboard, role = Role.VIEWER, st = status.copy(tps = emptyList(), mspt = null, platform = "Folia")) }
    @Test fun offline() = shot("09_offline") {
        server(Tab.Dashboard, error = de.remotemc.app.net.ApiError.Unreachable("x"), st = null, connection = Connection.OFFLINE)
    }
    @Test fun certificateChanged() = shot("10_certificate_changed") {
        server(Tab.Dashboard, error = de.remotemc.app.net.ApiError.CertificateChanged("x"))
    }

    @Test fun pairChoose() = shot("11_pair") {
        PairScreen(PairViewModel(RuntimeEnvironment.getApplication()), {}, {}, {})
    }

    @Test fun pairConfirm() = shot("12_pair_confirm") {
        val vm = PairViewModel(RuntimeEnvironment.getApplication()).apply {
            serverName = "Survival"
            deviceName = "Pixel 8"
            step = PairStep.Confirm("mc.example.com", 8765, true, PingInfo("1.0.0", 1, "Paper"),
                "X8Z7vZ4QM1IFpAkiY4T3PF5DKiok9ifVqw6sAUAyJBM", verified = false, mismatch = false, code = "123456", token = null)
        }
        PairScreen(vm, {}, {}, {})
    }

    @Test fun onboarding() = shot("13_onboarding") { OnboardingScreen("Pixel 8", {}) }
    @Test fun onboardingName() = shot("13b_onboarding_name") { OnboardingScreen("Pixel 8", {}, onlyName = true) }
    @Test fun history() = shot("20_history") {
        val now = 1_760_000_000_000L
        val points = (0 until 288).map { i ->
            val t = now - (288 - i) * 300_000L
            val lag = if (i in 150..170) 7.0 else 0.0
            de.remotemc.app.data.HistoryPoint(
                t, if (i in 200..215) null else 19.6 - lag - (i % 7) * 0.1, 8.0 + lag * 4, 2800L + (i % 40) * 20,
                (3 + 8 * kotlin.math.sin(i / 288.0 * 2 * Math.PI).coerceAtLeast(0.0)).toInt(),
            )
        }.filterIndexed { i, _ -> i !in 230..245 } // server was off
        val data = de.remotemc.app.data.PerformanceHistory(now - 86_400_000L, now, 300_000L, points)
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) { HistoryCard({ data }) }
        }
    }
    private val moderation = de.remotemc.app.ui.ModerationActions(
        whitelist = { de.remotemc.app.data.Whitelist(true, listOf("Alex_Builds", "flatsch", "Micheeee14", "Notch")) },
        setWhitelist = { error("unused") },
        addToWhitelist = { error("unused") },
        removeFromWhitelist = { error("unused") },
        bans = {
            listOf(
                de.remotemc.app.data.BanInfo("Griefer123", "Griefing at spawn", "Micheeee14", 1_760_000_000_000L, null),
                de.remotemc.app.data.BanInfo("SpamBot", "Spam", "Server", 1_759_000_000_000L, 1_762_000_000_000L),
            )
        },
        pardon = { error("unused") },
    )

    /** An older plugin without the feature list: only online players, no whitelist / ban tabs. */
    @Test fun olderPlugin() = shot("23_older_plugin") {
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            de.remotemc.app.ui.PlayersSection(
                ServerUiState("Survival", status.copy(features = emptySet()), players, console, chat, Connection.LIVE, null, false, Role.ADMIN),
                ServerActions({}, {}, {}, {}, { _, _, _, _ -> }, {}, moderation = moderation),
            )
        }
    }

    private val fileActions = de.remotemc.app.ui.FileActions(
        list = { path ->
            val now = 1_760_000_000_000L
            if (path.isEmpty()) {
                de.remotemc.app.data.FileList("", listOf(
                    de.remotemc.app.data.FileEntry("logs", "logs", true, 0, now),
                    de.remotemc.app.data.FileEntry("plugins", "plugins", true, 0, now),
                    de.remotemc.app.data.FileEntry("world", "world", true, 0, now),
                    de.remotemc.app.data.FileEntry("banned-players.json", "banned-players.json", false, 312, now),
                    de.remotemc.app.data.FileEntry("bukkit.yml", "bukkit.yml", false, 1_204, now),
                    de.remotemc.app.data.FileEntry("paper-1.21.4.jar", "paper-1.21.4.jar", false, 52_428_800, now),
                    de.remotemc.app.data.FileEntry("server.properties", "server.properties", false, 1_402, now),
                ))
            } else {
                de.remotemc.app.data.FileList(path, emptyList())
            }
        },
        read = { de.remotemc.app.data.TextFile(it, "#Minecraft server properties\nmotd=Welcome to Michi's server!\nmax-players=20\ndifficulty=normal\npvp=true\nview-distance=10\nwhite-list=true\nonline-mode=true\n", 0) },
        write = { _, _ -> },
        delete = { null },
        createFolder = {},
        nameOf = { "Plugin.jar" },
        upload = { dir, _ -> "$dir/Plugin.jar" },
    )

    @Test fun dashboardEdit() = shot("26_dashboard_edit") {
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            de.remotemc.app.ui.DashboardTab(status, null, startEditing = true, layoutText = "players:full,-tps_trend:full,gauges:half,memory:half")
        }
    }

    @Test fun files() = shot("24_files") { de.remotemc.app.ui.FolderView(fileActions, "", {}, {}, {}) }
    @Test fun fileEditor() = shot("25_file_editor") { de.remotemc.app.ui.FileEditor(fileActions, "server.properties") {} }

    @Test fun whitelist() = shot("21_whitelist") {
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            de.remotemc.app.ui.PlayersSection(
                ServerUiState("Survival", status, players, console, chat, Connection.LIVE, null, false, Role.ADMIN),
                ServerActions({}, {}, {}, {}, { _, _, _, _ -> }, {}, moderation = moderation), initialView = 1,
            )
        }
    }

    @Test fun bans() = shot("22_bans") {
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            de.remotemc.app.ui.PlayersSection(
                ServerUiState("Survival", status, players, console, chat, Connection.LIVE, null, false, Role.ADMIN),
                ServerActions({}, {}, {}, {}, { _, _, _, _ -> }, {}, moderation = moderation), initialView = 2,
            )
        }
    }

    @Test fun accentBlue() = shot("18_accent_blue", hue = 212f, content = list())
    @Test fun accentPurpleLight() = shot("19_accent_purple_light", dark = false, hue = 275f) { server(Tab.Dashboard) }
    @Test fun settingsCustomColour() = shot("14b_settings_orange", hue = 24f) {
        SettingsScreen(AppSettings(deviceName = "Pixel 8", accentHue = 24f), {}, {}, versionName = "1.0.0")
    }
    @Test
    @Config(qualifiers = "de-w400dp-h2400dp-xxhdpi")
    fun settingsNotifications() = shot("14c_settings_notifications_de") {
        SettingsScreen(
            AppSettings(
                deviceName = "Pixel 8",
                alerts = de.remotemc.app.data.AlertSettings(enabled = true, joins = true, players = "flatsch, Micheeee14", words = "Michi"),
            ),
            {}, {}, versionName = "1.0.0",
        )
    }
    @Test fun settings() = shot("14_settings") { SettingsScreen(AppSettings(deviceName = "Pixel 8"), {}, {}, versionName = "1.0.0") }

    @Test
    @Config(qualifiers = "de-w400dp-h860dp-xxhdpi")
    fun german() = shot("15_dashboard_de") { server(Tab.Players) }

    @Test
    @Config(qualifiers = "fr-w400dp-h860dp-xxhdpi")
    fun french() = shot("15b_dashboard_fr") { server(Tab.Dashboard) }

    @Test
    @Config(qualifiers = "ru-w400dp-h860dp-xxhdpi")
    fun russian() = shot("15c_players_ru") { server(Tab.Players) }

    @Test
    @Config(qualifiers = "w840dp-h600dp-xhdpi")
    fun tablet() = shot("16_tablet") { server(Tab.Dashboard) }

    @Test
    fun largeFont() {
        RuntimeEnvironment.setFontScale(2f)
        shot("17_large_font") { server(Tab.Players) }
    }
}
