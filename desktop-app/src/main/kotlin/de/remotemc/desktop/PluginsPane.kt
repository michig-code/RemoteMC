package de.remotemc.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.stringResource

/** Installed plugins with version, authors and whether they are enabled. */
@Composable
fun PluginsPane(session: ServerSession) {
    val manage = session.manage
    val (loader, reload) = rememberLoader { manage.plugins() }
    var query by remember { mutableStateOf("") }
    val list = loader.data
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (list == null) {
            LoadingOrError(loader.error, reload)
            return@Column
        }
        Row(Modifier.padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.dashboard_plugins) + " · ${list.size}",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
            )
            SearchField(query, { query = it }, stringResource(Res.string.plugins_search), Modifier.widthIn(max = 340.dp).fillMaxWidth())
        }
        LazyVerticalGrid(
            GridCells.Adaptive(320.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(list.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, key = { it.name }) { p ->
                McCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(
                            Icons.Outlined.Extension, null,
                            tint = if (p.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(8.dp))
                                Text(p.version, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!p.enabled) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge(stringResource(Res.string.plugins_disabled), McColors.Offline)
                                }
                            }
                            p.description?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (p.authors.isNotEmpty()) {
                                Text(
                                    stringResource(Res.string.plugins_by, p.authors.joinToString(", ")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
