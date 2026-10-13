package de.remotemc.app

import android.app.Application
import android.content.Context
import de.remotemc.app.data.CommandHistory
import de.remotemc.app.data.KeystoreBox
import de.remotemc.app.data.ServerRepository
import de.remotemc.app.data.SettingsStore
import de.remotemc.app.data.SnapshotStore
import java.io.File

class RemoteMCApp : Application() {
    lateinit var servers: ServerRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var history: CommandHistory
        private set
    /** Last shown data per server, for an instant start (see [de.remotemc.app.data.ServerSnapshot]). */
    lateinit var snapshots: SnapshotStore
        private set

    override fun onCreate() {
        super.onCreate()
        servers = ServerRepository(getSharedPreferences("servers", MODE_PRIVATE), KeystoreBox())
        settings = SettingsStore(getSharedPreferences("settings", MODE_PRIVATE))
        history = CommandHistory(getSharedPreferences("history", MODE_PRIVATE))
        snapshots = SnapshotStore(File(noBackupFilesDir, "snapshots"), KeystoreBox("remotemc.snapshots"))
    }
}

val Context.app: RemoteMCApp get() = applicationContext as RemoteMCApp
