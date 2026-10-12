package de.remotemc.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.ui.ChatTab
import de.remotemc.app.ui.ConsoleEntry
import de.remotemc.app.ui.ConsoleTab
import de.remotemc.app.ui.theme.RemoteMCTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** When the keyboard opens (less height), the newest console/chat lines must stay visible. */
@RunWith(RobolectricTestRunner::class)
class KeyboardScrollTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun consoleKeepsNewestLineVisible() {
        val lines = mutableStateListOf<ConsoleEntry>().apply {
            repeat(80) { add(ConsoleEntry("k$it", "line $it", "INFO", it.toLong())) }
        }
        var height by mutableStateOf(800.dp)
        rule.setContent {
            RemoteMCTheme {
                Box(Modifier.fillMaxWidth().height(height)) { ConsoleTab(lines, canRunCommands = true, onCommand = {}) }
            }
        }
        rule.onNodeWithText("line 79").assertIsDisplayed()

        height = 320.dp // keyboard opened
        rule.waitForIdle()
        rule.onNodeWithText("line 79").assertIsDisplayed()

        lines.add(ConsoleEntry("k80", "line 80", "INFO", 80))
        rule.waitForIdle()
        rule.onNodeWithText("line 80").assertIsDisplayed()
    }

    @Test
    fun chatKeepsNewestMessageVisible() {
        val chat = mutableStateListOf<ChatEntry>().apply {
            repeat(40) { add(ChatEntry(it.toLong(), it.toLong(), ChatEntry.Type.CHAT, "Steve", "message $it")) }
        }
        var height by mutableStateOf(800.dp)
        rule.setContent {
            RemoteMCTheme {
                Box(Modifier.fillMaxWidth().height(height)) { ChatTab(chat, canSend = true, onBroadcast = {}) }
            }
        }
        rule.onNodeWithText("message 39").assertIsDisplayed()
        height = 320.dp
        rule.waitForIdle()
        rule.onNodeWithText("message 39").assertIsDisplayed()
    }
}
