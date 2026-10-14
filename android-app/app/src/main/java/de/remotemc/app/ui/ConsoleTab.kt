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
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
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

/** Coloured console lines by key; a bit more than the console keeps (1500 lines). */
private val parsedLines = android.util.LruCache<String, AnnotatedString>(2000)
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
    /** Only saved and local suggestions: the server could not be asked. */
    suggestionsOffline: Boolean = false,
    /** The text before the cursor, whenever it changes (suggestions are for the word there). */
    onInput: (String) -> Unit = {},
    onCommand: (String) -> Unit,
    onForget: (String) -> Unit = {},
    initialInput: String = "",
    /** Book next to "send": all commands, favorites and macros (null = no button). */
    menu: CommandMenuActions? = null,
    initialSearch: String? = null,
    initialLevel: LevelFilter = LevelFilter.ALL,
) {
    var input by remember { mutableStateOf(TextFieldValue(initialInput, TextRange(initialInput.length))) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val animate = LocalAnimations.current

    // Search and level filter: the filter hides lines, the search highlights and jumps.
    var searchOpen by rememberSaveable { mutableStateOf(initialSearch != null || initialLevel != LevelFilter.ALL) }
    var query by rememberSaveable { mutableStateOf(initialSearch.orEmpty()) }
    var level by rememberSaveable { mutableStateOf(initialLevel) }
    val shown = remember(entries.size, entries.lastOrNull()?.key, level) { ConsoleSearch.filter(entries, level) }
    val matches = remember(shown, query) { ConsoleSearch.matches(shown, query) }
    // Which match is selected; the newest one at first.
    var current by remember(query, level) { mutableStateOf(-1) }
    val selected = if (matches.isEmpty()) -1 else if (current in matches.indices) current else matches.lastIndex
    val selectedKey = matches.getOrNull(selected)?.let { shown[it].key }

    // The list is laid out from the bottom (reverseLayout): when the keyboard opens and the
    // terminal gets smaller, the newest lines stay visible – like in a messenger.
    val reversed = shown.asReversed()
    val atBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48 }
    }
    // Follow new output unless the user scrolled up to read older lines.
    var follow by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to atBottom }
            .collect { (scrolling, bottom) -> if (scrolling) follow = bottom }
    }
    LaunchedEffect(shown.size) {
        if (follow && query.isBlank() && shown.isNotEmpty()) listState.scrollToItem(0)
    }
    // Jump to the selected match.
    LaunchedEffect(selected, matches.size, query) {
        val index = matches.getOrNull(selected) ?: return@LaunchedEffect
        follow = false
        listState.animateScrollToItem((shown.size - 1 - index).coerceAtLeast(0))
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

    /** Replaces the word at the cursor with [suggestion], like pressing Tab in Minecraft. */
    fun apply(suggestion: String) {
        haptics.tap()
        input = completeAt(input, suggestion)
        onInput(input.text.take(input.selection.end))
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            searchOpen,
            enter = if (animate) expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (animate) shrinkVertically() + fadeOut() else ExitTransition.None,
        ) {
            val context = LocalContext.current
            ConsoleSearchBar(
                query = query,
                onQuery = { query = it },
                level = level,
                onLevel = { haptics.tap(); level = it },
                position = if (matches.isEmpty()) null else selected + 1 to matches.size,
                onPrevious = { if (matches.isNotEmpty()) { haptics.tick(); current = (selected - 1 + matches.size) % matches.size } },
                onNext = { if (matches.isNotEmpty()) { haptics.tick(); current = (selected + 1) % matches.size } },
                onShare = {
                    val lines = if (query.isBlank()) shown else matches.map { shown[it] }
                    if (lines.isNotEmpty()) {
                        haptics.tap()
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, ConsoleSearch.shareText(lines))
                        context.startActivity(android.content.Intent.createChooser(send, null))
                    }
                },
                onClose = {
                    searchOpen = false
                    query = ""
                    level = LevelFilter.ALL
                    follow = true
                    scope.launch { listState.scrollToItem(0) }
                },
            )
        }
        // Labelled buttons above the log (they used to float over the text).
        if (!searchOpen && entries.isNotEmpty()) {
            val clipboard = LocalClipboardManager.current
            val context = LocalContext.current
            val copied = stringResource(R.string.console_copied)
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), horizontalArrangement = Arrangement.End) {
                // Screen readers hear the longer "Search console" / "Copy console".
                val searchLabel = stringResource(R.string.console_search)
                val copyLabel = stringResource(R.string.console_copy)
                TextButton(onClick = { haptics.tap(); searchOpen = true }, modifier = Modifier.semantics { contentDescription = searchLabel }) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_search))
                }
                TextButton(
                    onClick = {
                        haptics.tap()
                        // Copies what is shown (all lines, or only the filtered ones).
                        clipboard.setText(AnnotatedString(shown.joinToString("\n") { it.text }))
                        // Android 13+ shows its own confirmation for copied text.
                        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.semantics { contentDescription = copyLabel },
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy))
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = if (searchOpen || entries.isEmpty()) 8.dp else 0.dp)
                .clip(MaterialTheme.shapes.large)
                .background(McColors.Terminal),
        ) {
            if (shown.isEmpty()) {
                Text(
                    stringResource(if (entries.isEmpty()) R.string.console_waiting else R.string.console_no_lines),
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
                    items(reversed, key = { it.key }) { line ->
                        ConsoleLineView(line, highlight = query, current = line.key == selectedKey)
                    }
                }
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
                visible = canRunCommands && (suggestions.isNotEmpty() || usageHint != null || suggestionsOffline) && input.text.isNotBlank(),
                enter = if (animate) expandVertically(expandFrom = Alignment.Bottom) + fadeIn() else EnterTransition.None,
                exit = if (animate) shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut() else ExitTransition.None,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                SuggestionPanel(input.text.take(input.selection.end), suggestions, usageHint, suggestionsOffline, ::apply)
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
            menu?.runningMacro?.let { name ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.macro_running, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { haptics.heavy(); menu.onStopMacro() }) { Text(stringResource(R.string.macro_stop)) }
                }
            }
            CommandInput(
                value = input,
                onValueChange = {
                    // Moving the cursor to another word changes the suggestions too.
                    val changed = it.text != input.text || it.selection.end != input.selection.end
                    input = it
                    if (changed) onInput(it.text.take(it.selection.end))
                },
                placeholder = stringResource(R.string.console_input_hint),
                prefix = "/",
                onSend = ::send,
                extra = if (menu == null) null else {
                    {
                        IconButton(onClick = { haptics.tap(); menuOpen = true }) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.menu_open), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
            if (menuOpen && menu != null) {
                CommandMenuSheet(
                    menu = menu,
                    currentInput = input.text,
                    onInsert = { setInput(it) },
                    onRun = { onCommand(it) },
                    onDismiss = { menuOpen = false },
                )
            }
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
private fun SuggestionPanel(input: String, suggestions: List<Suggestion>, usageHint: String?, offline: Boolean, onPick: (String) -> Unit) {
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
                if (suggestions.isNotEmpty() || offline) HorizontalDivider(color = Color(0x22FFFFFF))
            }
            if (offline) {
                Text(
                    stringResource(R.string.console_suggestions_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = TerminalDim,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
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
private fun ConsoleLineView(line: ConsoleEntry, highlight: String = "", current: Boolean = false) {
    val base = when (line.level) {
        "WARN" -> Color(0xFFFFC857)
        "ERROR", "FATAL" -> Color(0xFFFF6B6B)
        "COMMAND" -> TerminalAccent
        "OUTPUT" -> Color(0xFF8BE9A8)
        else -> TerminalText
    }
    // Parsed once per line and kept while it is in the log, not again on every scroll.
    val plain = remember(line.key, line.formatted) {
        parsedLines.get(line.key) ?: (
            if (line.formatted != null) MinecraftText.parse(line.formatted, base)
            else MinecraftText.highlightPlain(line.text, base, TerminalAccent)
            ).also { parsedLines.put(line.key, it) }
    }
    val text = remember(plain, highlight, current) {
        val ranges = ConsoleSearch.ranges(plain.text, highlight)
        if (ranges.isEmpty()) {
            plain
        } else {
            AnnotatedString.Builder(plain).apply {
                val style = SpanStyle(
                    background = if (current) Color(0xFFFFC857) else Color(0x66FFC857),
                    color = if (current) Color(0xFF1B1B1B) else Color.Unspecified,
                )
                ranges.forEach { addStyle(style, it.first, it.last + 1) }
            }.toAnnotatedString()
        }
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
    /** Shown between the text field and the send button (the console's command menu). */
    extra: (@Composable () -> Unit)? = null,
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
        if (extra != null) Box(Modifier.padding(start = 4.dp)) { extra() }
        FilledIconButton(onClick = onSend, enabled = value.text.isNotBlank(), modifier = Modifier.padding(start = if (extra != null) 0.dp else 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send))
        }
    }
}

@Composable
private fun ConsoleSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    level: LevelFilter,
    onLevel: (LevelFilter) -> Unit,
    /** Selected match and number of matches; null when there are none. */
    position: Pair<Int, Int>?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.console_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    when {
                        position != null -> Text("${position.first}/${position.second}", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 12.dp))
                        query.isNotBlank() -> Text(stringResource(R.string.console_no_matches), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 12.dp))
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { onPrevious() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPrevious, enabled = position != null) {
                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.console_match_previous))
            }
            IconButton(onClick = onNext, enabled = position != null) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.console_match_next))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelFilter.entries.forEach { f ->
                    FilterChip(
                        selected = level == f,
                        onClick = { onLevel(f) },
                        label = {
                            Text(stringResource(when (f) {
                                LevelFilter.ALL -> R.string.console_filter_all
                                LevelFilter.WARNINGS -> R.string.console_filter_warnings
                                LevelFilter.ERRORS -> R.string.console_filter_errors
                            }))
                        },
                    )
                }
            }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, stringResource(R.string.console_share)) }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close)) }
        }
    }
}
