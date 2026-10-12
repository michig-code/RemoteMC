package de.remotemc.app.net

import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.CommandResult
import de.remotemc.app.data.ConsoleLine
import de.remotemc.app.data.PairResult
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

data class PingInfo(val version: String, val apiVersion: Int, val platform: String)

/** Probe result before pairing: what the server is and which certificate it presents. */
data class Probe(val ping: PingInfo, val fingerprint: String?)

/** REST client for one server, with certificate pinning. */
class ApiClient(private val profile: ServerProfile, base: OkHttpClient = Http.base) {

    private val client: OkHttpClient = Http.forProfile(base, profile.tls, profile.fingerprint)

    suspend fun ping(): PingInfo = ping(client, profile.baseUrl)
    suspend fun me(): Session = Session.fromJson(call("GET", "/api/v1/me"))
    suspend fun status(): ServerStatus = ServerStatus.fromJson(call("GET", "/api/v1/status"))
    suspend fun players(): List<PlayerInfo> = PlayerInfo.listFromJson(call("GET", "/api/v1/players").optJSONArray("players"))

    suspend fun console(since: Long): List<ConsoleLine> {
        val arr = call("GET", "/api/v1/console?since=$since").getJSONArray("lines")
        return List(arr.length()) { ConsoleLine.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun chat(since: Long): List<ChatEntry> {
        val arr = call("GET", "/api/v1/chat?since=$since").getJSONArray("messages")
        return List(arr.length()) { ChatEntry.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun command(command: String): CommandResult {
        val o = call("POST", "/api/v1/command", JSONObject().put("command", command))
        val out = o.optJSONArray("output")
        return CommandResult(
            success = o.optBoolean("success"),
            captured = o.optBoolean("captured", true),
            output = if (out == null) emptyList() else List(out.length()) { out.getString(it) },
        )
    }

    suspend fun broadcast(message: String) {
        call("POST", "/api/v1/chat", JSONObject().put("message", message))
    }

    suspend fun playerAction(player: String, action: String, body: JSONObject = JSONObject()) {
        call("POST", "/api/v1/players/${android.net.Uri.encode(player)}/$action", body)
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        execute(client, profile.baseUrl, method, path, profile.token, body)

    companion object {
        const val SUPPORTED_API = 1

        suspend fun ping(client: OkHttpClient, baseUrl: String): PingInfo {
            val o = try {
                execute(client, baseUrl, "GET", "/api/v1/ping", null, null)
            } catch (e: ApiError.Http) {
                if (e.status == 404) throw ApiError.Incompatible(pluginTooOld = true) else throw e
            }
            val info = PingInfo(o.optString("version"), o.optInt("apiVersion", 0), o.optString("platform"))
            if (info.apiVersion < SUPPORTED_API) throw ApiError.Incompatible(pluginTooOld = true)
            if (info.apiVersion > SUPPORTED_API) throw ApiError.Incompatible(pluginTooOld = false)
            return info
        }

        /**
         * Connects without trusting anything yet and reports the certificate fingerprint,
         * so the user can compare it with the console before pairing.
         */
        suspend fun probe(host: String, port: Int, tls: Boolean): Probe {
            val profile = ServerProfile(name = "", host = host, port = port, token = "", tls = tls)
            if (!tls) return Probe(ping(Http.base, profile.baseUrl), null)
            val capture = Tls.CapturingTrustManager()
            val client = Http.base.newBuilder()
                .sslSocketFactory(Tls.sslContext(capture).socketFactory, capture)
                .hostnameVerifier { _, _ -> true }
                .build()
            val ping = ping(client, profile.baseUrl)
            return Probe(ping, capture.captured?.let(Tls::fingerprint))
        }

        /** Exchanges a pairing code for this device's own token. */
        suspend fun pair(host: String, port: Int, tls: Boolean, fingerprint: String?, code: String, device: String): PairResult {
            val profile = ServerProfile(name = "", host = host, port = port, token = "", tls = tls, fingerprint = fingerprint)
            val client = Http.forProfile(Http.base, tls, fingerprint)
            ping(client, profile.baseUrl)
            val o = execute(client, profile.baseUrl, "POST", "/api/v1/pair", null,
                JSONObject().put("code", code).put("device", device))
            return PairResult(o.getString("token"), o.optString("name"), Role.parse(o.optString("role")))
        }

        internal suspend fun execute(
            client: OkHttpClient,
            baseUrl: String,
            method: String,
            path: String,
            token: String?,
            body: JSONObject?,
        ): JSONObject = withContext(Dispatchers.IO) {
            val request = try {
                Request.Builder()
                    .url(baseUrl + path)
                    .method(method, body?.toString()?.toRequestBody(JSON))
                    .header("Accept", "application/json")
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .build()
            } catch (e: IllegalArgumentException) {
                throw ApiError.Unreachable("Invalid address")
            }
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val json = try {
                        JSONObject(text)
                    } catch (e: JSONException) {
                        null
                    }
                    if (!response.isSuccessful) {
                        throw ApiError.Http(
                            response.code,
                            json?.optString("error")?.takeIf { it.isNotBlank() } ?: "http_${response.code}",
                            json?.optString("message").orEmpty(),
                        )
                    }
                    json ?: throw ApiError.InvalidResponse()
                }
            } catch (e: ApiError) {
                throw e
            } catch (e: IOException) {
                throw mapException(e)
            }
        }

        fun mapException(e: Throwable): ApiError {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is CertificateChangedException) return ApiError.CertificateChanged(cause.actualFingerprint)
                cause = cause.cause
            }
            return when (e) {
                is ApiError -> e
                is SocketTimeoutException, is InterruptedIOException -> ApiError.Timeout()
                is UnknownHostException -> ApiError.Unreachable("Unknown host")
                is ConnectException, is NoRouteToHostException -> ApiError.Unreachable(e.message ?: "")
                is SSLHandshakeException, is SSLException -> ApiError.Tls(e.message ?: "")
                else -> ApiError.Unreachable(e.message ?: e.javaClass.simpleName)
            }
        }

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

object Http {
    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun forProfile(base: OkHttpClient, tls: Boolean, fingerprint: String?): OkHttpClient {
        if (!tls) return base
        val tm = Tls.PinningTrustManager(fingerprint)
        return base.newBuilder()
            .sslSocketFactory(Tls.sslContext(tm).socketFactory, tm)
            .hostnameVerifier(Tls.hostnameVerifier(fingerprint != null))
            .build()
    }
}
