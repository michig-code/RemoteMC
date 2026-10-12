package de.remotemc.app

import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.ApiError
import de.remotemc.app.net.EventStream
import de.remotemc.app.net.StreamEvent
import de.remotemc.app.net.Tls
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ApiClient and EventStream against a local HTTPS server with a self-signed certificate. */
@RunWith(RobolectricTestRunner::class)
class NetworkTest {

    private val cert = HeldCertificate.Builder().commonName("RemoteMC").ecdsa256().build()
    private val server = MockWebServer()
    private lateinit var fingerprint: String

    @Before
    fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(), false)
        server.start()
        fingerprint = Tls.fingerprint(cert.certificate)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun profile(fp: String? = fingerprint) = ServerProfile(
        name = "Test", host = server.hostName, port = server.port, token = "rmc_test", tls = true, fingerprint = fp,
    )

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun pinnedCertificateIsAccepted() = runBlocking {
        server.enqueue(json("""{"name":"Pixel","role":"moderator","commandsEnabled":true,"capturesCommandOutput":true}"""))
        val me = ApiClient(profile()).me()
        assertEquals(Role.MODERATOR, me.role)
        assertEquals("Bearer rmc_test", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun differentCertificateIsRejected() = runBlocking {
        server.enqueue(json("{}"))
        try {
            ApiClient(profile(fp = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).me()
            fail("expected CertificateChanged")
        } catch (e: ApiError.CertificateChanged) {
            assertEquals(fingerprint, e.fingerprint)
        }
    }

    @Test
    fun unpinnedSelfSignedIsRejected() = runBlocking {
        server.enqueue(json("{}"))
        try {
            ApiClient(profile(fp = null)).me()
            fail("expected TLS failure")
        } catch (e: ApiError) {
            assertTrue(e is ApiError.Tls || e is ApiError.Unreachable)
        }
    }

    @Test
    fun probeReportsFingerprint() = runBlocking {
        server.enqueue(json("""{"app":"RemoteMC","version":"2.0.0","apiVersion":1,"platform":"Paper"}"""))
        val probe = ApiClient.probe(server.hostName, server.port, tls = true)
        assertEquals(fingerprint, probe.fingerprint)
        assertEquals("Paper", probe.ping.platform)
        assertEquals(4 + 1 + 4, Tls.shortFingerprint(probe.fingerprint!!).length)
    }

    @Test
    fun oldPluginIsDetected() = runBlocking {
        server.enqueue(json("""{"error":"not_found"}""", 404))
        try {
            ApiClient.probe(server.hostName, server.port, tls = true)
            fail("expected Incompatible")
        } catch (e: ApiError.Incompatible) {
            assertTrue(e.pluginTooOld)
        }
    }

    @Test
    fun errorCodesAreMapped() = runBlocking {
        server.enqueue(json("""{"error":"invalid_token","message":"Invalid or revoked token"}""", 401))
        try {
            ApiClient(profile()).status()
            fail("expected Http error")
        } catch (e: ApiError.Http) {
            assertEquals(401, e.status)
            assertEquals("invalid_token", e.code)
        }
    }

    @Test
    fun pairingExchangesCode() = runBlocking {
        server.enqueue(json("""{"app":"RemoteMC","version":"2.0.0","apiVersion":1,"platform":"Paper"}"""))
        server.enqueue(json("""{"token":"rmc_new","name":"Pixel","role":"admin"}"""))
        val result = ApiClient.pair(server.hostName, server.port, true, fingerprint, "123456", "Pixel")
        assertEquals("rmc_new", result.token)
        assertEquals(Role.ADMIN, result.role)
        server.takeRequest()
        val pair = server.takeRequest()
        assertEquals("/api/v1/pair", pair.path)
        assertTrue(pair.body.readUtf8().contains("\"code\":\"123456\""))
    }

    @Test
    fun eventStreamParsesEvents() = runBlocking {
        val snapshot = """{"status":{"onlinePlayers":1,"maxPlayers":20,"tps":[20.0],"mspt":3.1,"worlds":[]},"players":[]}"""
        val body = "retry: 3000\n\n" +
            "event: hello\ndata: {\"name\":\"Pixel\",\"role\":\"admin\",\"snapshot\":$snapshot,\"consoleLast\":5,\"chatLast\":2}\n\n" +
            ": ping\n\n" +
            "event: console\ndata: {\"id\":6,\"time\":1,\"level\":\"INFO\",\"message\":\"Hello\"}\n\n" +
            "event: chat\ndata: {\"id\":3,\"time\":1,\"type\":\"chat\",\"player\":\"Steve\",\"message\":\"hi\"}\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
        val events = EventStream(profile()).events().take(3).toList()
        val hello = events[0] as StreamEvent.Hello
        assertEquals(Role.ADMIN, hello.session.role)
        assertEquals(1, hello.status.onlinePlayers)
        assertEquals("Hello", (events[1] as StreamEvent.Console).line.message)
        assertEquals("Steve", (events[2] as StreamEvent.Chat).entry.player)
    }
}
