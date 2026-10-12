package de.remotemc.app

import de.remotemc.app.data.ConnectLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectLinkTest {

    @Test
    fun parsesPairingLink() {
        val link = ConnectLink.parse("remotemc://play.example.com:25570?code=123456&fp=X8Z7vZ4QM1IFpAkiY4T3PF5DKiok9ifVqw6sAUAyJBM")!!
        assertEquals("play.example.com", link.host)
        assertEquals(25570, link.port)
        assertEquals("123456", link.code)
        assertEquals("X8Z7vZ4QM1IFpAkiY4T3PF5DKiok9ifVqw6sAUAyJBM", link.fingerprint)
        assertTrue(link.tls)
    }

    @Test
    fun plainHttpWhenTlsDisabled() {
        val link = ConnectLink.parse("remotemc://192.168.1.20:8765?code=654321&tls=0")!!
        assertFalse(link.tls)
        assertNull(link.fingerprint)
    }

    @Test
    fun ipv6AndDefaultPort() {
        val link = ConnectLink.parse("remotemc://[fd00::1]?code=111222&fp=abc")!!
        assertEquals("fd00::1", link.host)
        assertEquals(8765, link.port)
    }

    @Test
    fun legacyMcRemoteTokenLink() {
        val link = ConnectLink.parse("mcremote://host:8765?token=abc")!!
        assertEquals("abc", link.token)
        assertNull(link.code)
    }

    @Test
    fun rejectsOtherCodes() {
        assertNull(ConnectLink.parse("https://example.com"))
        assertNull(ConnectLink.parse("remotemc://host:8765"))          // neither code nor token
        assertNull(ConnectLink.parse("remotemc://host:8765?code=12"))  // incomplete code
        assertNull(ConnectLink.parse("hello"))
    }
}
