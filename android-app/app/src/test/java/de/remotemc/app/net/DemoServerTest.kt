package de.remotemc.app.net

import de.remotemc.app.data.HistoryRange
import de.remotemc.app.data.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The demo server answers the app's own client like the plugin would, without network. */
@RunWith(RobolectricTestRunner::class)
class DemoServerTest {
    private val api = ApiClient(DemoServer.profile("Demo"))

    @Test
    fun readsLikeARealServer() = runBlocking {
        assertEquals(1, api.ping().apiVersion)
        assertEquals(Role.ADMIN, api.me().role)
        val status = api.status()
        assertTrue(status.onlinePlayers >= 1)
        assertTrue("inventory" in status.features)
        val players = api.players()
        assertTrue(players.any { it.name == "Lena_Builds" })
        assertTrue(api.inventory(players.first().name).items.isNotEmpty())
        assertTrue(api.console(0).isNotEmpty())
        assertTrue(api.chat(0).isNotEmpty())
        assertTrue(api.commands().any { it.name == "gamemode" })
        assertTrue("survival" in api.complete("gamemode su"))
        assertTrue(api.history(HistoryRange.entries.first()).points.isNotEmpty())
        assertTrue(api.plugins().any { it.name == "RemoteMC" })
        assertEquals(2, api.schedules().schedules.size)
        assertTrue(api.readFile("server.properties").content.contains("motd="))
        assertTrue(api.world("world").gameRules.any { it.name == "keepInventory" && it.isBoolean })
    }

    @Test
    fun actionsChangeTheState() = runBlocking {
        val result = api.command("list")
        assertTrue(result.output.single().startsWith("There are"))

        api.playerAction("Mira", "kick", JSONObject().put("reason", "test"))
        assertFalse(api.players().any { it.name == "Mira" })
        val error = runCatching { api.playerAction("Mira", "heal") }.exceptionOrNull()
        assertEquals("player_offline", (error as ApiError.Http).code)

        api.playerAction("Tobi_PvP", "ban", JSONObject().put("reason", "demo"))
        assertTrue(api.bans().any { it.name == "Tobi_PvP" && it.reason == "demo" })
        assertFalse(api.pardon("Tobi_PvP").any { it.name == "Tobi_PvP" })

        assertTrue("NewFriend" in api.whitelistAdd("NewFriend").players)
        assertFalse("NewFriend" in api.whitelistRemove("NewFriend").players)

        assertTrue(api.worldAction("world", "weather", JSONObject().put("weather", "thunder")).thunder)

        val trash = api.deleteFile("eula.txt")
        assertNotNull(trash)
        assertFalse(api.files("").entries.any { it.name == "eula.txt" })
        api.restoreFile(trash!!)
        assertTrue(api.files("").entries.any { it.name == "eula.txt" })
    }

    @Test
    fun liveStreamSendsHelloAndStatus() = runBlocking {
        val events = withTimeout(10_000) { EventStream(DemoServer.profile("Demo")).events().take(2).toList() }
        assertTrue(events[0] is StreamEvent.Hello)
        assertTrue(events[1] is StreamEvent.Status || events[1] is StreamEvent.Console || events[1] is StreamEvent.Chat)
        withTimeout(10_000) { EventStream(DemoServer.profile("Demo")).events().first() }
        Unit
    }
}

/** The whole server screen logic connects to the demo server live, like to a real one. */
@RunWith(RobolectricTestRunner::class)
class DemoServerScreenTest {
    @Test
    fun viewModelGoesLive() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val vm = de.remotemc.app.ui.ServerViewModel(app, DemoServer.profile("Demo"))
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { vm.runConnection() }
        val until = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < until && !(vm.connection == de.remotemc.app.ui.Connection.LIVE && vm.players.isNotEmpty() && vm.console.isNotEmpty())) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(100)
        }
        job.cancel()
        assertEquals(de.remotemc.app.ui.Connection.LIVE, vm.connection)
        assertTrue(vm.players.isNotEmpty())
        assertTrue(vm.console.isNotEmpty())
    }
}
