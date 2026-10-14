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

    @Test
    fun ownJarIsLocked() = runBlocking {
        val jar = api.files("plugins").entries.single { it.name.startsWith("RemoteMC") && it.name.endsWith(".jar") }
        assertTrue(jar.locked)
        assertTrue(api.files("").entries.single { it.name == "plugins" }.locked)
        val error = runCatching { api.deleteFile(jar.path) }.exceptionOrNull()
        assertEquals(403, (error as de.remotemc.app.net.ApiError.Http).status)
        assertTrue(api.files("plugins").entries.any { it.name == jar.name })
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
