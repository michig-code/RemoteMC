package de.remotemc.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import de.remotemc.app.app
import de.remotemc.app.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "Reply" on a mention notification: sends the typed text to the server's chat. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim().orEmpty()
        val serverId = intent.getStringExtra(EXTRA_SERVER).orEmpty()
        val id = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val server = context.app.servers.get(serverId)
        if (text.isEmpty() || server == null) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val error = runCatching { ApiClient(server).broadcast(text.take(256)) }.exceptionOrNull()
                Notifier.replied(context.applicationContext, server, id, text, error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val KEY_TEXT = "reply"
        const val EXTRA_SERVER = "server"
        const val EXTRA_NOTIFICATION = "notification"
    }
}
