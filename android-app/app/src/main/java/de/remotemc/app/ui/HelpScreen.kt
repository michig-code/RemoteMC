package de.remotemc.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.ui.components.LocalAnimations
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.SectionTitle
import de.remotemc.app.ui.util.rememberHaptics
import java.text.Normalizer
import java.util.Locale

enum class HelpSection(@StringRes val label: Int) {
    START(R.string.help_section_start),
    OVERVIEW(R.string.help_section_overview),
    PLAYERS(R.string.help_section_players),
    CONSOLE(R.string.help_section_console),
    MANAGE(R.string.help_section_manage),
    APP(R.string.help_section_app),
}

/** Where to tap on a help picture, as fractions of its width and height. */
data class Spot(val left: Float, val top: Float, val width: Float, val height: Float)

/** One feature of the app: what it does, how to get there and a picture of it. */
data class HelpTopic(
    val id: String,
    val section: HelpSection,
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val where: Int,
    @StringRes val text: Int,
    @StringRes val keys: Int,
    @DrawableRes val image: Int? = null,
    val spot: Spot? = null,
)

/**
 * Every topic of the help page. The pictures are the app's screenshot tests, made smaller with
 * tools/help_images.py; the spots mark the place to tap on them.
 */
val HelpTopics = listOf(
    HelpTopic("add_server", HelpSection.START, Icons.Outlined.AddCircleOutline, R.string.help_add_server_title, R.string.help_add_server_where,
        R.string.help_add_server_text, R.string.help_add_server_keys, R.drawable.help_pair, Spot(0.03f, 0.445f, 0.94f, 0.115f)),
    HelpTopic("demo", HelpSection.START, Icons.Outlined.PlayCircleOutline, R.string.help_demo_title, R.string.help_demo_where,
        R.string.help_demo_text, R.string.help_demo_keys, R.drawable.help_demo, Spot(0.26f, 0.435f, 0.48f, 0.075f)),
    HelpTopic("organize", HelpSection.START, Icons.Outlined.Sort, R.string.help_organize_title, R.string.help_organize_where,
        R.string.help_organize_text, R.string.help_organize_keys, R.drawable.help_servers, Spot(0.78f, 0.29f, 0.17f, 0.075f)),
    HelpTopic("roles", HelpSection.START, Icons.Outlined.AdminPanelSettings, R.string.help_roles_title, R.string.help_roles_where,
        R.string.help_roles_text, R.string.help_roles_keys),
    HelpTopic("overview", HelpSection.OVERVIEW, Icons.Outlined.Dashboard, R.string.help_overview_title, R.string.help_overview_where,
        R.string.help_overview_text, R.string.help_overview_keys, R.drawable.help_overview, Spot(0.02f, 0.905f, 0.25f, 0.09f)),
    HelpTopic("connection", HelpSection.OVERVIEW, Icons.Outlined.CloudOff, R.string.help_connection_title, R.string.help_connection_where,
        R.string.help_connection_text, R.string.help_connection_keys, R.drawable.help_offline, Spot(0.1f, 0.035f, 0.55f, 0.06f)),
    HelpTopic("history", HelpSection.OVERVIEW, Icons.Outlined.ShowChart, R.string.help_history_title, R.string.help_history_where,
        R.string.help_history_text, R.string.help_history_keys, R.drawable.help_history),
    HelpTopic("customize", HelpSection.OVERVIEW, Icons.Outlined.DashboardCustomize, R.string.help_customize_title, R.string.help_customize_where,
        R.string.help_customize_text, R.string.help_customize_keys, R.drawable.help_customize, Spot(0.02f, 0.32f, 0.96f, 0.33f)),
    HelpTopic("worlds", HelpSection.OVERVIEW, Icons.Outlined.Public, R.string.help_worlds_title, R.string.help_worlds_where,
        R.string.help_worlds_text, R.string.help_worlds_keys, R.drawable.help_manage, Spot(0.02f, 0.0f, 0.96f, 0.505f)),
    HelpTopic("players", HelpSection.PLAYERS, Icons.Outlined.Person, R.string.help_players_title, R.string.help_players_where,
        R.string.help_players_text, R.string.help_players_keys, R.drawable.help_players, Spot(0.02f, 0.18f, 0.96f, 0.175f)),
    HelpTopic("multiselect", HelpSection.PLAYERS, Icons.Outlined.Group, R.string.help_multiselect_title, R.string.help_multiselect_where,
        R.string.help_multiselect_text, R.string.help_multiselect_keys, R.drawable.help_players_selected, Spot(0.02f, 0.76f, 0.96f, 0.24f)),
    HelpTopic("inventory", HelpSection.PLAYERS, Icons.Outlined.Backpack, R.string.help_inventory_title, R.string.help_inventory_where,
        R.string.help_inventory_text, R.string.help_inventory_keys, R.drawable.help_inventory),
    HelpTopic("whitelist", HelpSection.PLAYERS, Icons.Outlined.Shield, R.string.help_whitelist_title, R.string.help_whitelist_where,
        R.string.help_whitelist_text, R.string.help_whitelist_keys, R.drawable.help_whitelist, Spot(0.33f, 0.02f, 0.65f, 0.1f)),
    HelpTopic("console", HelpSection.CONSOLE, Icons.Outlined.Terminal, R.string.help_console_title, R.string.help_console_where,
        R.string.help_console_text, R.string.help_console_keys, R.drawable.help_console_suggest, Spot(0.01f, 0.81f, 0.98f, 0.19f)),
    HelpTopic("command_menu", HelpSection.CONSOLE, Icons.AutoMirrored.Outlined.MenuBook, R.string.help_command_menu_title, R.string.help_command_menu_where,
        R.string.help_command_menu_text, R.string.help_command_menu_keys, R.drawable.help_console, Spot(0.7f, 0.835f, 0.15f, 0.06f)),
    HelpTopic("console_search", HelpSection.CONSOLE, Icons.Outlined.Search, R.string.help_console_search_title, R.string.help_console_search_where,
        R.string.help_console_search_text, R.string.help_console_search_keys, R.drawable.help_console, Spot(0.55f, 0.095f, 0.27f, 0.05f)),
    HelpTopic("chat", HelpSection.CONSOLE, Icons.AutoMirrored.Outlined.Chat, R.string.help_chat_title, R.string.help_chat_where,
        R.string.help_chat_text, R.string.help_chat_keys, R.drawable.help_chat, Spot(0.01f, 0.82f, 0.98f, 0.07f)),
    HelpTopic("schedules", HelpSection.MANAGE, Icons.Outlined.Schedule, R.string.help_schedules_title, R.string.help_schedules_where,
        R.string.help_schedules_text, R.string.help_schedules_keys, R.drawable.help_manage, Spot(0.02f, 0.545f, 0.96f, 0.15f)),
    HelpTopic("plugins", HelpSection.MANAGE, Icons.Outlined.Extension, R.string.help_plugins_title, R.string.help_plugins_where,
        R.string.help_plugins_text, R.string.help_plugins_keys, R.drawable.help_manage, Spot(0.02f, 0.71f, 0.96f, 0.13f)),
    HelpTopic("files", HelpSection.MANAGE, Icons.Outlined.FolderOpen, R.string.help_files_title, R.string.help_files_where,
        R.string.help_files_text, R.string.help_files_keys, R.drawable.help_files),
    HelpTopic("notifications", HelpSection.APP, Icons.Outlined.Notifications, R.string.help_notifications_title, R.string.help_notifications_where,
        R.string.help_notifications_text, R.string.help_notifications_keys, R.drawable.help_settings, Spot(0.03f, 0.82f, 0.94f, 0.15f)),
    HelpTopic("widget", HelpSection.APP, Icons.Outlined.Widgets, R.string.help_widget_title, R.string.help_widget_where,
        R.string.help_widget_text, R.string.help_widget_keys),
    HelpTopic("appearance", HelpSection.APP, Icons.Outlined.Palette, R.string.help_appearance_title, R.string.help_appearance_where,
        R.string.help_appearance_text, R.string.help_appearance_keys, R.drawable.help_settings, Spot(0.03f, 0.33f, 0.94f, 0.45f)),
)

