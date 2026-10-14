package de.remotemc.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private class WelcomePage(val icon: ImageVector?, val title: StringResource, val text: StringResource, val command: String? = null)

private val welcomePages = listOf(
    WelcomePage(null, Res.string.onboarding_welcome_title, Res.string.onboarding_welcome_text),
    WelcomePage(Icons.Outlined.Extension, Res.string.onboarding_plugin_title, Res.string.onboarding_plugin_text),
    WelcomePage(Icons.Outlined.Terminal, Res.string.onboarding_pair_title, Res.string.onboarding_pair_text, "/remotemc pair"),
)

/**
 * Welcome pages on the first start, like on the phone: what RemoteMC does, the plugin and the
 * pairing command. "Get started" opens "Add server"; the PC's name is taken from Windows.
 */
@Composable
fun OnboardingPane(onFinish: (addServer: Boolean) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current
    val last = page == welcomePages.lastIndex
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onFinish(false) }) { Text(stringResource(Res.string.action_skip)) }
            }
            AnimatedContent(page, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "welcome") { i ->
                val p = welcomePages[i]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(Modifier.widthIn(max = 560.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (p.icon == null) {
                            AppLogo(128.dp)
                        } else {
                            Box(Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(p.icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        Text(stringResource(p.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(p.text), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        p.command?.let {
                            Spacer(Modifier.height(20.dp))
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                                Text(it, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                welcomePages.indices.forEach { i ->
                    val width by animateDpAsState(if (i == page) 24.dp else 8.dp, motion(), label = "dot")
                    Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(
                        if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ))
                }
            }
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page == 1) {
                    OutlinedButton(onClick = { uri.openUri(PLUGIN_DOWNLOAD_URL) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(Res.string.onboarding_download_plugin))
                    }
                }
                Button(onClick = { if (last) onFinish(true) else page++ }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(if (last) Res.string.onboarding_start else Res.string.action_next))
                }
            }
        }
    }
}
