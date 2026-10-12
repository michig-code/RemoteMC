package de.remotemc.app.ui

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import de.remotemc.app.BuildConfig
import de.remotemc.app.R
import de.remotemc.app.data.AppSettings
import de.remotemc.app.data.ThemeMode
import de.remotemc.app.ui.components.AppLogo
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.SectionTitle

const val SOURCE_URL = "https://github.com/michig-code/main"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    var licenses by remember { mutableStateOf(false) }
    val currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

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
                    }
                }

                SectionTitle(stringResource(R.string.settings_language))
                McCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        listOf("" to R.string.settings_language_system, "en" to R.string.settings_language_en, "de" to R.string.settings_language_de)
                            .forEach { (tag, label) ->
                                RadioRow(stringResource(label), currentLanguage == tag) {
                                    AppCompatDelegate.setApplicationLocales(
                                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                                    )
                                }
                            }
                    }
                }

                SectionTitle(stringResource(R.string.settings_behaviour))
                McCard(Modifier.fillMaxWidth()) {
                    SwitchRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_text), settings.haptics) { v ->
                        onChange { it.copy(haptics = v) }
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
                                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LinkRow(stringResource(R.string.settings_get_plugin)) { uri.openUri(PLUGIN_DOWNLOAD_URL) }
                        LinkRow(stringResource(R.string.settings_source)) { uri.openUri(SOURCE_URL) }
                        LinkRow(stringResource(R.string.settings_licenses)) { licenses = true }
                        Text(
                            stringResource(R.string.settings_privacy),
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
                    "AndroidX, Jetpack Compose, CameraX – Apache 2.0\n" +
                        "Kotlin, kotlinx.coroutines – Apache 2.0\n" +
                        "OkHttp – Apache 2.0\n" +
                        "Coil – Apache 2.0\n" +
                        "ML Kit Barcode Scanning – ML Kit Terms\n" +
                        "Player heads – mc-heads.net",
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
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
