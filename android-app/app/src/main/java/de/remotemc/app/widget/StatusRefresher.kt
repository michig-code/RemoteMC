package de.remotemc.app.widget

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.ServerProfile
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.remotemc.app.app
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/** Checks all servers and refreshes the widget and the tile. */
class StatusRefresher(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val servers = context.app.servers.servers.value
        val results = coroutineScope {
            servers.map { profile ->
                async { WidgetServer.of(profile, fetch(profile)) }
            }.awaitAll()
        }
        WidgetStore.save(context, WidgetData(results, System.currentTimeMillis(), actions(context)))
        RemoteMCWidget().updateAll(context)
        TileService.requestListeningState(context, ComponentName(context, AlertsTileService::class.java))
        return Result.success()
    }

    companion object {
        /**
         * One server's status for the widget. In the background the phone's network often needs a
         * moment (the radio wakes up, the first connection is slow): a quick check that fails is
         * tried again with the normal timeouts, so a running server is not shown offline. The
         * server list in the app retries every few seconds anyway; the widget only every 15 minutes.
         */
        internal suspend fun fetch(
            profile: ServerProfile,
            quick: OkHttpClient = Http.quick,
            patient: OkHttpClient = Http.base,
            retryDelay: Long = 3_000,
        ): ServerStatus? {
            runCatching { ApiClient(profile, quick).status() }.getOrNull()?.let { return it }
            repeat(2) { attempt ->
                if (attempt > 0) delay(retryDelay)
                runCatching { ApiClient(profile, patient).status() }.getOrNull()?.let { return it }
            }
            return null
        }

        /** Favorites marked for the widget, of all servers (admins only: they run console commands). */
        fun actions(context: Context): List<WidgetAction> =
            context.app.servers.servers.value
                .filter { it.role.atLeast(de.remotemc.app.data.Role.ADMIN) }
                .flatMap { server ->
                    context.app.shortcuts.load(server.id).favorites
                        .filter { it.widget && it.fitsWidget }
                        .map { WidgetAction(server.id, it.id, "/" + it.command) }
                }

        /** Redraws the widget after favorites changed, without checking the servers again. */
        suspend fun updateActions(context: Context) {
            val data = WidgetStore.load(context)
            WidgetStore.save(context, data.copy(actions = actions(context)))
            RemoteMCWidget().updateAll(context)
        }

        private const val PERIODIC = "widget-periodic"
        private const val NOW = "widget-now"

        /** Every 15 minutes (the shortest Android allows) while a widget is on the home screen. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StatusRefresher>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<StatusRefresher>().build())
        }
    }
}
