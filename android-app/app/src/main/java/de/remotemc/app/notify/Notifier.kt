package de.remotemc.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.remotemc.app.MainActivity
import de.remotemc.app.R
import de.remotemc.app.data.ServerProfile
import java.util.concurrent.atomic.AtomicInteger

/** Notification channels and the notifications for [Alert]s. */
object Notifier {
    const val CHANNEL_WATCHER = "watcher"
    private const val CHANNEL_STATUS = "status"
    private const val CHANNEL_PERFORMANCE = "performance"
    private const val CHANNEL_PLAYERS = "players"
    private const val CHANNEL_CHAT = "chat"
    const val WATCHER_ID = 1

    private val nextId = AtomicInteger(1000)

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_PERFORMANCE, context.getString(R.string.channel_performance), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_PLAYERS, context.getString(R.string.channel_players), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_CHAT, context.getString(R.string.channel_chat), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_WATCHER, context.getString(R.string.channel_watcher), NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                },
            ),
        )
    }

    /** The permanent notification of the background connection. */
    fun watcher(context: Context, count: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCHER)
            .setSmallIcon(R.drawable.ic_stat_remotemc)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notif_watching, count, count))
            .setContentIntent(open(context, null))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    fun post(context: Context, server: ServerProfile, alert: Alert) {
        val name = server.name
        val (channel, title, text) = when (alert) {
            Alert.Offline -> Triple(CHANNEL_STATUS, context.getString(R.string.notif_offline_title, name), context.getString(R.string.notif_offline_text))
            Alert.BackOnline -> Triple(CHANNEL_STATUS, context.getString(R.string.notif_online_title, name), null)
            is Alert.LowTps -> Triple(CHANNEL_PERFORMANCE, context.getString(R.string.notif_tps_title, name), context.getString(R.string.notif_tps_text, alert.tps))
            is Alert.Joined -> Triple(CHANNEL_PLAYERS, context.getString(R.string.notif_join_title, alert.player, name), null)
            is Alert.Mention -> Triple(CHANNEL_CHAT, context.getString(R.string.notif_mention_title, alert.player, name), alert.message)
        }
        // Offline and back online replace each other; low TPS replaces the previous one of that server.
        val id = when (alert) {
            Alert.Offline, Alert.BackOnline -> server.id.hashCode() * 31 + 1
            is Alert.LowTps -> server.id.hashCode() * 31 + 2
            else -> nextId.incrementAndGet()
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_remotemc)
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)) }
            .setContentIntent(open(context, server.id))
            .setAutoCancel(true)
            .setCategory(if (alert is Alert.Mention) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun open(context: Context, serverId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (serverId != null) putExtra(MainActivity.EXTRA_SERVER_ID, serverId) }
        return PendingIntent.getActivity(
            context, serverId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
