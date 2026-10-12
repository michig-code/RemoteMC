package de.remotemc.app.notify

import de.remotemc.app.data.AlertSettings
import de.remotemc.app.data.ServerProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ServerWatcherTest {

    private val settings = AlertSettings(enabled = true, joins = true, words = "Michi")
    private val alerts = Collections.synchronizedList(mutableListOf<Alert>())
    private val client = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS).build()

    private fun sse(vararg events: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.joinToString("") { "$it\n\n" })

    private val hello = "event: hello\ndata: {\"name\":\"Pixel\",\"role\":\"admin\",\"snapshot\":" +
        "{\"status\":{\"onlinePlayers\":1,\"maxPlayers\":20,\"tps\":[20.0],\"worlds\":[]},\"players\":[]}}"

    private fun chat(type: String, player: String, message: String = "") =
        "event: chat\ndata: {\"id\":1,\"time\":1,\"type\":\"$type\",\"player\":\"$player\",\"message\":\"$message\"}"

    private fun profile(server: MockWebServer) =
        ServerProfile(name = "Survival", host = server.hostName, port = server.port, token = "t", tls = false)

    private suspend fun waitFor(count: Int) = withTimeout(10_000) {
        while (alerts.size < count) kotlinx.coroutines.delay(20)
    }

    @Test
    fun streamEventsBecomeAlerts() = runBlocking {
        val server = MockWebServer()
        server.enqueue(sse(hello, chat("join", "flatsch"), chat("chat", "Steve", "where is michi?"), chat("chat", "Steve", "hi")))
        server.start()
        val profile = profile(server)
        val watcher = ServerWatcher({ profile }, AlertRules { settings }, { true }, { _, a -> alerts += a }, client, minBackoff = 50)
        val job = launch { watcher.run() }
        waitFor(2)
        job.cancel()
        server.shutdown()
        assertEquals(listOf(Alert.Joined("flatsch"), Alert.Mention("Steve", "where is michi?")), alerts.toList())
    }

    @Test
    fun outageAndRecovery() = runBlocking {
        // One server that answers, then drops every connection (down), then answers again.
        val down = java.util.concurrent.atomic.AtomicBoolean(false)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (down.get()) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) else sse(hello)
        }
        server.start()
        val profile = profile(server)
        var now = 0L
        val watcher = ServerWatcher(
            { profile }, AlertRules { settings }, { true }, { _, a -> alerts += a }, client,
            clock = { now.also { now += 10_000 } }, minBackoff = 20, maxBackoff = 20,
        )
        down.set(true)
        val job = launch(kotlinx.coroutines.Dispatchers.IO) { watcher.run() }
        waitFor(1) // offline after the (fake) 30 s
        assertEquals(Alert.Offline, alerts[0])
        down.set(false)
        waitFor(2)
        job.cancel()
        server.shutdown()
        assertEquals(Alert.BackOnline, alerts[1])
    }
}
