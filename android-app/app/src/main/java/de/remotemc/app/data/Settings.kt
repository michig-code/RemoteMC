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
    val onboardingDone: Boolean = false,
    /** Name of this phone, used for every pairing (shown on the server in /remotemc token list). */
    val deviceName: String = "",
    /** Hue (0–360) of the app's colour theme; null = the classic RemoteMC green. */
    val accentHue: Float? = null,
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
            putBoolean("onboardingDone", next.onboardingDone)
            putString("deviceName", next.deviceName)
            putFloat("accentHue", next.accentHue ?: -1f)
        }
    }

    private fun read() = AppSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamicColor", false),
        haptics = prefs.getBoolean("haptics", true),
        onboardingDone = prefs.getBoolean("onboardingDone", false),
        deviceName = prefs.getString("deviceName", "").orEmpty(),
        accentHue = prefs.getFloat("accentHue", -1f).takeIf { it >= 0f },
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
