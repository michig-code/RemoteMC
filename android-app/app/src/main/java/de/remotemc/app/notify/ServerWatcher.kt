package de.remotemc.app.notify

import de.remotemc.app.data.ServerProfile
import de.remotemc.app.net.ApiError
import de.remotemc.app.net.EventStream
import de.remotemc.app.net.Http
import de.remotemc.app.net.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient

/** Live connection to one server that reconnects on its own and reports [Alert]s. */
class ServerWatcher(
    private val server: () -> ServerProfile?,
    private val rules: AlertRules,
    private val phoneOnline: () -> Boolean,
    private val post: (ServerProfile, Alert) -> Unit,
    private val base: OkHttpClient = Http.base,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val minBackoff: Long = 2_000,
    private val maxBackoff: Long = 30_000,
    private val errorBackoff: Long = 5 * 60_000,
    private val log: (String) -> Unit = {},
) {
    suspend fun run() {
        var backoff = minBackoff
        while (currentCoroutineContext().isActive) {
            val profile = server() ?: return
            var failure: Throwable? = null
            try {
                EventStream(profile, base).events().collect { event ->
                    val alert = when (event) {
                        is StreamEvent.Hello -> {
                            backoff = minBackoff
                            rules.onConnected() ?: rules.onStatus(event.status, clock())
                        }
                        is StreamEvent.Status -> rules.onStatus(event.status, clock())
                        is StreamEvent.Chat -> rules.onChat(event.entry)
                        is StreamEvent.Console -> null
                    }
                    alert?.let { post(server() ?: profile, it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            // Only "cannot reach it" means offline; a revoked token or a changed certificate does not.
            val unreachable = failure == null || failure is ApiError.Unreachable || failure is ApiError.Timeout
            if (unreachable) {
                rules.onDisconnected(clock(), phoneOnline())?.let { post(server() ?: profile, it) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
            } else {
                log("Not watching ${profile.name} for now: $failure")
                delay(errorBackoff)
            }
        }
    }
}
