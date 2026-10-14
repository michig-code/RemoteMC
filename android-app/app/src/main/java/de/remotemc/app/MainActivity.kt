package de.remotemc.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.remotemc.app.data.ConnectLink
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ThemeMode
import de.remotemc.app.data.suggestedDeviceName
import de.remotemc.app.notify.WatchService
import de.remotemc.app.net.DemoServer
import de.remotemc.app.ui.OnboardingScreen
import de.remotemc.app.ui.ServerListScreen
import de.remotemc.app.ui.ServerScreen
import de.remotemc.app.ui.ServerViewModel
import de.remotemc.app.ui.SettingsScreen
import de.remotemc.app.ui.components.LocalAnimations
import de.remotemc.app.ui.pair.PairScreen
import de.remotemc.app.ui.pair.PairViewModel
import de.remotemc.app.ui.pair.ScannerScreen
import de.remotemc.app.ui.theme.RemoteMCTheme
import de.remotemc.app.ui.util.LocalHapticsEnabled
import de.remotemc.app.widget.RemoteMCWidget
import de.remotemc.app.widget.StatusRefresher

class MainActivity : AppCompatActivity() {

    /** Pairing link or shortcut target received via an intent, consumed by the navigation. */
    private var pendingLink by mutableStateOf<ConnectLink?>(null)
    private var pendingServer by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by app.settings.settings.collectAsState()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            RemoteMCTheme(darkTheme = dark, dynamicColor = settings.dynamicColor, accentHue = settings.accentHue) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics, LocalAnimations provides settings.animations) {
                    AppNavigation(
                        startOnboarding = !settings.onboardingDone || settings.deviceName.isBlank(),
                        pendingLink = pendingLink,
                        pendingServer = pendingServer,
                        onIntentConsumed = { pendingLink = null; pendingServer = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.dataString?.let(ConnectLink::parse)?.let { pendingLink = it }
        intent?.getStringExtra(EXTRA_SERVER_ID)?.let { pendingServer = it }
    }

    companion object {
        const val EXTRA_SERVER_ID = "server_id"
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val GUIDE = "guide"
    const val SERVERS = "servers"
    const val PAIR = "pair?repair={repair}"
    const val SCAN = "scan"
    const val SERVER = "server/{id}"
    const val SETTINGS = "settings"

    fun pair(repair: String? = null) = if (repair == null) "pair" else "pair?repair=$repair"
    fun server(id: String) = "server/$id"
}

@Composable
private fun AppNavigation(
    startOnboarding: Boolean,
    pendingLink: ConnectLink?,
    pendingServer: String?,
    onIntentConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val repo = context.app.servers
    val servers by repo.servers.collectAsState()
    val settings by context.app.settings.settings.collectAsState()

    // Background notifications: start the service while the app is open; it stops itself when not needed.
    LaunchedEffect(settings.alerts.enabled, servers.map { it.id to it.notify }) {
        WatchService.sync(context)
    }
    // Keep the home screen widget in step with added, renamed or removed servers.
    LaunchedEffect(servers.map { it.id to it.name }) {
        if (GlanceAppWidgetManager(context).getGlanceIds(RemoteMCWidget::class.java).isNotEmpty()) {
            StatusRefresher.refreshNow(context)
        }
    }

    LaunchedEffect(pendingLink, pendingServer) {
        when {
            pendingLink != null -> {
                nav.navigate(Routes.pair())
                nav.currentBackStackEntry?.savedStateHandle?.set(LINK_KEY, pendingLink.raw)
                onIntentConsumed()
            }
            pendingServer != null && repo.get(pendingServer) != null -> {
                nav.navigate(Routes.server(pendingServer)) { launchSingleTop = true }
                onIntentConsumed()
            }
        }
    }

    val animate = LocalAnimations.current
    NavHost(
        navController = nav,
        startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.SERVERS,
        enterTransition = { if (animate) slideInHorizontally { it } + fadeIn() else EnterTransition.None },
        exitTransition = { if (animate) slideOutHorizontally { -it / 3 } + fadeOut() else ExitTransition.None },
        popEnterTransition = { if (animate) slideInHorizontally { -it / 3 } + fadeIn() else EnterTransition.None },
        popExitTransition = { if (animate) slideOutHorizontally { it } + fadeOut() else ExitTransition.None },
    ) {
        composable(Routes.ONBOARDING) {
            val current = context.app.settings.settings.collectAsState().value
            OnboardingScreen(
                initialName = current.deviceName.ifBlank { suggestedDeviceName() },
                // Users of an older version already saw the welcome pages and only need a name.
                onlyName = current.onboardingDone,
                onFinish = { name ->
                    context.app.settings.update { it.copy(onboardingDone = true, deviceName = name) }
                    nav.navigate(Routes.SERVERS) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        // The start guide again, from the settings (e.g. to show it to someone).
        composable(Routes.GUIDE) {
            val current = context.app.settings.settings.collectAsState().value
            OnboardingScreen(
                initialName = current.deviceName.ifBlank { suggestedDeviceName() },
                onFinish = { name ->
                    if (name.isNotBlank()) context.app.settings.update { it.copy(deviceName = name) }
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.SERVERS) {
            ServerListScreen(
                servers = servers,
                onOpen = { nav.navigate(Routes.server(it.id)) },
                onAdd = { nav.navigate(Routes.pair()) },
                onRename = { profile, name -> repo.upsert(profile.copy(name = name)) },
                onRepair = { nav.navigate(Routes.pair(it.id)) },
                onDelete = {
                    repo.delete(it.id)
                    context.app.snapshots.delete(it.id)
                    context.app.shortcuts.delete(it.id)
                    context.app.notes.deleteServer(it.id)
                    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(it.id))
                },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onPin = { profile, pinned -> repo.setPinned(profile.id, pinned) },
                onMoveToFolder = { profile, folder -> repo.setFolder(profile.id, folder) },
                onRenameFolder = repo::renameFolder,
                onRemoveFolder = repo::removeFolder,
                onReorder = repo::setOrder,
                onToggleNotify = { repo.upsert(it.copy(notify = !it.notify)) },
                alertsOn = settings.alerts.enabled,
                onDemo = {
                    repo.upsert(DemoServer.profile(context.getString(R.string.demo_name)))
                    nav.navigate(Routes.server(DemoServer.ID))
                },
            )
        }
        composable(
            Routes.PAIR,
            arguments = listOf(navArgument("repair") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            val vm: PairViewModel = viewModel()
            val repairId = entry.arguments?.getString("repair")
            LaunchedEffect(repairId) { repairId?.let(repo::get)?.let(vm::startRepair) }
            // Links arrive as text (saved state only holds simple types) from the scanner or an intent.
            val link = entry.savedStateHandle.getStateFlow<String?>(LINK_KEY, null).collectAsState().value
            LaunchedEffect(link) {
                if (link != null) {
                    entry.savedStateHandle[LINK_KEY] = null
                    ConnectLink.parse(link)?.let(vm::useLink)
                }
            }
            PairScreen(
                vm = vm,
                onScan = { nav.navigate(Routes.SCAN) },
                onClose = { nav.popBackStack() },
                onPaired = { profile ->
                    nav.navigate(Routes.server(profile.id)) { popUpTo(Routes.SERVERS) }
                },
            )
        }
        composable(Routes.SCAN) {
            ScannerScreen(
                onResult = { link ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(LINK_KEY, link.raw)
                    nav.popBackStack()
                },
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.SERVER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val profile = repo.get(id)
            if (profile == null) {
                LaunchedEffect(Unit) { nav.popBackStack(Routes.SERVERS, inclusive = false) }
                return@composable
            }
            LaunchedEffect(id) { publishShortcut(context, profile) }
            val vm: ServerViewModel = viewModel(
                factory = viewModelFactory { initializer { ServerViewModel(context.app, profile) } },
            )
            ServerScreen(vm, onBack = { nav.popBackStack() }, onRepair = { nav.navigate(Routes.pair(id)) })
        }
        composable(Routes.SETTINGS) {
            val settings by context.app.settings.settings.collectAsState()
            SettingsScreen(
                settings, onChange = context.app.settings::update, onBack = { nav.popBackStack() },
                onShowGuide = { nav.navigate(Routes.GUIDE) },
            )
        }
    }
}

private const val LINK_KEY = "link"

/** Long-press shortcut on the launcher icon for recently opened servers. */
private fun publishShortcut(context: android.content.Context, profile: ServerProfile) {
    val intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(MainActivity.EXTRA_SERVER_ID, profile.id)
    val shortcut = ShortcutInfoCompat.Builder(context, profile.id)
        .setShortLabel(profile.name.take(24))
        .setLongLabel(profile.name)
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()
    runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
}

