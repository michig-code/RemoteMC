package de.remotemc.app

import android.app.Application
import android.content.Context
import de.remotemc.app.data.CommandHistory
import de.remotemc.app.data.KeystoreBox
import de.remotemc.app.data.ServerRepository
import de.remotemc.app.data.SettingsStore

class RemoteMCApp : Application() {
    lateinit var servers: ServerRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var history: CommandHistory
        private set

    override fun onCreate() {
        super.onCreate()
        servers = ServerRepository(getSharedPreferences("servers", MODE_PRIVATE), KeystoreBox())
        settings = SettingsStore(getSharedPreferences("settings", MODE_PRIVATE))
        history = CommandHistory(getSharedPreferences("history", MODE_PRIVATE))
    }
}

val Context.app: RemoteMCApp get() = applicationContext as RemoteMCApp
