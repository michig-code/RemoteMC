package de.remotemc.desktop

import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
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

        val history = CommandHistory(File(dir, "h.json"))
        history.add("s", "time set day")
        history.add("s", "save-all")
        history.add("s", "time set day")
        assertEquals(listOf("time set day", "save-all"), CommandHistory(File(dir, "h.json")).load("s"))
    }
}
