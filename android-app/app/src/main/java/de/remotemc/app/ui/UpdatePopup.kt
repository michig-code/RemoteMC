package de.remotemc.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.ui.components.LocalAnimations
import de.remotemc.app.update.AppUpdates.State

/**
 * Pops up at the bottom of the screen when a new version of the app is available
 * (GitHub version only): "Later" hides it until the next start, "Update now" downloads and installs.
 */
@Composable
fun UpdatePopup(
    state: State,
    dismissed: Boolean,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    /** Android still has to allow RemoteMC to install apps. */
    needsPermission: Boolean = false,
) {
    val release = when (state) {
        is State.Available -> state.release
        is State.Downloading -> state.release
        is State.Ready -> state.release
        is State.Failed -> state.release
        else -> null
    }
    val animate = LocalAnimations.current
    AnimatedVisibility(
        visible = release != null && !dismissed,
        enter = if (animate) slideInVertically { it } + fadeIn() else EnterTransition.None,
        exit = if (animate) slideOutVertically { it } + fadeOut() else ExitTransition.None,
        modifier = modifier,
    ) {
        if (release == null) return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 8.dp,
            modifier = Modifier.navigationBarsPadding().padding(12.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.update_title, release.version), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (state) {
                                is State.Downloading -> stringResource(R.string.update_downloading)
                                is State.Failed -> stringResource(R.string.update_failed, state.message)
                                is State.Ready -> stringResource(if (needsPermission) R.string.update_permission else R.string.update_keeps)
                                else -> stringResource(R.string.update_keeps)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state is State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state is State.Downloading) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onUpdate) {
                            Text(stringResource(if (state is State.Ready) R.string.update_ready else R.string.update_now))
                        }
                    }
                }
            }
        }
    }
}
