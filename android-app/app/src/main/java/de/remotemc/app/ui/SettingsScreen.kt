package de.remotemc.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import de.remotemc.app.BuildConfig
import de.remotemc.app.R
import de.remotemc.app.data.AlertSettings
import de.remotemc.app.data.AppSettings
import de.remotemc.app.data.MAX_DEVICE_NAME
import de.remotemc.app.data.ThemeMode
import de.remotemc.app.ui.components.AppLogo
import de.remotemc.app.ui.components.LocalAnimations
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.SectionTitle
import de.remotemc.app.ui.theme.AccentPresets
import de.remotemc.app.ui.theme.accentSwatch
import de.remotemc.app.ui.util.Haptics
import kotlin.math.roundToInt

const val SOURCE_URL = "https://github.com/michig-code/RemoteMC"

/** Languages the app is translated into, with their names in that language. */
private val APP_LANGUAGES = listOf(
    "en" to "English",
    "de" to "Deutsch",
    "es" to "Español",
    "fr" to "Français",
    "it" to "Italiano",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "pt-BR" to "Português (Brasil)",
    "tr" to "Türkçe",
    "ru" to "Русский",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    versionName: String = BuildConfig.VERSION_NAME,
    onShowGuide: () -> Unit = {},
) {
    val uri = LocalUriHandler.current
    val view = LocalView.current
    var licenses by remember { mutableStateOf(false) }
    val currentLanguage = AppCompatDelegate.getApplicationLocales().get(0)?.let { locale ->
        APP_LANGUAGES.firstOrNull { it.first == locale.toLanguageTag() }?.first
            ?: APP_LANGUAGES.firstOrNull { it.first == locale.language }?.first
    }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(stringResource(R.string.settings_this_phone))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = settings.deviceName,
                            onValueChange = { v -> if (v.length <= MAX_DEVICE_NAME) onChange { it.copy(deviceName = v) } },
                            label = { Text(stringResource(R.string.pair_device_name)) },
                            singleLine = true,
                            isError = settings.deviceName.isBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_device_name_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SectionTitle(stringResource(R.string.settings_appearance))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to R.string.settings_theme_system,
                            ThemeMode.LIGHT to R.string.settings_theme_light,
                            ThemeMode.DARK to R.string.settings_theme_dark,
                        ).forEach { (mode, label) ->
                            RadioRow(stringResource(label), settings.theme == mode) { onChange { it.copy(theme = mode) } }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SwitchRow(
                                stringResource(R.string.settings_dynamic_color),
                                stringResource(R.string.settings_dynamic_color_text),
                                settings.dynamicColor,
                            ) { v -> onChange { it.copy(dynamicColor = v) } }
                        }
                        // Material You takes its colours from the wallpaper, so the choice only applies without it.
                        if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            AccentPicker(settings.accentHue) { hue -> onChange { it.copy(accentHue = hue) } }
                        }
                    }
                }

                SectionTitle(stringResource(R.string.settings_notifications))
                McCard(Modifier.fillMaxWidth()) {
                    AlertSettingsCard(settings.alerts) { change -> onChange { it.copy(alerts = change(it.alerts)) } }
                }

                SectionTitle(stringResource(R.string.settings_language))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        RadioRow(stringResource(R.string.settings_language_system), currentLanguage.isEmpty()) {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                        }
                        APP_LANGUAGES.forEach { (tag, label) ->
                            RadioRow(label, currentLanguage == tag) {
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            }
                        }
                    }
                }

                SectionTitle(stringResource(R.string.settings_behaviour))
                McCard(Modifier.fillMaxWidth()) {
                    Column {
                        SwitchRow(stringResource(R.string.settings_animations), stringResource(R.string.settings_animations_text), settings.animations) { v ->
                            onChange { it.copy(animations = v) }
                        }
                        SwitchRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_text), settings.haptics) { v ->
                            // Switching it on gives a short sample, so you feel what it is.
                            if (v) Haptics(view, enabled = true).success()
                            onChange { it.copy(haptics = v) }
                        }
                    }
                }

                SectionTitle(stringResource(R.string.settings_about))
                McCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppLogo(48.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.settings_version, versionName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LinkRow(stringResource(R.string.settings_show_guide), Icons.AutoMirrored.Outlined.KeyboardArrowRight, onShowGuide)
                        LinkRow(stringResource(R.string.settings_get_plugin)) { uri.openUri(PLUGIN_DOWNLOAD_URL) }
                        LinkRow(stringResource(R.string.settings_source)) { uri.openUri(SOURCE_URL) }
                        LinkRow(stringResource(R.string.settings_licenses)) { licenses = true }
                        Text(
                            stringResource(R.string.settings_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        )
                        Text(
                            stringResource(R.string.settings_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Text(
                    "RemoteMC – MIT\n\n" +
                        "AndroidX, Jetpack Compose, CameraX – Apache 2.0\n" +
                        "Kotlin, kotlinx.coroutines – Apache 2.0\n" +
                        "OkHttp – Apache 2.0\n" +
                        "Coil – Apache 2.0\n" +
                        "ML Kit Barcode Scanning – ML Kit Terms\n" +
                        "Player heads – textures.minecraft.net, minotar.net",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, onValueChange = onChange, role = Role.Switch).padding(16.dp),
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
private fun LinkRow(label: String, icon: ImageVector = Icons.AutoMirrored.Outlined.OpenInNew, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val PresetNames = mapOf(
    null to R.string.color_green, 172f to R.string.color_teal, 212f to R.string.color_blue,
    240f to R.string.color_indigo, 275f to R.string.color_purple, 330f to R.string.color_pink,
    2f to R.string.color_red, 24f to R.string.color_orange, 45f to R.string.color_gold,
)

/** Colour swatches for the theme, plus a rainbow swatch for any hue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(current: Float?, onPick: (Float?) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val isCustom = current != null && current !in AccentPresets
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentPresets.forEach { hue ->
                Swatch(
                    SolidColor(accentSwatch(hue)),
                    stringResource(PresetNames.getValue(hue)),
                    selected = current == hue,
                ) { onPick(hue) }
            }
            Swatch(
                Brush.sweepGradient((0..6).map { Color.hsl(it * 60f % 360f, 0.65f, 0.52f) }),
                stringResource(R.string.settings_accent_custom),
                selected = isCustom,
            ) { custom = true }
        }
    }
    if (custom) {
        var hue by remember { mutableFloatStateOf(current ?: 145f) }
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text(stringResource(R.string.settings_accent_custom)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(accentSwatch(hue)))
                    Spacer(Modifier.height(16.dp))
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(
                                Brush.horizontalGradient((0..12).map { Color.hsl(it * 30f % 360f, 0.65f, 0.52f) }),
                            ),
                        )
                        Slider(
                            value = hue,
                            onValueChange = { hue = it },
                            valueRange = 0f..359f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentSwatch(hue),
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                            ),
                            
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onPick(hue); custom = false }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Swatch(fill: Brush, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, null, tint = Color.White)
    }
}

@Composable
private fun AlertSettingsCard(alerts: AlertSettings, onChange: ((AlertSettings) -> AlertSettings) -> Unit) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        blocked = !granted
        if (granted) onChange { it.copy(enabled = true) }
    }
    Column(Modifier.padding(vertical = 8.dp)) {
        SwitchRow(
            stringResource(R.string.settings_notify_enable),
            stringResource(R.string.settings_notify_enable_text),
            alerts.enabled,
        ) { on ->
            if (on && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onChange { it.copy(enabled = on) }
            }
        }
        if (alerts.enabled && blocked) {
            Text(
                stringResource(R.string.settings_notify_permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        AnimatedVisibility(
            alerts.enabled,
            enter = if (LocalAnimations.current) expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (LocalAnimations.current) shrinkVertically() + fadeOut() else ExitTransition.None,
        ) {
            Column {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    stringResource(R.string.settings_notify_offline),
                    stringResource(R.string.settings_notify_offline_text),
                    alerts.offline,
                ) { v -> onChange { it.copy(offline = v) } }
                SwitchRow(
                    stringResource(R.string.settings_notify_tps),
                    stringResource(R.string.settings_notify_tps_text, alerts.tpsThreshold),
                    alerts.lowTps,
                ) { v -> onChange { it.copy(lowTps = v) } }
                if (alerts.lowTps) {
                    Slider(
                        value = alerts.tpsThreshold.toFloat(),
                        onValueChange = { v -> onChange { it.copy(tpsThreshold = v.roundToInt()) } },
                        valueRange = 5f..19f,
                        steps = 13,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                SwitchRow(
                    stringResource(R.string.settings_notify_joins),
                    stringResource(R.string.settings_notify_players_hint),
                    alerts.joins,
                ) { v -> onChange { it.copy(joins = v) } }
                if (alerts.joins) {
                    OutlinedTextField(
                        value = alerts.players,
                        onValueChange = { v -> onChange { it.copy(players = v.take(300)) } },
                        label = { Text(stringResource(R.string.settings_notify_players)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                SwitchRow(
                    stringResource(R.string.settings_notify_mentions),
                    stringResource(R.string.settings_notify_words_hint),
                    alerts.mentions,
                ) { v -> onChange { it.copy(mentions = v) } }
                if (alerts.mentions) {
                    OutlinedTextField(
                        value = alerts.words,
                        onValueChange = { v -> onChange { it.copy(words = v.take(300)) } },
                        label = { Text(stringResource(R.string.settings_notify_words)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
                val context = LocalContext.current
                val from = clockText(context, alerts.quietFrom)
                val to = clockText(context, alerts.quietTo)
                SwitchRow(
                    stringResource(R.string.settings_notify_quiet),
                    stringResource(R.string.settings_notify_quiet_text, from, to),
                    alerts.quiet,
                ) { v -> onChange { it.copy(quiet = v) } }
                if (alerts.quiet) {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(true, false).forEach { start ->
                            OutlinedButton(onClick = {
                                val current = if (start) alerts.quietFrom else alerts.quietTo
                                android.app.TimePickerDialog(
                                    context,
                                    { _, h, m -> onChange { a -> if (start) a.copy(quietFrom = h * 60 + m) else a.copy(quietTo = h * 60 + m) } },
                                    current / 60, current % 60,
                                    android.text.format.DateFormat.is24HourFormat(context),
                                ).show()
                            }) {
                                Text(stringResource(if (start) R.string.settings_notify_quiet_from else R.string.settings_notify_quiet_to) + "  " + (if (start) from else to))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "22:00" or "10:00 PM", as the phone shows times. */
private fun clockText(context: android.content.Context, minuteOfDay: Int): String {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(java.util.Calendar.MINUTE, minuteOfDay % 60)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}
