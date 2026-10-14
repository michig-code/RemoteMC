package de.remotemc.desktop

import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.DemoServer
import de.remotemc.app.net.Http
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The built-in demo server also answers on the PC, without network. */
class DemoServerTest {
    private val api = ApiClient(DemoServer.profile("Demo server"), Http.base)

    @Test
    fun answersStatusAndPlayers() = runBlocking {
        val status = api.status()
        assertEquals("Paper", status.platform)
        assertTrue(status.onlinePlayers > 0)
        assertEquals(status.onlinePlayers, api.players().size)
    }
}

class DemoStreamTest {
    @Test
    fun liveStreamSaysHello() = runBlocking {
        val first = kotlinx.coroutines.withTimeout(10_000) {
            de.remotemc.app.net.EventStream(DemoServer.profile("Demo server")).events().first()
        }
        assertTrue(first is de.remotemc.app.net.StreamEvent.Hello, "got $first")
    }
}
