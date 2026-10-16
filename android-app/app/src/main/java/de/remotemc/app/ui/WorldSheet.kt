package de.remotemc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.GameRuleInfo
import de.remotemc.app.data.WorldDetails
import de.remotemc.app.data.daylightCycle
import de.remotemc.app.data.timeAfter
import de.remotemc.app.ui.components.SectionTitle
import de.remotemc.app.ui.util.errorText
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The world's time, counting on between fetches like the in-game clock. */
@Composable
private fun liveTime(d: WorldDetails): Long {
    val fetchedAt = remember(d) { System.currentTimeMillis() }
    var now by remember(d) { mutableLongStateOf(fetchedAt) }
    LaunchedEffect(d) {
        while (d.daylightCycle) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    return d.timeAfter(now - fetchedAt)
}

/** Minecraft time (0 = 06:00) as a clock: "18:30". */
internal fun clockTime(ticks: Long): String {
    val t = ((ticks % 24000) + 24000) % 24000
    val hours = (t / 1000 + 6) % 24
    val minutes = (t % 1000) * 60 / 1000
    return "%02d:%02d".format(hours, minutes)
}

private val Times = listOf(
    Triple("morning", R.string.time_morning, Icons.Outlined.WbTwilight),
    Triple("day", R.string.time_day, Icons.Outlined.LightMode),
    Triple("noon", R.string.time_noon, Icons.Outlined.WbSunny),
    Triple("evening", R.string.time_evening, Icons.Outlined.WbTwilight),
    Triple("night", R.string.time_night, Icons.Outlined.DarkMode),
    Triple("midnight", R.string.time_midnight, Icons.Outlined.Bedtime),
)

/** Time, weather, difficulty and game rules of one world. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorldSheet(world: String, actions: WorldActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<WorldDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<GameRuleInfo?>(null) }

    fun run(call: suspend () -> WorldDetails) {
        scope.launch {
            busy = true
            try {
                details = call()
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = context.errorText(e)
                haptics.error()
            } finally {
                busy = false
            }
        }
    }

    fun change(action: String, body: JSONObject) {
        haptics.tap()
        run { actions.change(world, action, body) }
    }

    LaunchedEffect(world) { run { actions.load(world) } }
    // Fetch the real time again now and then (someone may change it in the game).
    LaunchedEffect(world) {
        while (true) {
            delay(10_000)
            if (!busy) runCatching { actions.load(world) }.onSuccess { details = it }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val d = details
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(world, style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.world_controls), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            error?.let { item(key = "error") { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (d == null) {
                if (error == null) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                return@LazyColumn
            }

            if (!d.hasSky) {
                item(key = "no-sky") {
                    Text(
                        stringResource(R.string.world_no_sky),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                item(key = "time") {
                    SectionTitle(stringResource(R.string.world_time) + " · " + clockTime(liveTime(d)))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Times.forEach { (id, label, icon) ->
                            AssistChip(
                                onClick = { change("time", JSONObject().put("time", id)) },
                                label = { Text(stringResource(label)) },
                                leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                                enabled = !busy,
                            )
                        }
                    }
                }
                item(key = "weather") {
                    SectionTitle(stringResource(R.string.world_weather))
                    val current = when {
                        d.thunder -> "thunder"
                        d.storm -> "rain"
                        else -> "clear"
                    }
                    Choice(
                        listOf("clear" to R.string.weather_clear, "rain" to R.string.weather_rain, "thunder" to R.string.weather_thunder),
                        current, enabled = !busy,
                    ) { change("weather", JSONObject().put("weather", it)) }
                }
            }
            item(key = "difficulty") {
                SectionTitle(stringResource(R.string.world_difficulty))
                Choice(
                    listOf(
                        "PEACEFUL" to R.string.difficulty_peaceful, "EASY" to R.string.difficulty_easy,
                        "NORMAL" to R.string.difficulty_normal, "HARD" to R.string.difficulty_hard,
                    ),
                    d.difficulty, enabled = !busy,
                ) { change("difficulty", JSONObject().put("difficulty", it.lowercase())) }
            }
            item(key = "rules-title") {
                SectionTitle(stringResource(R.string.world_gamerules))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.world_gamerules_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(d.gameRules.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, key = { "rule:" + it.name }) { rule ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        if (rule.isBoolean) {
                            change("gamerule", JSONObject().put("rule", rule.name).put("value", rule.value != "true"))
                        } else {
                            editing = rule
                        }
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(rule.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (rule.isBoolean) {
                        Switch(rule.value == "true", onCheckedChange = null, enabled = !busy)
                    } else {
                        Text(rule.value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    editing?.let { rule ->
        var value by remember(rule.name) { mutableStateOf(rule.value) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(rule.name) },
            text = {
                OutlinedTextField(
                    value, { v -> value = v.filter { it.isDigit() || it == '-' }.take(9) },
                    label = { Text(stringResource(R.string.world_gamerule_value)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = value.toIntOrNull() != null,
                    onClick = {
                        editing = null
                        change("gamerule", JSONObject().put("rule", rule.name).put("value", value.toInt()))
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(options: List<Pair<String, Int>>, selected: String, enabled: Boolean, onPick: (String) -> Unit) {
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

