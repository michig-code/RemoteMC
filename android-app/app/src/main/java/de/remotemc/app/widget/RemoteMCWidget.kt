package de.remotemc.app.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.remotemc.app.MainActivity
import de.remotemc.app.R
import de.remotemc.app.ui.theme.McColors
import java.util.Date

/** Home screen widget: every server with its status, players and TPS. */
class RemoteMCWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetStore.load(context)
        provideContent { GlanceTheme { Content(context, data) } }
    }

    @Composable
    private fun Content(context: Context, data: WidgetData) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    ImageProvider(R.drawable.ic_launcher_foreground), null,
                    GlanceModifier.size(28.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    context.getString(R.string.app_name),
                    GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                )
                Image(
                    ImageProvider(R.drawable.ic_widget_refresh), context.getString(R.string.action_refresh),
                    GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (data.servers.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(context.getString(R.string.widget_empty), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                }
            } else {
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(data.servers, itemId = { it.id.hashCode().toLong() }) { server -> ServerRow(context, server) }
                }
                if (data.updatedAt > 0) {
                    Text(
                        context.getString(R.string.widget_updated, DateFormat.getTimeFormat(context).format(Date(data.updatedAt))),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }
            }
        }
    }

    @Composable
    private fun ServerRow(context: Context, server: WidgetServer) {
        // A unique data URI keeps the click intents of the rows apart.
        val open = Intent(context, MainActivity::class.java)
            .setData("remotemc-widget://server/${server.id}".toUri())
            .putExtra(MainActivity.EXTRA_SERVER_ID, server.id)
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier.size(10.dp).cornerRadius(5.dp)
                    .background(ColorProvider(if (server.online) McColors.Online else McColors.Offline)),
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            Text(
                server.name, GlanceModifier.defaultWeight(), maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                if (server.online) {
                    "${server.players}/${server.maxPlayers}" + (server.tps?.let { " · %.1f TPS".format(it) } ?: "")
                } else {
                    context.getString(R.string.status_offline_short)
                },
                style = TextStyle(
                    color = if (server.online) GlanceTheme.colors.onSurfaceVariant else ColorProvider(Color(0xFFF87171)),
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        StatusRefresher.refreshNow(context)
    }
}

class WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemoteMCWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        StatusRefresher.schedule(context)
        StatusRefresher.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        StatusRefresher.cancel(context)
    }
}
