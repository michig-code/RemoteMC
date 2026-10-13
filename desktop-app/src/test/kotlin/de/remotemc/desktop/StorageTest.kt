package de.remotemc.desktop

import de.remotemc.app.data.AlertSettings
import de.remotemc.app.data.Favorite
import de.remotemc.app.data.Macro
import de.remotemc.app.data.MacroStep
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.Shortcuts
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StorageTest {
    private val dir = Files.createTempDirectory("rmc").toFile()

    @Test
    fun serversSurviveRestart() {
        val file = File(dir, "servers.dat")
        val store = ServerStore(file)
        val a = ServerProfile(name = "Survival", host = "mc.example.com", token = "rmc_secret", role = Role.MODERATOR, fingerprint = "fp")
        store.upsert(a)
        store.upsert(ServerProfile(name = "Test", host = "10.0.0.5", token = "t2", tls = false))
        store.delete(store.servers.value[1].id)

        val again = ServerStore(file).servers.value
        assertEquals(listOf(a), again)
        if (AppDirs.isWindows) assertFalse("rmc_secret" in file.readText(Charsets.ISO_8859_1)) // DPAPI encrypted
    }

    @Test
    fun settingsAndHistory() {
        val settings = SettingsStore(File(dir, "settings.json"))
        settings.update { it.copy(theme = ThemeMode.DARK, accentHue = 212f, deviceName = "Gaming-PC") }
        assertEquals(DesktopSettings(ThemeMode.DARK, 212f, "Gaming-PC"), SettingsStore(File(dir, "settings.json")).settings.value)

        val alerts = AlertSettings(enabled = true, lowTps = false, tpsThreshold = 12, joins = true, players = "Steve, Alex", words = "Michi", quiet = true, quietFrom = 23 * 60, quietTo = 6 * 60 + 30)
        settings.update { it.copy(alerts = alerts) }
        assertEquals(alerts, SettingsStore(File(dir, "settings.json")).settings.value.alerts)
        settings.update { it.copy(animations = false) }
        assertEquals(false, SettingsStore(File(dir, "settings.json")).settings.value.animations)

        val history = CommandHistory(File(dir, "h.json"))
        history.add("s", "time set day")
        history.add("s", "save-all")
        history.add("s", "time set day")
        assertEquals(listOf("time set day", "save-all"), CommandHistory(File(dir, "h.json")).load("s"))
    }

    @Test
    fun shortcutsAndNotesPerServer() {
        val shortcuts = Shortcuts(
            favorites = listOf(Favorite("f", "tp {player} 0 100 0", confirm = true)),
            macros = listOf(Macro("m", "Restart", listOf(MacroStep("say bye"), MacroStep("restart", 10)))),
        )
        ShortcutFiles(File(dir, "shortcuts.json")).save("a", shortcuts)
        val notes = NoteFiles(File(dir, "notes.json"))
        notes.set("a", "uuid-1", "griefed spawn")
        notes.set("b", "uuid-1", "builder")

        // Read back as after a restart.
        val extras = ServerExtras(ShortcutFiles(File(dir, "shortcuts.json")), NoteFiles(File(dir, "notes.json")), null)
        assertEquals(shortcuts, extras.shortcuts.load("a"))
        assertEquals(Shortcuts(), extras.shortcuts.load("b"))
        assertEquals("griefed spawn", extras.notes.get("a", "uuid-1"))
        extras.forget("a")
        assertEquals(Shortcuts(), ShortcutFiles(File(dir, "shortcuts.json")).load("a"))
        assertEquals("", NoteFiles(File(dir, "notes.json")).get("a", "uuid-1"))
        assertEquals("builder", NoteFiles(File(dir, "notes.json")).get("b", "uuid-1"))
        extras.notes.set("b", "uuid-1", "  ")
        assertEquals("", extras.notes.get("b", "uuid-1"))
    }
}
