package de.remotemc.desktop

import androidx.compose.runtime.Composable

/** The PC has no vibration: the same calls as on the phone, doing nothing (keeps ported screens alike). */
object Haptics {
    fun tap() = Unit
    fun tick() = Unit
    fun heavy() = Unit
    fun success() = Unit
    fun error() = Unit
}

@Composable
fun rememberHaptics() = Haptics
