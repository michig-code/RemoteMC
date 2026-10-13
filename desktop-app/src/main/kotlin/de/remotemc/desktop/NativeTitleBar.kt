package de.remotemc.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import java.awt.Frame

/**
 * On the JetBrains Runtime (bundled with the Windows installer) the window keeps its real Windows
 * frame: Windows itself drags, snaps, maximizes and minimizes it with its own animations and
 * shows its own minimize / maximize / close buttons, while RemoteMC draws the tabs into the title
 * bar. Elsewhere (e.g. the portable JAR on another Java) the app falls back to [AwtWindowControls].
 */
object NativeTitleBar {
    val supported: Boolean by lazy {
        AppDirs.isWindows && runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false)
    }

    fun install(window: Frame, height: Dp, dark: Boolean): WindowDecorations.CustomTitleBar {
        val decorations = JBR.getWindowDecorations()
        val titleBar = decorations.createCustomTitleBar()
        titleBar.height = height.value
        titleBar.putProperty("controls.dark", dark)
        decorations.setCustomTitleBar(window, titleBar)
        return titleBar
    }
}

class NativeWindowControls(
    private val window: Frame,
    private val titleBar: WindowDecorations.CustomTitleBar,
    private val onClose: () -> Unit,
    private val quit: () -> Unit,
) : WindowControls {
    override var maximized by mutableStateOf(window.extendedState and Frame.MAXIMIZED_BOTH != 0)
        private set

    init {
        window.addWindowStateListener { maximized = it.newState and Frame.MAXIMIZED_BOTH != 0 }
    }

    override val native = true

    /** Room on the right for Windows' own buttons. */
    override val rightInset: Dp get() = titleBar.rightInset.dp

    override fun hitTest(client: Boolean) = titleBar.forceHitTest(client)

    fun setDark(dark: Boolean) = titleBar.putProperty("controls.dark", dark)

    // Windows' own buttons and the caption do these natively; kept for shortcuts and menus.
    override fun minimize() {
        window.extendedState = window.extendedState or Frame.ICONIFIED
    }

    override fun toggleMaximize() {
        window.extendedState = if (maximized) Frame.NORMAL else window.extendedState or Frame.MAXIMIZED_BOTH
    }

    override fun close() = onClose()

    override fun quit() = quit.invoke()

    @Composable
    override fun DragArea(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Box(modifier, content = content)
    }
}
