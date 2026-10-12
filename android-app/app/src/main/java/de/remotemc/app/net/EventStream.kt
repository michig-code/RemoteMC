package de.remotemc.app.net

import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.ConsoleLine
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.Session
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface StreamEvent {
    data class Hello(
        val session: Session,
        val status: ServerStatus,
        val players: List<PlayerInfo>,
        val consoleLast: Long,
        val chatLast: Long,
    ) : StreamEvent

    data class Status(val status: ServerStatus, val players: List<PlayerInfo>) : StreamEvent
    data class Console(val line: ConsoleLine) : StreamEvent
    data class Chat(val entry: ChatEntry) : StreamEvent
}

/** Live events from GET /api/v1/events (Server-Sent Events). Completes with an [ApiError] on failure. */
class EventStream(private val profile: ServerProfile, base: OkHttpClient = Http.base) {

    // Heartbeats arrive every 15 s; a silent connection after 40 s is dead.
    private val client = Http.forProfile(base, profile.tls, profile.fingerprint).newBuilder()
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    fun events(): Flow<StreamEvent> = callbackFlow {
        val request = Request.Builder()
            .url(profile.baseUrl + "/api/v1/events")
            .header("Authorization", "Bearer ${profile.token}")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val event = runCatching { parse(type, JSONObject(data)) }.getOrNull() ?: return
                trySend(event)
            }

            override fun onClosed(eventSource: EventSource) {
                close(ApiError.Unreachable("stream closed"))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val error = when {
                    response != null && !response.isSuccessful -> {
                        val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                        ApiError.Http(response.code, json?.optString("error") ?: "http_${response.code}", json?.optString("message").orEmpty())
                    }
                    t != null -> ApiClient.mapException(t)
                    else -> ApiError.Unreachable("stream failed")
                }
                close(error)
            }
        }
        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private fun parse(type: String?, o: JSONObject): StreamEvent? = when (type) {
        "hello" -> {
            val snapshot = o.getJSONObject("snapshot")
            StreamEvent.Hello(
                session = Session.fromJson(o),
                status = ServerStatus.fromJson(snapshot.getJSONObject("status")),
                players = PlayerInfo.listFromJson(snapshot.optJSONArray("players")),
                consoleLast = o.optLong("consoleLast"),
                chatLast = o.optLong("chatLast"),
            )
        }
        "status" -> StreamEvent.Status(ServerStatus.fromJson(o.getJSONObject("status")), PlayerInfo.listFromJson(o.optJSONArray("players")))
        "console" -> StreamEvent.Console(ConsoleLine.fromJson(o))
        "chat" -> StreamEvent.Chat(ChatEntry.fromJson(o))
        else -> null
    }
}
