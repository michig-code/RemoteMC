package de.remotemc.app.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.remotemc.app.app
import de.remotemc.app.data.AppSettings
import de.remotemc.app.data.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps a live connection to every server with notifications on and turns its events into
 * notifications. Runs as a foreground service (with a silent permanent notification), because
 * the app is installed without Google services and so cannot receive push messages.
 */
class WatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val watchers = ConcurrentHashMap<String, Pair<ServerProfile, Job>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        ServiceCompat.startForeground(
            this, Notifier.WATCHER_ID, Notifier.watcher(this, 0),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        scope.launch {
            combine(app.servers.servers, app.settings.settings) { servers, settings -> watched(servers, settings) }
                .collect(::sync)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun sync(servers: List<ServerProfile>) {
        if (servers.isEmpty()) {
            stopSelf()
            return
        }
        val wanted = servers.associateBy { it.id }
        // Stop watchers for removed servers or changed connections.
        watchers.entries.removeAll { (id, entry) ->
            val now = wanted[id]
            val stale = now == null || now.baseUrl != entry.first.baseUrl || now.token != entry.first.token ||
                now.fingerprint != entry.first.fingerprint
            if (stale) entry.second.cancel()
            stale
        }
        servers.forEach { server ->
            val current = watchers[server.id]
            if (current == null) {
                watchers[server.id] = server to scope.launch { watch(server.id) }
            } else {
                watchers[server.id] = server to current.second // keep the name up to date
            }
        }
        NotificationManagerCompat.from(this).let {
            if (it.areNotificationsEnabled()) {
                runCatching { it.notify(Notifier.WATCHER_ID, Notifier.watcher(this, servers.size)) }
            }
        }
    }

    private suspend fun watch(id: String) {
        ServerWatcher(
            server = { watchers[id]?.first ?: app.servers.get(id) },
            rules = AlertRules { app.settings.settings.value.alerts },
            phoneOnline = ::phoneOnline,
            post = { server, alert -> Notifier.post(this, server, alert) },
            clock = SystemClock::elapsedRealtime, // keeps counting while the phone sleeps
            log = { Log.w(TAG, it) },
        ).run()
    }

    private fun phoneOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "WatchService"

        fun watched(servers: List<ServerProfile>, settings: AppSettings) =
            if (settings.alerts.enabled) servers.filter { it.notify } else emptyList()

        /** Starts the service if something is to be watched (it stops itself otherwise). */
        fun sync(context: Context) {
            val app = context.app
            if (watched(app.servers.servers.value, app.settings.settings.value).isEmpty()) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
            } catch (e: IllegalStateException) {
                // Not allowed from the background on Android 12+; it starts the next time the app opens.
                Log.w(TAG, "Could not start: $e")
            }
        }
    }
}
