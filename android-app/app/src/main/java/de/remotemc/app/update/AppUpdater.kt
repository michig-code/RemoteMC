package de.remotemc.app.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import de.remotemc.app.BuildConfig
import de.remotemc.app.data.Release
import de.remotemc.app.data.Releases
import de.remotemc.app.net.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Updates of the GitHub version of the app: looks for a newer release, downloads its APK, checks
 * size and SHA-256 and hands it to Android's installer (which also checks it is signed with the
 * same key). The Google Play version never does this ([BuildConfig.SELF_UPDATE] is false there).
 */
object AppUpdater {
    val enabled: Boolean get() = BuildConfig.SELF_UPDATE

    suspend fun check(
        current: String = BuildConfig.VERSION_NAME,
        client: OkHttpClient = Http.base,
        api: String = Releases.LATEST_API,
    ): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(api)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "RemoteMC-Android/$current")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null // no release yet
            if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
            Releases.parse(response.body?.string().orEmpty(), current, Releases::isPhoneApk)
        }
    }

    /** Downloads the APK into the app's cache and checks size and checksum. */
    suspend fun download(
        context: Context,
        release: Release,
        client: OkHttpClient = Http.base,
        /** Only GitHub over HTTPS; tests use a local server. */
        trusted: (String) -> Boolean = Releases::isTrusted,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val url = release.fileUrl ?: throw IOException("This release has no app file")
        if (!trusted(url)) throw IOException("Untrusted download address")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // older downloads
        val target = File(dir, "RemoteMC-${release.version.filter { it.isLetterOrDigit() || it == '.' }}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val slow = client.newBuilder().readTimeout(2, TimeUnit.MINUTES).build()
        slow.newCall(Request.Builder().url(url).header("User-Agent", "RemoteMC-Android").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
            // GitHub redirects to its file servers; the final address must still be GitHub's.
            if (!trusted(response.request.url.toString())) throw IOException("Untrusted download address")
            val body = response.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: release.fileSize
            var done = 0L
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (release.fileSize > 0 && target.length() != release.fileSize) {
            target.delete()
            throw IOException("The download is incomplete")
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && hash != release.sha256) {
            target.delete()
            throw IOException("The download does not match the checksum")
        }
        target
    }

    /** Android 8+ asks once whether RemoteMC may install apps. */
    fun mayInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** The system page where the user allows RemoteMC to install apps. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Opens Android's installer for the downloaded APK. */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** What the app shows about updates; looks at start and every 6 hours while turned on. */
class AppUpdates(private val context: Context, private val autoCheck: () -> Boolean) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        /** Downloaded; Android's installer is open (or needs the permission first). */
        data class Ready(val release: Release, val apk: File) : State
        data class Failed(val release: Release?, val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** "Later" hides the pop-up until the app starts again. */
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    private var started = false

    fun start() {
        if (!AppUpdater.enabled || started) return
        started = true
        scope.launch {
            delay(4_000) // not while the app is still opening
            while (isActive) {
                if (autoCheck()) check(manual = false)
                delay(6 * 60 * 60 * 1000L)
            }
        }
    }

    fun check(manual: Boolean = true) {
        if (!AppUpdater.enabled) return
        val now = _state.value
        if (now is State.Checking || now is State.Downloading) return
        if (manual) _dismissed.value = false
        scope.launch {
            _state.value = State.Checking
            _state.value = try {
                AppUpdater.check()?.let { State.Available(it) } ?: State.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed automatic check stays quiet; a manual one says why.
                if (manual) State.Failed(null, e.message ?: e.toString()) else State.Idle
            }
        }
    }

    fun later() {
        _dismissed.value = true
    }

    /** Downloads the release and opens the installer; [open] starts an activity (installer or permission page). */
    fun install(open: (Intent) -> Unit) {
        val release = when (val s = _state.value) {
            is State.Available -> s.release
            is State.Failed -> s.release ?: return
            is State.Ready -> {
                launchInstaller(s.apk, open)
                return
            }
            else -> return
        }
        scope.launch {
            _state.value = State.Downloading(release, 0f)
            try {
                val apk = AppUpdater.download(context, release) { p -> _state.value = State.Downloading(release, p) }
                _state.value = State.Ready(release, apk)
                launchInstaller(apk, open)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(release, e.message ?: e.toString())
            }
        }
    }

    private fun launchInstaller(apk: File, open: (Intent) -> Unit) {
        // Without the permission Android shows its settings page first; afterwards "Install" again.
        open(if (AppUpdater.mayInstall(context)) AppUpdater.installIntent(context, apk) else AppUpdater.permissionIntent(context))
    }
}
