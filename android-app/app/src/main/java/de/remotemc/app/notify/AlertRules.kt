package de.remotemc.app.notify

import de.remotemc.app.data.AlertSettings
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.ServerStatus

/** Something worth a notification. */
sealed interface Alert {
    data object Offline : Alert
    data object BackOnline : Alert
    data class LowTps(val tps: Double) : Alert
    data class Joined(val player: String) : Alert
    data class Mention(val player: String, val message: String) : Alert
}

/**
 * Decides which events of one server become notifications. Pure logic (time is passed in), so it
 * can be tested without Android. Avoids noise: offline only after a grace period, low TPS once
 * until it recovers and at most every [TPS_COOLDOWN_MS].
 */
class AlertRules(private val settings: () -> AlertSettings) {

    private var lostSince: Long? = null
    private var offlineAlerted = false
    private var tpsAlertedAt: Long? = null
    private var tpsLow = false

    /** Connected (the stream said hello). */
    fun onConnected(): Alert? {
        lostSince = null
        val wasOffline = offlineAlerted
        offlineAlerted = false
        return if (wasOffline && settings().offline) Alert.BackOnline else null
    }

    /** Connection lost or failed; [phoneOnline] = false when the phone itself has no internet. */
    fun onDisconnected(now: Long, phoneOnline: Boolean): Alert? {
        if (!phoneOnline) {
            lostSince = null // not the server's fault; start counting again when the phone is back
            return null
        }
        val since = lostSince ?: now.also { lostSince = it }
        if (!offlineAlerted && now - since >= OFFLINE_GRACE_MS) {
            offlineAlerted = true
            if (settings().offline) return Alert.Offline
        }
        return null
    }

    fun onStatus(status: ServerStatus, now: Long): Alert? {
        val s = settings()
        val tps = status.tps.firstOrNull() ?: return null // Folia / Spigot report no TPS
        if (tps < s.tpsThreshold) {
            if (tpsLow) return null
            tpsLow = true
            val last = tpsAlertedAt
            if (!s.lowTps || (last != null && now - last < TPS_COOLDOWN_MS)) return null
            tpsAlertedAt = now
            return Alert.LowTps(tps)
        }
        if (tps >= s.tpsThreshold + 1) tpsLow = false // a little hysteresis against flapping
        return null
    }

    fun onChat(entry: ChatEntry): Alert? {
        val s = settings()
        return when (entry.type) {
            ChatEntry.Type.JOIN -> {
                val wanted = s.playerList
                if (s.joins && (wanted.isEmpty() || wanted.any { it.equals(entry.player, ignoreCase = true) })) {
                    Alert.Joined(entry.player)
                } else null
            }
            ChatEntry.Type.CHAT -> {
                if (s.mentions && s.wordList.any { mentions(entry.message, it) }) Alert.Mention(entry.player, entry.message) else null
            }
            else -> null
        }
    }

    companion object {
        const val OFFLINE_GRACE_MS = 30_000L
        const val TPS_COOLDOWN_MS = 10 * 60_000L

        /** Whole word, ignoring case and a leading @ ("Michi", "@michi," but not "Michigan"). */
        fun mentions(message: String, word: String): Boolean =
            Regex("(?<![\\p{L}\\p{N}_])@?" + Regex.escape(word) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
                .containsMatchIn(message)
    }
}
