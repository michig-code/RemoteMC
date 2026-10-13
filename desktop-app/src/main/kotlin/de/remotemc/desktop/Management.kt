package de.remotemc.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.remotemc.app.data.BanInfo
import de.remotemc.app.data.FileList
import de.remotemc.app.data.PluginInfo
import de.remotemc.app.data.ScheduleInfo
import de.remotemc.app.data.ScheduleList
import de.remotemc.app.data.TextFile
import de.remotemc.app.data.Whitelist
import de.remotemc.app.data.WorldDetails
import de.remotemc.app.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Server management calls; an interface so screenshot tests can use fixed data. */
interface Management {
    suspend fun whitelist(): Whitelist
    suspend fun setWhitelist(enabled: Boolean): Whitelist
    suspend fun whitelistAdd(name: String): Whitelist
    suspend fun whitelistRemove(name: String): Whitelist
    suspend fun bans(): List<BanInfo>
    suspend fun pardon(name: String): List<BanInfo>
    suspend fun world(name: String): WorldDetails
    suspend fun worldAction(name: String, action: String, body: JSONObject): WorldDetails
    suspend fun plugins(): List<PluginInfo>
    suspend fun schedules(): ScheduleList
    suspend fun saveSchedule(task: ScheduleInfo): ScheduleList
    suspend fun scheduleAction(id: String, action: String): ScheduleList
    suspend fun files(path: String): FileList
    suspend fun readFile(path: String): TextFile
    suspend fun writeFile(path: String, content: String)
    suspend fun deleteFile(path: String)
    suspend fun createFolder(path: String)
    suspend fun uploadFile(path: String, file: java.io.File)
}

class ApiManagement(private val api: () -> ApiClient) : Management {
    override suspend fun whitelist() = api().whitelist()
    override suspend fun setWhitelist(enabled: Boolean) = api().setWhitelist(enabled)
    override suspend fun whitelistAdd(name: String) = api().whitelistAdd(name)
    override suspend fun whitelistRemove(name: String) = api().whitelistRemove(name)
    override suspend fun bans() = api().bans()
    override suspend fun pardon(name: String) = api().pardon(name)
    override suspend fun world(name: String) = api().world(name)
    override suspend fun worldAction(name: String, action: String, body: JSONObject) = api().worldAction(name, action, body)
    override suspend fun plugins() = api().plugins()
    override suspend fun schedules() = api().schedules()
    override suspend fun saveSchedule(task: ScheduleInfo) = api().saveSchedule(task)
    override suspend fun scheduleAction(id: String, action: String) = api().scheduleAction(id, action)
    override suspend fun files(path: String) = api().files(path)
    override suspend fun readFile(path: String) = api().readFile(path)
    override suspend fun writeFile(path: String, content: String) {
        api().writeFile(path, content)
    }
    override suspend fun deleteFile(path: String) {
        api().deleteFile(path)
    }
    override suspend fun createFolder(path: String) {
        api().createFolder(path)
    }
    override suspend fun uploadFile(path: String, file: java.io.File) {
        api().uploadFile(path, file.length()) { file.inputStream() }
    }
}

/** Loads data and runs changes that return the new data; errors become readable text. */
class Loader<T>(private val scope: CoroutineScope) {
    var data by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    fun run(onDone: suspend (T) -> Unit = {}, call: suspend () -> T) {
        scope.launch {
            busy = true
            try {
                val result = call()
                data = result
                error = null
                onDone(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                busy = false
            }
        }
    }
}

/** A [Loader] that loads once per [key]; call `reload` to fetch again. */
@Composable
fun <T> rememberLoader(key: Any? = Unit, load: suspend () -> T): Pair<Loader<T>, () -> Unit> {
    val scope = rememberCoroutineScope()
    val loader = remember(key) { Loader<T>(scope) }
    var tick by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key, tick) { loader.run { load() } }
    return loader to { tick++ }
}
