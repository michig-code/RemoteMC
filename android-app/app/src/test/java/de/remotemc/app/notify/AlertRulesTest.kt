package de.remotemc.app.notify

import de.remotemc.app.data.AlertSettings
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {

    private var settings = AlertSettings(enabled = true, joins = true, words = "Michi, admin")
    private val rules = AlertRules { settings }

    private fun status(tps: Double?) = ServerStatus(
        "", "", "", "Paper", "1.0.0", 0, 20, listOfNotNull(tps), null, 0, 0, 0, emptyList(),
    )
    private fun chat(type: ChatEntry.Type, player: String, message: String = "") = ChatEntry(1, 0, type, player, message)

    @Test
    fun offlineOnlyAfterGracePeriodAndOnce() {
        assertNull(rules.onDisconnected(0, phoneOnline = true))
        assertNull(rules.onDisconnected(29_000, phoneOnline = true))
        assertEquals(Alert.Offline, rules.onDisconnected(30_000, phoneOnline = true))
        assertNull(rules.onDisconnected(60_000, phoneOnline = true))
        assertEquals(Alert.BackOnline, rules.onConnected())
        assertNull(rules.onConnected())
    }

    @Test
    fun shortRestartIsNoOutage() {
        rules.onDisconnected(0, phoneOnline = true)
        assertNull(rules.onConnected())
        assertNull(rules.onDisconnected(40_000, phoneOnline = true)) // the clock starts again
    }

    @Test
    fun phoneWithoutInternetDoesNotCount() {
        rules.onDisconnected(0, phoneOnline = false)
        assertNull(rules.onDisconnected(60_000, phoneOnline = false))
        assertNull(rules.onDisconnected(70_000, phoneOnline = true)) // counting starts now
        assertEquals(Alert.Offline, rules.onDisconnected(100_000, phoneOnline = true))
    }

    @Test
    fun lowTpsOnceUntilRecoveredWithCooldown() {
        assertEquals(Alert.LowTps(12.0), rules.onStatus(status(12.0), 0))
        assertNull(rules.onStatus(status(11.0), 2_000))
        assertNull(rules.onStatus(status(15.5), 4_000)) // within hysteresis: still "low"
        assertNull(rules.onStatus(status(14.0), 6_000))
        rules.onStatus(status(19.9), 8_000) // recovered
        assertNull(rules.onStatus(status(10.0), 60_000)) // cooldown
        rules.onStatus(status(20.0), 62_000)
        assertEquals(Alert.LowTps(9.0), rules.onStatus(status(9.0), AlertRules.TPS_COOLDOWN_MS + 1))
        assertNull(rules.onStatus(status(null), 0)) // no TPS reported (Folia)
    }

    @Test
    fun joinsFilteredByPlayerList() {
        assertEquals(Alert.Joined("Steve"), rules.onChat(chat(ChatEntry.Type.JOIN, "Steve")))
        settings = settings.copy(players = "flatsch, Micheeee14")
        assertNull(rules.onChat(chat(ChatEntry.Type.JOIN, "Steve")))
        assertEquals(Alert.Joined("micheeee14"), rules.onChat(chat(ChatEntry.Type.JOIN, "micheeee14")))
        settings = settings.copy(joins = false)
        assertNull(rules.onChat(chat(ChatEntry.Type.JOIN, "flatsch")))
    }

    @Test
    fun mentionsAreWholeWords() {
        assertTrue(rules.onChat(chat(ChatEntry.Type.CHAT, "Steve", "hey @michi, come here")) is Alert.Mention)
        assertTrue(rules.onChat(chat(ChatEntry.Type.CHAT, "Steve", "ADMIN help")) is Alert.Mention)
        assertNull(rules.onChat(chat(ChatEntry.Type.CHAT, "Steve", "michigan is big")))
        assertNull(rules.onChat(chat(ChatEntry.Type.BROADCAST, "Server", "Michi")))
        assertFalse(AlertRules.mentions("administrator", "admin"))
    }

    @Test
    fun switchedOffTypesStayQuiet() {
        settings = settings.copy(offline = false, lowTps = false, mentions = false)
        rules.onDisconnected(0, true)
        assertNull(rules.onDisconnected(31_000, true))
        assertNull(rules.onConnected())
        assertNull(rules.onStatus(status(5.0), 0))
        assertNull(rules.onChat(chat(ChatEntry.Type.CHAT, "Steve", "Michi")))
    }
}
