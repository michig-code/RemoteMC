package de.remotemc.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
class ServerSnapshotTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Reversible stand-in for the Keystore, so the test can see that the file is not plain text. */
    private val box = object : SecureBox {
        override fun encrypt(plain: String) = plain.reversed()
        override fun decrypt(encrypted: String) = encrypted.reversed()
    }

    private val snapshot = ServerSnapshot(
        savedAt = 1_700_000_000_000,
        session = Session("Pixel", Role.MODERATOR, commandsEnabled = false, capturesCommandOutput = true),
        status = ServerStatus(
            motd = "§aHello", version = "git-Paper-123", minecraftVersion = "1.21.4", platform = "Paper",
            pluginVersion = "1.0.0", onlinePlayers = 1, maxPlayers = 20, tps = listOf(20.0, 19.5, 19.9), mspt = 3.5,
            memoryUsedMb = 812, memoryMaxMb = 2048, uptimeSeconds = 3600,
            worlds = listOf(WorldInfo("world", "NORMAL", 1, 441, 120)), features = setOf("history", "files"),
        ),
        players = listOf(
            PlayerInfo("Steve", "uuid-1", "world", 1, 64, -3, 18.0, 20.0, 17, 5, 42, "SURVIVAL", false, "https://textures.minecraft.net/texture/abc", 7200),
        ),
        console = listOf(ConsoleLine(41, 1000, "INFO", "Steve joined the game", "§eSteve joined the game")),
        chat = listOf(ChatEntry(7, 1001, ChatEntry.Type.CHAT, "Steve", "hi")),
        commands = listOf(CommandInfo("gamemode", "Sets a game mode", "/gamemode <mode> [player]", listOf("gm"))),
    )

    @Test
    fun roundTripKeepsEverything() {
        assertEquals(snapshot, ServerSnapshot.fromJson(JSONObject(snapshot.toJson().toString())))
    }

    @Test
    fun emptyParts() {
        val empty = ServerSnapshot(1, null, null, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, ServerSnapshot.fromJson(JSONObject(empty.toJson().toString())))
        val noMspt = snapshot.copy(status = snapshot.status!!.copy(mspt = null))
        assertNull(ServerSnapshot.fromJson(noMspt.toJson())!!.status!!.mspt)
    }

    @Test
    fun keepsOnlyTheNewestLines() {
        val many = snapshot.copy(console = List(500) { ConsoleLine(it.toLong(), it.toLong(), "INFO", "line $it") })
        val restored = ServerSnapshot.fromJson(many.toJson())!!
        assertEquals(ServerSnapshot.MAX_CONSOLE, restored.console.size)
        assertEquals("line 499", restored.console.last().message)
    }

    @Test
    fun otherVersionsAndBrokenDataAreIgnored() {
        assertNull(ServerSnapshot.fromJson(snapshot.toJson().put("version", 99)))
        assertNull(ServerSnapshot.fromJson(JSONObject().put("version", ServerSnapshot.VERSION)))
    }

    @Test
    fun storeSavesEncryptedLoadsAndDeletes() {
        val store = SnapshotStore(folder.root, box)
        assertNull(store.load("a1b2-c3"))
        store.save("a1b2-c3", snapshot)
        val file = folder.root.listFiles()!!.single()
        assertFalse("console text must not be stored readable", file.readText().contains("joined the game"))
        assertEquals(snapshot, store.load("a1b2-c3"))
        file.writeText("garbage")
        assertNull(store.load("a1b2-c3"))
        store.delete("a1b2-c3")
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test
    fun serverIdCannotEscapeTheFolder() {
        val store = SnapshotStore(folder.newFolder("snaps"), box)
        store.save("../../evil", snapshot)
        assertTrue(folder.root.resolve("snaps").listFiles()!!.single().name.matches(Regex("[A-Za-z0-9-]+\\.snap")))
    }
}
