package de.remotemc.app.widget

import de.remotemc.app.data.ServerProfile
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/** The widget shows a slow server as online: a too quick first check is tried again with patience. */
@RunWith(RobolectricTestRunner::class)
class WidgetFetchTest {
    private val status = """{"motd":"Hi","version":"git","minecraftVersion":"1.21.4","platform":"Paper","pluginVersion":"1.0.0",""" +
        """"onlinePlayers":3,"maxPlayers":20,"tps":[19.9],"mspt":12.0,"memoryUsedMb":1000,"memoryMaxMb":4000,"uptimeSeconds":60,"worlds":[]}"""
    private val quick = OkHttpClient.Builder().callTimeout(300, TimeUnit.MILLISECONDS).build()
    private val patient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()

    private fun profile(server: MockWebServer) = ServerProfile(name = "Survival", host = server.hostName, port = server.port, token = "t", tls = false)

    @Test
    fun slowServerIsOnline() = runBlocking {
        val server = MockWebServer()
        // Both answers come late: too late for the quick check, in time for the patient one.
        repeat(2) { server.enqueue(MockResponse().setBody(status).setHeadersDelay(800, TimeUnit.MILLISECONDS)) }
        server.start()
        val result = StatusRefresher.fetch(profile(server), quick, patient, retryDelay = 10)
        server.shutdown()
        assertEquals(3, result?.onlinePlayers)
    }

    @Test
    fun downServerIsOffline() = runBlocking {
        val server = MockWebServer()
        server.start()
        val p = profile(server)
        server.shutdown()
        assertNull(StatusRefresher.fetch(p, quick, patient, retryDelay = 10))
    }
}
