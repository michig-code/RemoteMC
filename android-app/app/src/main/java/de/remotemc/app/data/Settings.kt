package de.remotemc.app.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val haptics: Boolean = true,
    /** Transitions, moving numbers and the like; off = everything changes instantly. */
    val animations: Boolean = true,
    val onboardingDone: Boolean = false,
    /** Name of this phone, used for every pairing (shown on the server in /remotemc token list). */
    val deviceName: String = "",
    /** Hue (0–360) of the app's colour theme; null = the classic RemoteMC green. */
    val accentHue: Float? = null,
    val alerts: AlertSettings = AlertSettings(),
    /** Order, width and visibility of the overview cards ([DashboardLayout.encode]); empty = default. */
    val dashboard: String = "",
)

class SettingsStore(private val prefs: SharedPreferences) {

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit {
            putString("theme", next.theme.name)
            putBoolean("dynamicColor", next.dynamicColor)
            putBoolean("haptics", next.haptics)
            putBoolean("animations", next.animations)
            putBoolean("onboardingDone", next.onboardingDone)
            putString("deviceName", next.deviceName)
            putFloat("accentHue", next.accentHue ?: -1f)
            putString("dashboard", next.dashboard)
            next.alerts.let {
                putBoolean("alerts.enabled", it.enabled)
                putBoolean("alerts.offline", it.offline)
                putBoolean("alerts.lowTps", it.lowTps)
                putInt("alerts.tpsThreshold", it.tpsThreshold)
                putBoolean("alerts.joins", it.joins)
                putString("alerts.players", it.players)
                putBoolean("alerts.mentions", it.mentions)
                putString("alerts.words", it.words)
            }
        }
    }

    private fun read() = AppSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamicColor", false),
        haptics = prefs.getBoolean("haptics", true),
        animations = prefs.getBoolean("animations", true),
        onboardingDone = prefs.getBoolean("onboardingDone", false),
        deviceName = prefs.getString("deviceName", "").orEmpty(),
        accentHue = prefs.getFloat("accentHue", -1f).takeIf { it >= 0f },
        dashboard = prefs.getString("dashboard", "").orEmpty(),
        alerts = AlertSettings(
            enabled = prefs.getBoolean("alerts.enabled", false),
            offline = prefs.getBoolean("alerts.offline", true),
            lowTps = prefs.getBoolean("alerts.lowTps", true),
            tpsThreshold = prefs.getInt("alerts.tpsThreshold", 15),
            joins = prefs.getBoolean("alerts.joins", false),
            players = prefs.getString("alerts.players", "").orEmpty(),
            mentions = prefs.getBoolean("alerts.mentions", true),
            words = prefs.getString("alerts.words", "").orEmpty(),
        ),
    )
}

/** Suggestion for the phone's name, e.g. "Samsung SM-S931B" or "Pixel 8". */
fun suggestedDeviceName(): String {
    val model = android.os.Build.MODEL.orEmpty()
    val maker = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
    return when {
        model.isBlank() -> "Android"
        model.startsWith(maker, ignoreCase = true) -> model
        else -> "$maker $model"
    }.take(MAX_DEVICE_NAME)
}

const val MAX_DEVICE_NAME = 40
