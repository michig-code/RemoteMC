package de.remotemc.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.AlertSettings
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(model: AppModel, onBack: () -> Unit, version: String = AppVersion) {
    val settings by model.settings.settings.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
                }
                SectionTitle(stringResource(Res.string.pair_device_name))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            settings.deviceName, { v -> if (v.length <= 40) model.settings.update { it.copy(deviceName = v) } },
                            label = { Text(stringResource(Res.string.pair_device_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(Res.string.settings_device_name_text), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                SectionTitle(stringResource(Res.string.settings_appearance))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        listOf(ThemeMode.SYSTEM to Res.string.settings_theme_system, ThemeMode.LIGHT to Res.string.settings_theme_light,
                            ThemeMode.DARK to Res.string.settings_theme_dark).forEach { (mode, label) ->
                            Row(Modifier.fillMaxWidth().clickable { model.settings.update { it.copy(theme = mode) } }.padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(settings.theme == mode, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(label))
                            }
                        }
                        Text(stringResource(Res.string.settings_accent), modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccentPresets.forEach { hue -> Swatch(accentSwatch(hue), settings.accentHue == hue) { model.settings.update { it.copy(accentHue = hue) } } }
                        }
                        Text(stringResource(Res.string.settings_accent_custom), modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                        Slider(
                            value = settings.accentHue ?: 145f,
                            onValueChange = { v -> model.settings.update { it.copy(accentHue = v) } },
                            valueRange = 0f..359f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentSwatch(settings.accentHue),
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                SectionTitle(stringResource(Res.string.settings_behaviour))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SwitchRow(stringResource(Res.string.settings_animations), stringResource(Res.string.settings_animations_text), settings.animations) { v ->
                            model.settings.update { it.copy(animations = v) }
                        }
                    }
                }
                SectionTitle(stringResource(Res.string.settings_notifications))
                McCard(Modifier.fillMaxWidth()) {
                    AlertSettingsCard(settings.alerts) { change -> model.settings.update { it.copy(alerts = change(it.alerts)) } }
                }
                SectionTitle(stringResource(Res.string.settings_updates))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SwitchRow(stringResource(Res.string.settings_auto_update), stringResource(Res.string.settings_auto_update_text), settings.autoUpdate) { v ->
                            model.settings.update { it.copy(autoUpdate = v) }
                        }
                        model.updates?.let { updates -> UpdateRow(updates, version, model.quit) }
                    }
                }
                SectionTitle(stringResource(Res.string.settings_about))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppLogo(40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(Res.string.settings_version, version), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(stringResource(Res.string.settings_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Data: " + AppDirs.dir.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertSettingsCard(alerts: AlertSettings, onChange: ((AlertSettings) -> AlertSettings) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        SwitchRow(stringResource(Res.string.settings_notifications), stringResource(Res.string.desktop_notify_text), alerts.enabled) { on ->
            onChange { it.copy(enabled = on) }
        }
        AnimatedVisibility(
            alerts.enabled,
            enter = if (LocalAnimations.current) expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (LocalAnimations.current) shrinkVertically() + fadeOut() else ExitTransition.None,
        ) {
            Column {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(stringResource(Res.string.settings_notify_offline), stringResource(Res.string.settings_notify_offline_text), alerts.offline) { v ->
                    onChange { it.copy(offline = v) }
                }
                SwitchRow(stringResource(Res.string.settings_notify_tps), stringResource(Res.string.settings_notify_tps_text, alerts.tpsThreshold), alerts.lowTps) { v ->
                    onChange { it.copy(lowTps = v) }
                }
                if (alerts.lowTps) {
                    Slider(
                        value = alerts.tpsThreshold.toFloat(),
                        onValueChange = { v -> onChange { it.copy(tpsThreshold = v.roundToInt()) } },
                        valueRange = 5f..19f,
                        steps = 13,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                SwitchRow(stringResource(Res.string.settings_notify_joins), stringResource(Res.string.settings_notify_players_hint), alerts.joins) { v ->
                    onChange { it.copy(joins = v) }
                }
                if (alerts.joins) {
                    OutlinedTextField(
                        alerts.players, { v -> onChange { it.copy(players = v.take(300)) } },
                        label = { Text(stringResource(Res.string.settings_notify_players)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                SwitchRow(stringResource(Res.string.settings_notify_mentions), stringResource(Res.string.settings_notify_words_hint), alerts.mentions) { v ->
                    onChange { it.copy(mentions = v) }
                }
                if (alerts.mentions) {
                    OutlinedTextField(
                        alerts.words, { v -> onChange { it.copy(words = v.take(300)) } },
                        label = { Text(stringResource(Res.string.settings_notify_words)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
                SwitchRow(
                    stringResource(Res.string.settings_notify_quiet),
                    stringResource(Res.string.settings_notify_quiet_text_pc, clockText(alerts.quietFrom), clockText(alerts.quietTo)),
                    alerts.quiet,
                ) { v -> onChange { it.copy(quiet = v) } }
                if (alerts.quiet) {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeButton(stringResource(Res.string.settings_notify_quiet_from), alerts.quietFrom) { m -> onChange { it.copy(quietFrom = m) } }
                        TimeButton(stringResource(Res.string.settings_notify_quiet_to), alerts.quietTo) { m -> onChange { it.copy(quietTo = m) } }
                    }
                }
            }
        }
    }
}

private fun clockText(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** Picks a time in half-hour steps. */
@Composable
private fun TimeButton(label: String, minuteOfDay: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label + "  " + clockText(minuteOfDay)) }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 320.dp)) {
            (0 until 24 * 60 step 30).forEach { m ->
                DropdownMenuItem(text = { Text(clockText(m)) }, onClick = { open = false; onPick(m) })
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun UpdateRow(updates: UpdateState, version: String, quit: () -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val info = updates.available
        Text(
            when {
                updates.checking -> stringResource(Res.string.update_checking)
                info != null -> stringResource(Res.string.update_title, info.version)
                updates.error != null -> stringResource(Res.string.update_failed, updates.error.orEmpty())
                updates.upToDate -> stringResource(Res.string.update_up_to_date, version)
                else -> stringResource(Res.string.settings_version, version)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (updates.error != null && info == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (info != null) {
            Button(onClick = { dialog = true }) { Text(stringResource(Res.string.update_button)) }
        } else {
            OutlinedButton(onClick = updates::checkNow, enabled = !updates.checking) { Text(stringResource(Res.string.update_check_now)) }
        }
    }
    val info = updates.available
    if (dialog && info != null) UpdateDialog(updates, info, onQuit = quit) { dialog = false }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White) }
}

/** Version written by the build (-Prmc.version), or the dev default. */
val AppVersion: String = AppModel::class.java.getResource("/remotemc-version.txt")?.readText()?.trim()?.ifBlank { null }
    ?: AppModel::class.java.`package`?.implementationVersion ?: "1.0.0"
