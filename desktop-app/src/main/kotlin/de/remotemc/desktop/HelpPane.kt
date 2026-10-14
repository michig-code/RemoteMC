package de.remotemc.desktop

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.text.Normalizer
import java.util.Locale

/** The sections of the help page (the same as on the phone). */
enum class HelpSection(val label: StringResource) {
    START(Res.string.help_section_start),
    OVERVIEW(Res.string.help_section_overview),
    PLAYERS(Res.string.help_section_players),
    CONSOLE(Res.string.help_section_console),
    MANAGE(Res.string.help_section_manage),
    APP(Res.string.help_section_app),
}

/** One feature: what it does and where it is. The texts are shared with the phone app. */
class DeskHelpTopic(
    val id: String,
    val section: HelpSection,
    val icon: ImageVector,
    val title: StringResource,
    val where: StringResource,
    val text: StringResource,
    val keys: StringResource,
)

/** Everything the phone's help page explains, without the home-screen widget (the PC has none). */
val DeskHelpTopics = listOf(
    DeskHelpTopic("add_server", HelpSection.START, Icons.Outlined.AddCircleOutline, Res.string.help_add_server_title, Res.string.help_add_server_where, Res.string.help_add_server_text, Res.string.help_add_server_keys),
    DeskHelpTopic("demo", HelpSection.START, Icons.Outlined.PlayCircleOutline, Res.string.help_demo_title, Res.string.help_demo_where, Res.string.help_demo_text, Res.string.help_demo_keys),
    DeskHelpTopic("organize", HelpSection.START, Icons.Outlined.Sort, Res.string.help_organize_title, Res.string.help_organize_where, Res.string.help_organize_text, Res.string.help_organize_keys),
    DeskHelpTopic("roles", HelpSection.START, Icons.Outlined.AdminPanelSettings, Res.string.help_roles_title, Res.string.help_roles_where, Res.string.help_roles_text, Res.string.help_roles_keys),
    DeskHelpTopic("overview", HelpSection.OVERVIEW, Icons.Outlined.Dashboard, Res.string.help_overview_title, Res.string.help_overview_where, Res.string.help_overview_text, Res.string.help_overview_keys),
    DeskHelpTopic("connection", HelpSection.OVERVIEW, Icons.Outlined.CloudOff, Res.string.help_connection_title, Res.string.help_connection_where, Res.string.help_connection_text, Res.string.help_connection_keys),
    DeskHelpTopic("history", HelpSection.OVERVIEW, Icons.Outlined.ShowChart, Res.string.help_history_title, Res.string.help_history_where, Res.string.help_history_text, Res.string.help_history_keys),
    DeskHelpTopic("customize", HelpSection.OVERVIEW, Icons.Outlined.DashboardCustomize, Res.string.help_customize_title, Res.string.help_customize_where, Res.string.help_customize_text, Res.string.help_customize_keys),
    DeskHelpTopic("worlds", HelpSection.OVERVIEW, Icons.Outlined.Public, Res.string.help_worlds_title, Res.string.help_worlds_where, Res.string.help_worlds_text, Res.string.help_worlds_keys),
    DeskHelpTopic("players", HelpSection.PLAYERS, Icons.Outlined.Person, Res.string.help_players_title, Res.string.help_players_where, Res.string.help_players_text, Res.string.help_players_keys),
    DeskHelpTopic("multiselect", HelpSection.PLAYERS, Icons.Outlined.Group, Res.string.help_multiselect_title, Res.string.help_multiselect_where, Res.string.help_multiselect_text, Res.string.help_multiselect_keys),
    DeskHelpTopic("inventory", HelpSection.PLAYERS, Icons.Outlined.Backpack, Res.string.help_inventory_title, Res.string.help_inventory_where, Res.string.help_inventory_text, Res.string.help_inventory_keys),
    DeskHelpTopic("whitelist", HelpSection.PLAYERS, Icons.Outlined.Shield, Res.string.help_whitelist_title, Res.string.help_whitelist_where, Res.string.help_whitelist_text, Res.string.help_whitelist_keys),
    DeskHelpTopic("console", HelpSection.CONSOLE, Icons.Outlined.Terminal, Res.string.help_console_title, Res.string.help_console_where, Res.string.help_console_text, Res.string.help_console_keys),
    DeskHelpTopic("command_menu", HelpSection.CONSOLE, Icons.AutoMirrored.Outlined.MenuBook, Res.string.help_command_menu_title, Res.string.help_command_menu_where, Res.string.help_command_menu_text, Res.string.help_command_menu_keys),
    DeskHelpTopic("console_search", HelpSection.CONSOLE, Icons.Outlined.Search, Res.string.help_console_search_title, Res.string.help_console_search_where, Res.string.help_console_search_text, Res.string.help_console_search_keys),
    DeskHelpTopic("chat", HelpSection.CONSOLE, Icons.AutoMirrored.Outlined.Chat, Res.string.help_chat_title, Res.string.help_chat_where, Res.string.help_chat_text, Res.string.help_chat_keys),
    DeskHelpTopic("schedules", HelpSection.MANAGE, Icons.Outlined.Schedule, Res.string.help_schedules_title, Res.string.help_schedules_where, Res.string.help_schedules_text, Res.string.help_schedules_keys),
    DeskHelpTopic("plugins", HelpSection.MANAGE, Icons.Outlined.Extension, Res.string.help_plugins_title, Res.string.help_plugins_where, Res.string.help_plugins_text, Res.string.help_plugins_keys),
    DeskHelpTopic("files", HelpSection.MANAGE, Icons.Outlined.FolderOpen, Res.string.help_files_title, Res.string.help_files_where, Res.string.help_files_text, Res.string.help_files_keys),
    DeskHelpTopic("notifications", HelpSection.APP, Icons.Outlined.Notifications, Res.string.help_notifications_title, Res.string.help_notifications_where, Res.string.help_notifications_text, Res.string.help_notifications_keys),
    DeskHelpTopic("updates", HelpSection.APP, Icons.Outlined.SystemUpdate, Res.string.help_updates_title, Res.string.help_updates_where, Res.string.help_updates_text, Res.string.help_updates_keys),
    DeskHelpTopic("changelog", HelpSection.APP, Icons.Outlined.NewReleases, Res.string.help_changelog_title, Res.string.help_changelog_where, Res.string.help_changelog_text, Res.string.help_changelog_keys),
    DeskHelpTopic("appearance", HelpSection.APP, Icons.Outlined.Palette, Res.string.help_appearance_title, Res.string.help_appearance_where, Res.string.help_appearance_text, Res.string.help_appearance_keys),
)

