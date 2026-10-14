package de.remotemc.app

import android.app.Application
import android.content.Context
import de.remotemc.app.data.CommandHistory
import de.remotemc.app.data.KeystoreBox
import de.remotemc.app.data.PlayerNotes
import de.remotemc.app.data.ServerRepository
import de.remotemc.app.data.SettingsStore
import de.remotemc.app.data.ShortcutStore
import de.remotemc.app.data.SnapshotStore
import de.remotemc.app.update.AppUpdates
import java.io.File

class RemoteMCApp : Application() {
    lateinit var servers: ServerRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var history: CommandHistory
        private set
    /** Private notes about players. */
    lateinit var notes: PlayerNotes
        private set
    /** Favorites and macros per server. */
    lateinit var shortcuts: ShortcutStore
        private set
    /** Last shown data per server, for an instant start (see [de.remotemc.app.data.ServerSnapshot]). */
    lateinit var snapshots: SnapshotStore
        private set
    /** New versions of the GitHub version of the app (does nothing in the Google Play version). */
    lateinit var updates: AppUpdates
        private set

    override fun onCreate() {
        super.onCreate()
        servers = ServerRepository(getSharedPreferences("servers", MODE_PRIVATE), KeystoreBox())
        settings = SettingsStore(getSharedPreferences("settings", MODE_PRIVATE))
        history = CommandHistory(getSharedPreferences("history", MODE_PRIVATE))
        notes = PlayerNotes(getSharedPreferences("notes", MODE_PRIVATE))
        shortcuts = ShortcutStore(getSharedPreferences("shortcuts", MODE_PRIVATE))
        snapshots = SnapshotStore(File(noBackupFilesDir, "snapshots"), KeystoreBox("remotemc.snapshots"))
        updates = AppUpdates(this) { settings.settings.value.autoUpdate }
    }
}

val Context.app: RemoteMCApp get() = applicationContext as RemoteMCApp
