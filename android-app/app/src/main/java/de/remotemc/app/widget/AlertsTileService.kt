package de.remotemc.app.widget

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import de.remotemc.app.MainActivity
import de.remotemc.app.R
import de.remotemc.app.app
import de.remotemc.app.notify.WatchService

/** Quick settings tile: background notifications on or off, with how many servers are online. */
class AlertsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val enable = !app.settings.settings.value.alerts.enabled
        if (enable && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            openApp() // the permission can only be asked for in the app
            return
        }
        app.settings.update { it.copy(alerts = it.alerts.copy(enabled = enable)) }
        if (enable) WatchService.sync(this)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val data = WidgetStore.load(this)
        tile.state = if (app.settings.settings.value.alerts.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (data.servers.isEmpty()) null else getString(R.string.tile_online, data.onlineCount, data.servers.size)
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
