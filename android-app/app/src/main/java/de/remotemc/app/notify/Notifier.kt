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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import de.remotemc.app.MainActivity
import de.remotemc.app.R
import de.remotemc.app.data.Role
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
    private val joins = JoinBundle()

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

    fun post(context: Context, server: ServerProfile, alert: Alert, quiet: Boolean = false) {
        val name = server.name
        // Joins of the last minutes become one notification per server.
        val joined = (alert as? Alert.Joined)?.let { joins.add(server.id, it.player, System.currentTimeMillis()) }
        val (channel, title, text) = when (alert) {
            Alert.Offline -> Triple(CHANNEL_STATUS, context.getString(R.string.notif_offline_title, name), context.getString(R.string.notif_offline_text))
            Alert.BackOnline -> Triple(CHANNEL_STATUS, context.getString(R.string.notif_online_title, name), null)
            is Alert.LowTps -> Triple(CHANNEL_PERFORMANCE, context.getString(R.string.notif_tps_title, name), context.getString(R.string.notif_tps_text, alert.tps))
            is Alert.Joined ->
                if (joined == null || joined.size < 2) Triple(CHANNEL_PLAYERS, context.getString(R.string.notif_join_title, alert.player, name), null)
                else Triple(CHANNEL_PLAYERS, context.resources.getQuantityString(R.plurals.notif_joins_title, joined.size, joined.size, name), joined.joinToString(", "))
            is Alert.Mention -> Triple(CHANNEL_CHAT, context.getString(R.string.notif_mention_title, alert.player, name), alert.message)
        }
        // Offline and back online replace each other; low TPS replaces the previous one of that server.
        val id = when (alert) {
            Alert.Offline, Alert.BackOnline -> server.id.hashCode() * 31 + 1
            is Alert.LowTps -> server.id.hashCode() * 31 + 2
            is Alert.Joined -> server.id.hashCode() * 31 + 3
            else -> nextId.incrementAndGet()
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_remotemc)
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)) }
            .setContentIntent(open(context, server.id))
            .setAutoCancel(true)
            .setCategory(if (alert is Alert.Mention) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            // Quiet hours: shown, but without sound or vibration.
            .setSilent(quiet)
            .apply { if (alert is Alert.Mention && server.role.atLeast(Role.MODERATOR)) addAction(replyAction(context, server, id)) }
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    /** "Reply" with a text field right in the notification (moderators can write in the chat). */
    private fun replyAction(context: Context, server: ServerProfile, notificationId: Int): NotificationCompat.Action {
        val intent = Intent(context, ReplyReceiver::class.java)
            .putExtra(ReplyReceiver.EXTRA_SERVER, server.id)
            .putExtra(ReplyReceiver.EXTRA_NOTIFICATION, notificationId)
        // Mutable so Android can add the typed text.
        val pending = PendingIntent.getBroadcast(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
        val input = RemoteInput.Builder(ReplyReceiver.KEY_TEXT).setLabel(context.getString(R.string.notif_reply)).build()
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_remotemc, context.getString(R.string.notif_reply), pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    /** Replaces the mention notification after a reply: shows what was sent or why it failed. */
    fun replied(context: Context, server: ServerProfile, id: Int, text: String, error: Throwable?) {
        val title = if (error == null) context.getString(R.string.notif_reply_sent, text)
        else context.getString(R.string.notif_reply_failed, de.remotemc.app.ui.util.UiText.Error(error).resolve(context))
        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_stat_remotemc)
            .setContentTitle(server.name)
            .setContentText(title)
            .setContentIntent(open(context, server.id))
            .setAutoCancel(true)
            .setSilent(true)
            .setTimeoutAfter(if (error == null) 5_000 else 0)
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
