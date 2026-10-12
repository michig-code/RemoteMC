package de.remotemc.app

import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.EventStream
import de.remotemc.app.net.StreamEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Optional end-to-end test against a real server running the plugin. Skipped unless a pairing
 * link is given, e.g. ./gradlew testDebugUnitTest -Prmc.it.link="remotemc://127.0.0.1:8765?code=...&fp=..."
 */
@RunWith(RobolectricTestRunner::class)
class LiveServerTest {

    @Test
    fun pairAndStream() = runBlocking {
        val raw = System.getProperty("rmc.it.link")
        assumeTrue("set -Prmc.it.link to run", !raw.isNullOrBlank())
        val link = de.remotemc.app.data.ConnectLink.parse(raw!!)!!

        val probe = ApiClient.probe(link.host, link.port, link.tls)
        assertEquals(link.fingerprint, probe.fingerprint)

        val paired = ApiClient.pair(link.host, link.port, link.tls, probe.fingerprint, link.code!!, "Robolectric")
        val profile = ServerProfile(name = "IT", host = link.host, port = link.port, token = paired.token,
            tls = link.tls, fingerprint = probe.fingerprint, role = paired.role)
        val api = ApiClient(profile)
        assertEquals(Role.ADMIN, api.me().role)
        assertNotNull(api.status().minecraftVersion)
        val result = api.command("difficulty")
        assertEquals(true, result.success)

        val hello = withTimeout(10_000) { EventStream(profile).events().first() }
        assert(hello is StreamEvent.Hello)
    }
}
