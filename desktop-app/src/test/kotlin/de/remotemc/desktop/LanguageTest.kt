package de.remotemc.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test

/** Picking a language in the settings shows the app in it right away. */
@OptIn(ExperimentalTestApi::class)
class LanguageTest {
    private val dir = Files.createTempDirectory("rmc").toFile()
    private val model = AppModel(ServerStore(File(dir, "servers.dat")), SettingsStore(File(dir, "settings.json")), CommandHistory(File(dir, "h.json")), ServerExtras.inMemory())

    @AfterTest
    fun reset() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun switchesToGermanAndBack() = runDesktopComposeUiTest(width = 900, height = 1600) {
        Locale.setDefault(Locale.ENGLISH)
        var language by mutableStateOf("en")
        setContent { key(language) { RemoteMCTheme(model.settings.settings.value) { HelpPane({}) } } }
        onNodeWithText("Help & guide").assertExists()
        AppLanguage.apply("de")
        language = "de"
        waitForIdle()
        onNodeWithText("Hilfe & Anleitung").assertExists()
    }
}
