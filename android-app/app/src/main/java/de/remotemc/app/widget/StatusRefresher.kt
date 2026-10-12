package de.remotemc.app.widget

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
                async { WidgetServer.of(profile, runCatching { ApiClient(profile, Http.quick).status() }.getOrNull()) }
            }.awaitAll()
        }
        WidgetStore.save(context, WidgetData(results, System.currentTimeMillis()))
        RemoteMCWidget().updateAll(context)
        TileService.requestListeningState(context, ComponentName(context, AlertsTileService::class.java))
        return Result.success()
    }

    companion object {
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
