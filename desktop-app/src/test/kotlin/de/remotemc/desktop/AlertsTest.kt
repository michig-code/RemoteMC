package de.remotemc.desktop

import de.remotemc.app.data.ServerProfile
import de.remotemc.app.notify.Alert
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AlertsTest {
    private val server = ServerProfile(name = "Survival", host = "mc.example.com", token = "x")

    /** Shows which text was picked and with which values (real texts need a screen). */
    private fun check(alert: Alert, expected: DesktopNotice, joined: List<String> = emptyList()) = runBlocking {
        assertEquals(expected, describe(server, alert, joined, text = { res, args -> (listOf(res.key) + args).joinToString("|") }, plural = { res, n, args -> (listOf(res.key, n) + args).joinToString("|") }))
    }

    @Test fun offline() = check(Alert.Offline, DesktopNotice("notif_offline_title|Survival", "notif_offline_text", true))
    @Test fun online() = check(Alert.BackOnline, DesktopNotice("notif_online_title|Survival", null, false))
    @Test fun tps() = check(Alert.LowTps(12.34), DesktopNotice("notif_tps_title|Survival", "notif_tps_text|" + "%.1f".format(12.34), true))
    @Test fun join() = check(Alert.Joined("Steve"), DesktopNotice("notif_join_title|Steve|Survival", null, false))
    @Test fun joinsBundled() = check(Alert.Joined("Sam"), DesktopNotice("notif_joins_title|3|3|Survival", "Alex, Steve, Sam", false), listOf("Alex", "Steve", "Sam"))
    @Test fun mention() = check(Alert.Mention("Alex", "hey @Michi"), DesktopNotice("notif_mention_title|Alex|Survival", "hey @Michi", false))
}
