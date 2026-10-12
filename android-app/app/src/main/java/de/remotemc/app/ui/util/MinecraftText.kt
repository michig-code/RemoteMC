package de.remotemc.app.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Renders Minecraft formatting codes (§a, §l, §x§r§r§g§g§b§b …) as styled text for the dark console.
 * Colours follow Minecraft's palette, with black and dark blue lifted so they stay readable.
 */
object MinecraftText {

    private val palette = mapOf(
        '0' to Color(0xFF6B7A70), '1' to Color(0xFF6C7BFF), '2' to Color(0xFF3FBF4F), '3' to Color(0xFF2EC4C4),
        '4' to Color(0xFFE05252), '5' to Color(0xFFC060D0), '6' to Color(0xFFFFB13B), '7' to Color(0xFFB8C2BC),
        '8' to Color(0xFF7D8A82), '9' to Color(0xFF7C8CFF), 'a' to Color(0xFF6BF27B), 'b' to Color(0xFF6BF2F2),
        'c' to Color(0xFFFF6B6B), 'd' to Color(0xFFFF7BEA), 'e' to Color(0xFFFFF06B), 'f' to Color(0xFFF2F5F3),
    )

    fun parse(text: String, base: Color): AnnotatedString = buildAnnotatedString {
        var color = base
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        val run = StringBuilder()

        fun flush() {
            if (run.isEmpty()) return
            val decorations = listOfNotNull(
                TextDecoration.Underline.takeIf { underline },
                TextDecoration.LineThrough.takeIf { strike },
            )
            pushStyle(
                SpanStyle(
                    color = color,
                    fontWeight = if (bold) FontWeight.Bold else null,
                    fontStyle = if (italic) FontStyle.Italic else null,
                    textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                ),
            )
            append(run)
            pop()
            run.clear()
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '§' || i + 1 >= text.length) {
                if (c != '§') run.append(c) // a dangling § at the end is dropped
                i++
                continue
            }
            val code = text[i + 1].lowercaseChar()
            // §x§R§R§G§G§B§B hex colour
            if (code == 'x' && i + 13 < text.length) {
                val hex = buildString { for (k in 0 until 6) append(text.getOrNull(i + 3 + k * 2) ?: '0') }
                val parsed = hex.toLongOrNull(16)
                if (parsed != null) {
                    flush()
                    color = Color(0xFF000000 or parsed)
                    bold = false; italic = false; underline = false; strike = false
                    i += 14
                    continue
                }
            }
            flush()
            when (code) {
                in palette -> {
                    color = palette.getValue(code)
                    bold = false; italic = false; underline = false; strike = false
                }
                'l' -> bold = true
                'o' -> italic = true
                'n' -> underline = true
                'm' -> strike = true
                'r' -> {
                    color = base
                    bold = false; italic = false; underline = false; strike = false
                }
                // 'k' (obfuscated) is ignored
            }
            i += 2
        }
        flush()
    }

    /** Lines without own colours get a subtle highlight of the "[Plugin]" prefix. */
    fun highlightPlain(text: String, base: Color, accent: Color): AnnotatedString = buildAnnotatedString {
        val match = Regex("^\\[[^\\]]{1,32}]").find(text)
        if (match != null) {
            pushStyle(SpanStyle(color = accent))
            append(match.value)
            pop()
            pushStyle(SpanStyle(color = base))
            append(text.substring(match.value.length))
            pop()
        } else {
            pushStyle(SpanStyle(color = base))
            append(text)
            pop()
        }
    }
}