/** Search that ignores case and accents ("Übersicht" matches "ubersicht"); every word must appear. */
object HelpSearch {
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun matches(haystack: String, query: String): Boolean {
        val words = normalize(query).split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val text = normalize(haystack)
        return words.all { it in text }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, initialQuery: String = "", initialOpen: Set<String> = emptySet()) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var open by rememberSaveable { mutableStateOf(initialOpen.toList()) }
    val searchable = remember(context) {
        HelpTopics.associate { t ->
            t.id to listOf(t.title, t.where, t.text, t.keys).joinToString(" ") { context.getString(it) }
        }
    }
    val results = if (query.isBlank()) HelpTopics else HelpTopics.filter { HelpSearch.matches(searchable.getValue(it.id), query) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 720.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "search") {
                    Column {
                        Text(
                            stringResource(R.string.help_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(60) },
                            placeholder = { Text(stringResource(R.string.help_search_hint)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear)) }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (results.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.help_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                // Sections while browsing, one flat list while searching.
                val groups = if (query.isBlank()) results.groupBy { it.section }.toList() else listOf(null to results)
                groups.forEach { (section, topics) ->
                    if (section != null) {
                        item(key = "section-${section.name}") {
                            SectionTitle(stringResource(section.label), Modifier.padding(top = 8.dp).semantics { heading() })
                        }
                    }
                    items(topics, key = { it.id }) { topic ->
                        // A single search hit opens right away.
                        val expanded = topic.id in open || (query.isNotBlank() && results.size == 1)
                        HelpCard(topic, expanded) {
                            haptics.tap()
                            open = if (topic.id in open) open - topic.id else open + topic.id
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpCard(topic: HelpTopic, expanded: Boolean, onToggle: () -> Unit) {
    val animate = LocalAnimations.current
    McCard(Modifier.fillMaxWidth(), onClick = onToggle) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(topic.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(topic.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(topic.where),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            AnimatedVisibility(
                expanded,
                enter = if (animate) expandVertically() + fadeIn() else EnterTransition.None,
                exit = if (animate) shrinkVertically() + fadeOut() else ExitTransition.None,
            ) {
                Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(topic.text),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    topic.image?.let { image ->
                        Spacer(Modifier.size(14.dp))
                        HelpPicture(image, topic.spot, stringResource(topic.title))
                    }
                }
            }
        }
    }
}

/** A screenshot with the place to tap ringed and the rest slightly dimmed. */
@Composable
private fun HelpPicture(@DrawableRes image: Int, spot: Spot?, description: String) {
    val ring = MaterialTheme.colorScheme.primary
    // The ring pulses three times when the picture appears, then stays.
    val pulse = remember { Animatable(1f) }
    val animate = LocalAnimations.current && spot != null
    LaunchedEffect(image, animate) {
        if (animate) {
            repeat(3) {
                pulse.animateTo(0.35f, tween(450))
                pulse.animateTo(1f, tween(450))
            }
        }
    }
    val shape = MaterialTheme.shapes.large
    Image(
        painterResource(image),
        contentDescription = description,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .drawWithContent {
                drawContent()
                if (spot != null) {
                    val pad = 4.dp.toPx()
                    val left = spot.left * size.width - pad
                    val top = spot.top * size.height - pad
                    val w = spot.width * size.width + 2 * pad
                    val h = spot.height * size.height + 2 * pad
                    val dim = Color.Black.copy(alpha = 0.35f)
                    drawRect(dim, Offset.Zero, Size(size.width, top.coerceAtLeast(0f)))
                    drawRect(dim, Offset(0f, top + h), Size(size.width, (size.height - top - h).coerceAtLeast(0f)))
                    drawRect(dim, Offset(0f, top), Size(left.coerceAtLeast(0f), h))
                    drawRect(dim, Offset(left + w, top), Size((size.width - left - w).coerceAtLeast(0f), h))
                    drawRoundRect(
                        ring.copy(alpha = pulse.value), Offset(left, top), Size(w, h),
                        CornerRadius(10.dp.toPx()), style = Stroke(3.dp.toPx()),
                    )
                }
            },
    )
}
