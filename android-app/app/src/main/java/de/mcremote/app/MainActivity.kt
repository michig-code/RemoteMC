package de.mcremote.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mcremote.app.data.ServerProfile
import de.mcremote.app.data.ServerStore
import de.mcremote.app.ui.ServerListScreen
import de.mcremote.app.ui.ServerScreen
import de.mcremote.app.ui.ServerViewModel
import de.mcremote.app.ui.theme.McRemoteTheme

class MainActivity : ComponentActivity() {

    /** Profile parsed from a mcremote:// link that should be offered for saving. */
    private var pendingLink by mutableStateOf<ServerProfile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val store = ServerStore(this)
        setContent {
            McRemoteTheme {
                App(store, pendingLink, onLinkConsumed = { pendingLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.dataString?.let { ServerProfile.fromLink(it) }?.let { pendingLink = it }
    }
}

@Composable
private fun App(store: ServerStore, pendingLink: ServerProfile?, onLinkConsumed: () -> Unit) {
    var servers by remember { mutableStateOf(store.load()) }
    var selected by remember { mutableStateOf<ServerProfile?>(null) }

    fun update(list: List<ServerProfile>) {
        servers = list
        store.save(list)
    }

    // Slide between the server list and a server, like a navigation push/pop.
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally(tween(320)) { if (forward) it else -it / 3 } + fadeIn(tween(320)))
                .togetherWith(slideOutHorizontally(tween(320)) { if (forward) -it / 3 else it } + fadeOut(tween(240)))
        },
        contentKey = { it?.id },
        label = "screen",
    ) { current ->
        if (current == null) {
            ServerListScreen(
                servers = servers,
                pendingLink = pendingLink,
                onLinkConsumed = onLinkConsumed,
                onOpen = { selected = it },
                onSave = { profile ->
                    val existing = servers.indexOfFirst { it.id == profile.id }
                    update(if (existing >= 0) servers.toMutableList().also { it[existing] = profile } else servers + profile)
                },
                onDelete = { profile -> update(servers.filterNot { it.id == profile.id }) },
            )
        } else {
            BackHandler { selected = null }
            val vm: ServerViewModel = viewModel(
                key = current.id + current.hashCode(),
                factory = viewModelFactory { initializer { ServerViewModel(current) } },
            )
            ServerScreen(vm, onBack = { selected = null })
        }
    }
}
