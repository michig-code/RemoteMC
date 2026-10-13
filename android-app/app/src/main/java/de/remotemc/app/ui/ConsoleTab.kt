package de.remotemc.app.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.ui.components.LocalAnimations
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.theme.MonoStyle
import de.remotemc.app.ui.util.MinecraftText
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
private val TerminalText = Color(0xFFD5DDD8)
private val TerminalDim = Color(0xFF56635A)
private val TerminalAccent = Color(0xFF7CC4FF)

@Composable
fun ConsoleTab(
    entries: List<ConsoleEntry>,
    canRunCommands: Boolean,
    history: List<String> = emptyList(),
    suggestions: List<Suggestion> = emptyList(),
    usageHint: String? = null,
    onInput: (String) -> Unit = {},
    onCommand: (String) -> Unit,
    onForget: (String) -> Unit = {},
    initialInput: String = "",
) {
    var input by remember { mutableStateOf(TextFieldValue(initialInput, TextRange(initialInput.length))) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val animate = LocalAnimations.current

    // The list is laid out from the bottom (reverseLayout): when the keyboard opens and the
    // terminal gets smaller, the newest lines stay visible – like in a messenger.
    val reversed = entries.asReversed()
    val atBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48 }
    }
    // Follow new output unless the user scrolled up to read older lines.
    var follow by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to atBottom }
            .collect { (scrolling, bottom) -> if (scrolling) follow = bottom }
    }
    LaunchedEffect(entries.size) {
        if (follow && entries.isNotEmpty()) listState.scrollToItem(0)
    }

    fun setInput(text: String) {
        input = TextFieldValue(text, TextRange(text.length))
        onInput(text)
    }

    fun send() {
        if (input.text.isBlank()) return
        haptics.tap()
        onCommand(input.text)
        setInput("")
    }

    /** Replaces the word being typed with [suggestion], like pressing Tab in Minecraft. */
    fun apply(suggestion: String) {
        haptics.tap()
        val text = input.text
        val cut = text.lastIndexOf(' ')
        val prefix = if (cut < 0) (if (text.startsWith("/")) "/" else "") else text.substring(0, cut + 1)
        setInput("$prefix$suggestion ")
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                .clip(MaterialTheme.shapes.large)
                .background(McColors.Terminal),
        ) {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.console_waiting),
                    style = MonoStyle,
                    color = TerminalDim,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Long-press a line to select and copy text; the button copies the whole log.
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.Top, // short logs start at the top like before
                ) {
                    items(reversed, key = { it.key }) { line -> ConsoleLineView(line) }
                }
            }
            if (entries.isNotEmpty()) {
                val clipboard = LocalClipboardManager.current
                val context = LocalContext.current
                val copied = stringResource(R.string.console_copied)
                IconButton(
                    onClick = {
                        haptics.tap()
                        clipboard.setText(AnnotatedString(entries.joinToString("\n") { it.text }))
                        // Android 13+ shows its own confirmation for copied text.
                        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(McColors.Terminal.copy(alpha = 0.85f), CircleShape),
                ) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.console_copy), tint = TerminalDim) }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !atBottom && entries.isNotEmpty(),
                enter = if (animate) fadeIn() + scaleIn() else EnterTransition.None,
                exit = if (animate) fadeOut() + scaleOut() else ExitTransition.None,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        follow = true
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.console_scroll_down)) }
            }
            // Suggestions float over the bottom of the terminal, like Minecraft's chat completion.
            androidx.compose.animation.AnimatedVisibility(
                visible = canRunCommands && (suggestions.isNotEmpty() || usageHint != null) && input.text.isNotBlank(),
                enter = if (animate) expandVertically(expandFrom = Alignment.Bottom) + fadeIn() else EnterTransition.None,
                exit = if (animate) shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut() else ExitTransition.None,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                SuggestionPanel(input.text, suggestions, usageHint, ::apply)
            }
        }

        if (canRunCommands) {
            AnimatedVisibility(
                input.text.isEmpty() && history.isNotEmpty(),
                enter = if (animate) expandVertically() + fadeIn() else EnterTransition.None,
                exit = if (animate) shrinkVertically() + fadeOut() else ExitTransition.None,
            ) {
                RecentCommands(history, onPick = { setInput(it) }, onForget = { haptics.heavy(); onForget(it) })
            }
            CommandInput(
                value = input,
                onValueChange = {
                    val changed = it.text != input.text
                    input = it
                    if (changed) onInput(it.text)
                },
                placeholder = stringResource(R.string.console_input_hint),
                prefix = "/",
                onSend = ::send,
            )
        } else {
            Text(
                stringResource(R.string.console_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionPanel(input: String, suggestions: List<Suggestion>, usageHint: String?, onPick: (String) -> Unit) {
    val typed = input.substringAfterLast(' ').removePrefix("/")
    Surface(
        color = Color(0xF0161D19),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (usageHint != null) {
                Text(
                    usageHint,
                    style = MonoStyle,
                    color = TerminalDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                if (suggestions.isNotEmpty()) HorizontalDivider(color = Color(0x22FFFFFF))
            }
            LazyColumn(Modifier.heightIn(max = 220.dp)) {
                items(suggestions, key = { it.text }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onPick(s.text) })
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            buildAnnotatedString {
                                // The part already typed is dimmed, the completion stands out (like Minecraft).
                                val match = s.text.startsWith(typed, ignoreCase = true) && typed.isNotEmpty()
                                if (match) {
                                    withStyle(SpanStyle(color = TerminalText)) { append(s.text.take(typed.length)) }
                                    withStyle(SpanStyle(color = Color(0xFFFFF06B), fontWeight = FontWeight.Bold)) {
                                        append(s.text.drop(typed.length))
                                    }
                                } else {
                                    withStyle(SpanStyle(color = Color(0xFFFFF06B))) { append(s.text) }
                                }
                            },
                            style = MonoStyle,
                        )
                        if (s.description != null) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                s.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TerminalDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCommands(history: List<String>, onPick: (String) -> Unit, onForget: (String) -> Unit) {
    val forgetLabel = stringResource(R.string.console_recent_forget)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "icon") {
            Icon(
                Icons.Outlined.History,
                stringResource(R.string.console_recent),
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(history, key = { it }) { cmd ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier
                    .then(if (LocalAnimations.current) Modifier.animateItem() else Modifier)
                    .clip(CircleShape)
                    .combinedClickable(onClick = { onPick(cmd) }, onLongClick = { onForget(cmd) })
                    .semantics { onLongClick(label = forgetLabel) { onForget(cmd); true } },
            ) {
                Text(
                    "/$cmd",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).widthIn(max = 220.dp),
                )
            }
        }
    }
}

@Composable
private fun ConsoleLineView(line: ConsoleEntry) {
    val base = when (line.level) {
        "WARN" -> Color(0xFFFFC857)
        "ERROR", "FATAL" -> Color(0xFFFF6B6B)
        "COMMAND" -> TerminalAccent
        "OUTPUT" -> Color(0xFF8BE9A8)
        else -> TerminalText
    }
    val text = remember(line.key, line.formatted) {
        if (line.formatted != null) MinecraftText.parse(line.formatted, base)
        else MinecraftText.highlightPlain(line.text, base, TerminalAccent)
    }
    // Timestamp in its own column so wrapped lines stay indented under the message.
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(timeFormat.format(Date(line.time)), style = MonoStyle, color = TerminalDim)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MonoStyle, modifier = Modifier.weight(1f))
    }
}

/** Rounded input bar with a send button, shared by console and chat. */
@Composable
fun CommandInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    prefix: String? = null,
) {
    CommandInput(
        value = TextFieldValue(value, TextRange(value.length)),
        onValueChange = { onValueChange(it.text) },
        placeholder = placeholder,
        onSend = onSend,
        prefix = prefix,
    )
}

@Composable
fun CommandInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    prefix: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            prefix = prefix?.let { { Text(it, color = MaterialTheme.colorScheme.primary) } },
            singleLine = true,
            textStyle = if (prefix != null) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                autoCorrectEnabled = prefix == null,
                capitalization = if (prefix == null) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        FilledIconButton(onClick = onSend, enabled = value.text.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send))
        }
    }
}
