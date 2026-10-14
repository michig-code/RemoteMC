package de.remotemc.desktop

import de.remotemc.app.data.ServerProfile
import de.remotemc.app.notify.Alert
import de.remotemc.app.notify.AlertRules
import de.remotemc.app.notify.JoinBundle
import de.remotemc.app.notify.ServerWatcher
import de.remotemc.desktop.res.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.net.NetworkInterface

/** One Windows notification. */
data class DesktopNotice(
    val title: String,
    val text: String?,
    val warning: Boolean,
    /** Clicking the notification opens this server (and its chat for a mention). */
    val serverId: String? = null,
    val chat: Boolean = false,
)

/** Same texts as the phone's notifications. */
suspend fun describe(
    server: ServerProfile,
    alert: Alert,
    /** Everyone who joined recently, for one notification instead of one each. */
    joined: List<String> = emptyList(),
    text: suspend (StringResource, Array<out Any>) -> String = { res, args -> getString(res, *args) },
    plural: suspend (PluralStringResource, Int, Array<out Any>) -> String = { res, n, args -> getPluralString(res, n, *args) },
): DesktopNotice {
    suspend fun t(res: StringResource, vararg args: Any) = text(res, args)
    val name = server.name
    return when (alert) {
        Alert.Offline -> DesktopNotice(t(Res.string.notif_offline_title, name), t(Res.string.notif_offline_text), true)
        Alert.BackOnline -> DesktopNotice(t(Res.string.notif_online_title, name), null, false)
        is Alert.LowTps -> DesktopNotice(t(Res.string.notif_tps_title, name), t(Res.string.notif_tps_text, "%.1f".format(alert.tps)), true)
        is Alert.Joined ->
            if (joined.size < 2) DesktopNotice(t(Res.string.notif_join_title, alert.player, name), null, false)
            else DesktopNotice(plural(Res.plurals.notif_joins_title, joined.size, arrayOf(joined.size, name)), joined.joinToString(", "), false)
        is Alert.Mention -> DesktopNotice(t(Res.string.notif_mention_title, alert.player, name), alert.message, false)
    }
}

/**
 * While notifications are on, keeps a connection to every server that has them enabled (the same
 * rules as on the phone: offline after 30 s, low TPS, joins, mentions) and shows what they report.
 */
class AlertCenter(
    private val model: AppModel,
    private val scope: CoroutineScope,
    private val show: (DesktopNotice) -> Unit,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val joins = JoinBundle()

    fun start(): Job = scope.launch {
        combine(model.servers.servers, model.settings.settings) { servers, settings ->
            if (settings.alerts.enabled) servers.filter { it.notify }.map { it.id }.toSet() else emptySet()
        }.distinctUntilChanged().collect(::sync)
    }

    private fun sync(wanted: Set<String>) {
        (jobs.keys - wanted).forEach { jobs.remove(it)?.cancel() }
        (wanted - jobs.keys).forEach { id ->
            jobs[id] = scope.launch(Dispatchers.IO) {
                ServerWatcher(
                    server = { model.servers.get(id) },
                    rules = AlertRules { model.settings.settings.value.alerts },
                    phoneOnline = ::networkUp,
                    post = { server, alert -> post(server, alert) },
                ).run()
            }
        }
    }

    private fun post(server: ServerProfile, alert: Alert) {
        val joined = (alert as? Alert.Joined)?.let { joins.add(server.id, it.player, System.currentTimeMillis()) }.orEmpty()
        // Quiet hours: Windows has no silent notifications, so only offline/online still pop up.
        if (alert !is Alert.Offline && alert !is Alert.BackOnline && model.settings.settings.value.alerts.isQuiet(minuteOfDay())) return
        scope.launch { show(describe(server, alert, joined).copy(serverId = server.id, chat = alert is Alert.Mention)) }
    }

    private fun minuteOfDay() = java.time.LocalTime.now().let { it.hour * 60 + it.minute }

    /** Without any network the PC is offline, not the servers. */
    private fun networkUp(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence().any { it.isUp && !it.isLoopback }
    }.getOrDefault(true)
}
