package de.remotemc.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.remotemc.app.data.SecureBox
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerRepositoryTest {

    private object PlainBox : SecureBox {
        override fun encrypt(plain: String) = plain
        override fun decrypt(encrypted: String) = encrypted
    }

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test", Context.MODE_PRIVATE)
    private fun repo() = ServerRepository(prefs, PlainBox)
    private fun names(repo: ServerRepository) = repo.servers.value.map { it.name }

    private val a = ServerProfile(name = "Alpha", host = "a.example.com", token = "t")
    private val b = ServerProfile(name = "Beta", host = "192.168.1.5", token = "t")
    private val c = ServerProfile(name = "Gamma", host = "c.example.com", token = "t")

    @Test
    fun moveSortsAndIsSaved() {
        val repo = repo()
        listOf(a, b, c).forEach(repo::upsert)
        repo.move(c.id, a.id)
        assertEquals(listOf("Gamma", "Alpha", "Beta"), names(repo))
        repo.move(c.id, b.id)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), names(repo))
        assertEquals(listOf("Alpha", "Beta", "Gamma"), names(repo()))
    }

    @Test
    fun pinsAndFoldersAreSaved() {
        val repo = repo()
        listOf(a, b, c).forEach(repo::upsert)
        repo.setPinned(a.id, true)
        repo.setFolder(b.id, " Friends ")
        repo.setFolder(c.id, "Friends")
        repo.renameFolder("Friends", "Family")

        val loaded = repo().servers.value.associateBy { it.name }
        assertTrue(loaded.getValue("Alpha").pinned)
        assertEquals("Family", loaded.getValue("Beta").folder)
        assertEquals("Family", loaded.getValue("Gamma").folder)

        repo.removeFolder("Family")
        assertTrue(repo.servers.value.all { it.folder.isEmpty() })
    }

    @Test
    fun searchMatchesNameAddressAndFolder() {
        val friend = b.copy(folder = "Friends")
        assertTrue(friend.matches("beta"))
        assertTrue(friend.matches("192.168"))
        assertTrue(friend.matches("1.5:8765"))
        assertTrue(friend.matches("friend"))
        assertTrue(friend.matches("  "))
        assertFalse(friend.matches("gamma"))
    }
}
