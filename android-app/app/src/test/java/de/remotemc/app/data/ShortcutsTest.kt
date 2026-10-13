package de.remotemc.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ShortcutsTest {
    private val restart = Macro(
        id = "m1", name = "Restart",
        steps = listOf(MacroStep("say Restart in 10s"), MacroStep("save-all", 10), MacroStep("restart", 2)),
        confirm = true,
    )
    private val shortcuts = Shortcuts(
        favorites = listOf(Favorite("f1", "tp {player} 0 100 0"), Favorite("f2", "stop", confirm = true)),
        macros = listOf(restart),
    )

    @Test
    fun roundTrip() {
        assertEquals(shortcuts, Shortcuts.fromJson(JSONObject(shortcuts.toJson().toString())))
        assertEquals(12, restart.duration)
    }

    @Test
    fun brokenEntriesAreDropped() {
        val json = JSONObject("""{"favorites":[{"command":"  "},{"command":"list"}],"macros":[{"name":"x","steps":[{"command":""}]},{"name":"y","steps":[{"command":"a","wait":99999}]}]}""")
        val s = Shortcuts.fromJson(json)
        assertEquals(listOf("list"), s.favorites.map { it.command })
        assertEquals(listOf("y"), s.macros.map { it.name })
        assertEquals(Shortcuts.MAX_WAIT, s.macros.single().steps.single().waitSeconds)
    }

    @Test
    fun editing() {
        val changed = shortcuts.upsert(Favorite("f2", "stop", confirm = false)).upsert(Favorite("f3", "save-all"))
        assertEquals(listOf("f1", "f2", "f3"), changed.favorites.map { it.id })
        assertEquals(false, changed.favorites[1].confirm)
        assertEquals(listOf("f3", "f1", "f2"), changed.moveFavorite(2, 0).favorites.map { it.id })
        assertEquals(listOf("f1", "f3"), changed.removeFavorite("f2").favorites.map { it.id })
        assertTrue(changed.removeMacro("m1").macros.isEmpty())
    }

    @Test
    fun onlyFavoritesWithoutQuestionsFitTheWidget() {
        assertEquals(true, Favorite(command = "save-all", widget = true).fitsWidget)
        assertEquals(false, Favorite(command = "stop", confirm = true).fitsWidget)
        assertEquals(false, Favorite(command = "kick {player}").fitsWidget)
        val s = Shortcuts(favorites = listOf(Favorite("f", "save-all", widget = true)))
        assertEquals(s, Shortcuts.fromJson(JSONObject(s.toJson().toString())))
    }

    @Test
    fun placeholders() {
        assertEquals(listOf("{player}", "{text}"), Placeholders.find("msg {Spieler} {text} – {player}"))
        assertEquals(listOf("{player}", "{player2}"), Placeholders.find("tp {player} {player2}"))
        assertEquals("msg Steve hi – Steve", Placeholders.fill("msg {spieler} {text} – {player}", mapOf("{player}" to "Steve", "{text}" to "hi")))
        assertEquals("tp Steve {player2}", Placeholders.fill("tp {player} {player2}", mapOf("{player}" to "Steve")))
        assertEquals(listOf("{player}", "{text}"), Macro(name = "m", steps = listOf(MacroStep("kick {player}"), MacroStep("say {text} {player}"))).placeholders)
    }

    @Test
    fun storePerServer() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("test-shortcuts", 0)
        val store = ShortcutStore(prefs)
        assertEquals(Shortcuts(), store.load("a"))
        store.save("a", shortcuts)
        assertEquals(shortcuts, store.load("a"))
        assertEquals(Shortcuts(), store.load("b"))
        store.delete("a")
        assertEquals(Shortcuts(), store.load("a"))
    }

    @Test
    fun catalogHidesAppFeaturesAndGroupsByPlugin() {
        val commands = listOf(
            CommandInfo("gamemode", null, null, emptyList(), "Minecraft"),
            CommandInfo("save-all", "Saves the world", null, emptyList(), "Minecraft"),
            CommandInfo("lp", "LuckPerms", null, listOf("perms"), "LuckPerms"),
            CommandInfo("essentials", null, null, emptyList(), "Essentials"),
            CommandInfo("tps", null, null, emptyList(), "Paper"),
            CommandInfo("eban", null, null, listOf("ban"), "Essentials"),
            CommandInfo("old", null, null, emptyList(), null),
        )
        val groups = CommandCatalog.grouped(commands, "")
        assertEquals(listOf("Minecraft", "Paper", "Server", "Essentials", "LuckPerms"), groups.map { it.first })
        assertEquals(listOf("save-all"), groups.first().second.map { it.name })
        assertEquals(listOf("lp"), CommandCatalog.grouped(commands, "/perm").flatMap { it.second }.map { it.name })
        assertEquals(listOf("save-all"), CommandCatalog.grouped(commands, "world").flatMap { it.second }.map { it.name })
    }
}
