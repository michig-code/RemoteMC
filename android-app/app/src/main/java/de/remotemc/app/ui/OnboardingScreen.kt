package de.remotemc.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.remotemc.app.data.MAX_DEVICE_NAME
import de.remotemc.app.ui.components.AppLogo
import kotlinx.coroutines.launch

const val PLUGIN_DOWNLOAD_URL = "https://github.com/michig-code/RemoteMC/releases/latest"

private data class Page(val icon: ImageVector?, val title: Int, val text: Int, val command: String? = null)

private val pages = listOf(
    Page(null, R.string.onboarding_welcome_title, R.string.onboarding_welcome_text),
    Page(Icons.Outlined.Extension, R.string.onboarding_plugin_title, R.string.onboarding_plugin_text),
    Page(Icons.Outlined.Terminal, R.string.onboarding_pair_title, R.string.onboarding_pair_text, "/remotemc pair"),
    Page(Icons.Outlined.QrCode2, R.string.onboarding_scan_title, R.string.onboarding_scan_text),
)

/**
 * Welcome pages, ending with the phone's name (asked once; it is used for every pairing).
 * With [onlyName] just the name page is shown, e.g. for users who set up an older version.
 */
@Composable
fun OnboardingScreen(initialName: String, onFinish: (deviceName: String) -> Unit, onlyName: Boolean = false) {
    val infoPages = if (onlyName) emptyList() else pages
    val pageCount = infoPages.size + 1
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val last = pager.currentPage == pageCount - 1
    var name by rememberSaveable { mutableStateOf(initialName) }

    // Surface sets the text colour for the background; without it, plain texts default to black.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                // "Skip" jumps to the name page; the name is still needed.
                if (!last) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pageCount - 1) } }) {
                        Text(stringResource(R.string.action_skip))
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                if (index < infoPages.size) {
                    PageContent(infoPages[index], index == pager.currentPage)
                } else {
                    NamePage(name) { if (it.length <= MAX_DEVICE_NAME) name = it }
                }
            }
            if (pageCount > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                    repeat(pageCount) { i ->
                        val width by animateDpAsState(if (i == pager.currentPage) 24.dp else 8.dp, label = "dot")
                        Box(
                            Modifier.height(8.dp).width(width).clip(CircleShape).background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                    }
                }
            }
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (!onlyName && pager.currentPage == 1) {
                    OutlinedButton(onClick = { uri.openUri(PLUGIN_DOWNLOAD_URL) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(R.string.onboarding_download_plugin))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Button(
                    onClick = {
                        if (last) onFinish(name.trim()) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    enabled = !last || name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(if (last) R.string.onboarding_start else R.string.action_next))
                }
            }
        }
    }
}

@Composable
private fun NamePage(name: String, onName: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Smartphone, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.onboarding_name_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_name_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.pair_device_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun PageContent(page: Page, visible: Boolean) {
    val scale = remember { Animatable(0.7f) }
    LaunchedEffect(visible) {
        if (visible) {
            scale.snapTo(0.7f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            if (page.icon == null) {
                AppLogo(128.dp)
            } else {
                Box(
                    Modifier.size(128.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(page.icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        page.command?.let {
            Spacer(Modifier.height(20.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                Text(
                    it,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
