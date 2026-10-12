package de.remotemc.app.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Whether haptic feedback is enabled in the app settings. */
val LocalHapticsEnabled = compositionLocalOf { true }

class Haptics(private val view: View, private val enabled: Boolean) {
    fun tap() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun success() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun error() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)

    private fun perform(type: Int) {
        if (enabled) view.performHapticFeedback(type)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
