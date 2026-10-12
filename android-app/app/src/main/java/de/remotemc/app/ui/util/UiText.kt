package de.remotemc.app.ui.util

import android.content.Context
import androidx.annotation.StringRes

/** Text for the UI that is resolved with the current locale only when shown. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Error(val error: Throwable) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.toTypedArray())
        is Error -> context.errorText(error)
    }
}