/** Finds topics by words, ignoring case and accents ("Rucksack" finds "rücksack"). */
object DeskHelpSearch {
    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun matches(haystack: String, query: String): Boolean {
        val words = normalize(query).split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val text = normalize(haystack)
        return words.all { it in text }
    }
}

/** Help & guide: every feature with search, like on the phone (Settings › Help or F1). */
@Composable
fun HelpPane(onBack: () -> Unit, initialQuery: String = "") {
    var query by remember { mutableStateOf(initialQuery) }
    var open by remember { mutableStateOf(setOf<String>()) }
    // The searchable text of each topic: title, where, text and extra key words.
    val searchable = DeskHelpTopics.associate { t ->
        t.id to listOf(stringResource(t.title), stringResource(t.where), stringResource(t.text), stringResource(t.keys)).joinToString(" ")
    }
    val shown = DeskHelpTopics.filter { query.isBlank() || DeskHelpSearch.matches(searchable.getValue(it.id), query) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 820.dp).fillMaxSize(),
                contentPadding = PaddingValues(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(Res.string.help_title), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(Res.string.help_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    TextField(
                        query, { query = it },
                        placeholder = { Text(stringResource(Res.string.help_search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(Res.string.action_clear)) }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(stringResource(Res.string.desktop_help_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
                if (shown.isEmpty()) {
                    item { Text(stringResource(Res.string.help_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
                }
                HelpSection.entries.forEach { section ->
                    val topics = shown.filter { it.section == section }
                    if (topics.isNotEmpty()) {
                        item(key = "section:" + section.name) { SectionTitle(stringResource(section.label)) }
                        items(topics, key = { it.id }) { t ->
                            // While searching every match is open; otherwise a click opens it.
                            HelpCard(t, expanded = query.isNotBlank() || t.id in open) {
                                open = if (t.id in open) open - t.id else open + t.id
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpCard(topic: DeskHelpTopic, expanded: Boolean, onToggle: () -> Unit) {
    McCard(Modifier.fillMaxWidth().animateContentSize(), onClick = onToggle) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(topic.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(topic.title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(topic.where), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Default.ExpandMore, null, Modifier.rotate(if (expanded) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Text(stringResource(topic.text), modifier = Modifier.padding(top = 10.dp, start = 36.dp))
            }
        }
    }
}
