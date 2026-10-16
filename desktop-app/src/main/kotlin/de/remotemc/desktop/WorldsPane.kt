package de.remotemc.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.GameRuleInfo
import de.remotemc.app.data.WorldDetails
import de.remotemc.app.data.daylightCycle
import de.remotemc.app.data.timeAfter
import de.remotemc.desktop.res.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.json.JSONObject

/** The world's time, counting on between fetches like the in-game clock. */
@Composable
private fun liveTime(d: WorldDetails): Long {
    val fetchedAt = remember(d) { System.currentTimeMillis() }
    var now by remember(d) { mutableStateOf(fetchedAt) }
    LaunchedEffect(d) {
        while (d.daylightCycle) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    return d.timeAfter(now - fetchedAt)
}

/** Minecraft time (0 = 06:00) as a clock: "18:30". */
fun clockTime(ticks: Long): String {
    val t = ((ticks % 24000) + 24000) % 24000
    val hours = (t / 1000 + 6) % 24
    val minutes = (t % 1000) * 60 / 1000
    return "%02d:%02d".format(hours, minutes)
}

private val Times = listOf(
    Triple("morning", Res.string.time_morning, Icons.Outlined.WbTwilight),
    Triple("day", Res.string.time_day, Icons.Outlined.LightMode),
    Triple("noon", Res.string.time_noon, Icons.Outlined.WbSunny),
    Triple("evening", Res.string.time_evening, Icons.Outlined.WbTwilight),
    Triple("night", Res.string.time_night, Icons.Outlined.DarkMode),
    Triple("midnight", Res.string.time_midnight, Icons.Outlined.Bedtime),
)

/** Time, weather, difficulty and game rules of each world (admins). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldsPane(session: ServerSession, initialWorld: String? = null) {
    val worlds = session.status?.worlds.orEmpty().map { it.name }
    var selected by remember { mutableStateOf(initialWorld?.takeIf { it in worlds } ?: worlds.firstOrNull()) }
    val world = selected?.takeIf { it in worlds } ?: worlds.firstOrNull() ?: return
    val manage = session.manage
    val (loader, reload) = rememberLoader(world) { manage.world(world) }
    // Fetch the real time again now and then (someone may change it in the game).
    LaunchedEffect(world) {
        while (true) {
            delay(10_000)
            if (!loader.busy) runCatching { loader.data = manage.world(world) }
        }
    }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<GameRuleInfo?>(null) }
    fun change(action: String, body: JSONObject) = loader.run { manage.worldAction(world, action, body) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                worlds.forEach { w ->
                    FilterChip(
                        w == world, { selected = w }, label = { Text(w) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            if (loader.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        val d = loader.data
        if (d == null) {
            LoadingOrError(loader.error, reload)
            return@Column
        }
        loader.error?.let { ErrorLine(it) }
        SplitView("worlds", 0.48f, Modifier.fillMaxSize(), minLeft = 300.dp, minRight = 260.dp, left = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!d.hasSky) {
                    // Nether and End: no day and night, no weather.
                    McCard(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(Res.string.world_no_sky),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                } else {
                    McCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.world_time), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(clockTime(liveTime(d)), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Times.forEach { (id, label, icon) ->
                                    AssistChip(
                                        onClick = { change("time", JSONObject().put("time", id)) },
                                        label = { Text(stringResource(label)) },
                                        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                                        enabled = !loader.busy,
                                    )
                                }
                            }
                        }
                    }
                    McCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(Res.string.world_weather), style = MaterialTheme.typography.titleMedium)
                            val current = when {
                                d.thunder -> "thunder"
                                d.storm -> "rain"
                                else -> "clear"
                            }
                            Choice(
                                listOf("clear" to Res.string.weather_clear, "rain" to Res.string.weather_rain, "thunder" to Res.string.weather_thunder),
                                current, !loader.busy,
                            ) { change("weather", JSONObject().put("weather", it)) }
                        }
                    }
                }
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(Res.string.world_difficulty), style = MaterialTheme.typography.titleMedium)
                        Choice(
                            listOf(
                                "PEACEFUL" to Res.string.difficulty_peaceful, "EASY" to Res.string.difficulty_easy,
                                "NORMAL" to Res.string.difficulty_normal, "HARD" to Res.string.difficulty_hard,
                            ),
                            d.difficulty, !loader.busy,
                        ) { change("difficulty", JSONObject().put("difficulty", it.lowercase())) }
                    }
                }
            }
        }, right = {
            McCard(Modifier.fillMaxSize().padding(bottom = 24.dp)) {
                Column(Modifier.padding(top = 18.dp, start = 18.dp, end = 18.dp)) {
                    Text(stringResource(Res.string.world_gamerules), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(10.dp))
                    SearchField(query, { query = it }, stringResource(Res.string.world_gamerules_search))
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(d.gameRules.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, key = { it.name }) { rule ->
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !loader.busy) {
                                    if (rule.isBoolean) {
                                        change("gamerule", JSONObject().put("rule", rule.name).put("value", rule.value != "true"))
                                    } else {
                                        editing = rule
                                    }
                                }.padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(rule.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (rule.isBoolean) {
                                    Switch(rule.value == "true", onCheckedChange = null, enabled = !loader.busy)
                                } else {
                                    Text(rule.value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    editing?.let { rule ->
        var value by remember(rule.name) { mutableStateOf(rule.value) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(rule.name) },
            text = {
                OutlinedTextField(
                    value, { v -> value = v.filter { it.isDigit() || it == '-' }.take(9) },
                    label = { Text(stringResource(Res.string.world_gamerule_value)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = value.toIntOrNull() != null, onClick = {
                    editing = null
                    change("gamerule", JSONObject().put("rule", rule.name).put("value", value.toInt()))
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(options: List<Pair<String, StringResource>>, selected: String, enabled: Boolean, onPick: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (id, label) ->
            SegmentedButton(
                selected = selected.equals(id, ignoreCase = true),
                onClick = { if (!selected.equals(id, ignoreCase = true)) onPick(id) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                enabled = enabled,
                icon = {},
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier.fillMaxWidth()) = TextField(
    value = value,
    onValueChange = onChange,
    placeholder = { Text(placeholder) },
    leadingIcon = { Icon(Icons.Outlined.Search, null) },
    singleLine = true,
    shape = CircleShape,
    colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    modifier = modifier,
)
