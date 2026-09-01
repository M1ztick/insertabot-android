package org.mistykmedia.insertabot.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Minimal Markdown rendering for agent replies.
 *
 * The model answers in Markdown, so raw `##` and `**` reach the reader unless
 * something interprets them. This covers the subset a chat model actually
 * emits — headings, emphasis, code, lists, quotes, rules and pipe tables — and
 * deliberately not the whole spec.
 *
 * Everything degrades to literal text rather than throwing or eating input.
 * That matters most while streaming: a half-arrived `**bo` has no closing
 * delimiter yet, and must render as the characters typed rather than swallow
 * the rest of the message until the closer shows up.
 */

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Item(val marker: String, val text: String) : MdBlock
    data class Code(val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
private val ORDERED = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
private val RULE = Regex("^\\s*([-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$")
private val TABLE_DIVIDER = Regex("^\\s*\\|?[\\s:|-]+\\|[\\s:|-]*$")

private fun splitRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

private fun parseBlocks(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        // Fenced code. An unterminated fence still renders — it is the normal
        // mid-stream state, not an error.
        if (line.trimStart().startsWith("```")) {
            flushParagraph()
            val body = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                body.appendLine(lines[i]); i++
            }
            if (i < lines.size) i++ // consume the closing fence when present
            blocks += MdBlock.Code(body.toString().trimEnd('\n'))
            continue
        }

        // Pipe table: a header row followed by a |---|---| divider.
        if (line.contains('|') && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1])) {
            flushParagraph()
            val header = splitRow(line)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                rows += splitRow(lines[i]); i++
            }
            blocks += MdBlock.Table(header, rows)
            continue
        }

        when {
            line.isBlank() -> flushParagraph()

            RULE.matches(line) -> { flushParagraph(); blocks += MdBlock.Rule }

            HEADING.matches(line) -> {
                flushParagraph()
                val m = HEADING.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }

            line.trimStart().startsWith("> ") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.trimStart().removePrefix("> "))
            }

            BULLET.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Item("•", BULLET.find(line)!!.groupValues[1])
            }

            ORDERED.matches(line) -> {
                flushParagraph()
                val m = ORDERED.find(line)!!
                blocks += MdBlock.Item("${m.groupValues[1]}.", m.groupValues[2])
            }

            else -> paragraph.appendLine(line)
        }
        i++
    }
    flushParagraph()
    return blocks
}

/**
 * Inline spans: `code`, **bold**, *italic*, ~~strike~~, [text](url).
 *
 * A delimiter only takes effect once its closer is found, so unterminated
 * markers stay literal instead of consuming the remainder of the message.
 */
private fun inline(source: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < source.length) {
        val c = source[i]

        // Code spans win over everything else — no emphasis parsed inside them.
        if (c == '`') {
            val end = source.indexOf('`', i + 1)
            if (end > i) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(source.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }

        // *** / ___ is bold+italic, and must be tried before ** or the
        // leading and trailing single markers survive as literal text.
        val three = if (i + 2 < source.length) source.substring(i, i + 3) else ""
        if (three == "***" || three == "___") {
            val end = source.indexOf(three, i + 3)
            if (end > i) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(inline(source.substring(i + 3, end), linkColor))
                }
                i = end + 3; continue
            }
        }

        val two = if (i + 1 < source.length) source.substring(i, i + 2) else ""
        val twoStyle = when (two) {
            "**", "__" -> SpanStyle(fontWeight = FontWeight.Bold)
            "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            else -> null
        }
        if (twoStyle != null) {
            val end = source.indexOf(two, i + 2)
            if (end > i) {
                withStyle(twoStyle) { append(inline(source.substring(i + 2, end), linkColor)) }
                i = end + 2; continue
            }
            // No closer yet — normal mid-stream. Fall through and emit literally.
        }

        if ((c == '*' || c == '_') && twoStyle == null) {
            val end = source.indexOf(c, i + 1)
            // end == i + 1 would be an empty span, which is a literal pair.
            if (end > i + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(inline(source.substring(i + 1, end), linkColor))
                }
                i = end + 1; continue
            }
        }

        // [label](url) — styled, not clickable.
        if (c == '[') {
            val close = source.indexOf(']', i)
            if (close > i && close + 1 < source.length && source[close + 1] == '(') {
                val paren = source.indexOf(')', close)
                if (paren > close) {
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(source.substring(i + 1, close))
                    }
                    i = paren + 1; continue
                }
            }
        }

        append(c); i++
    }
}

@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    val linkColor = MaterialTheme.colorScheme.primary

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, linkColor),
                    color = color,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                )

                is MdBlock.Paragraph ->
                    Text(inline(block.text, linkColor), color = color, style = MaterialTheme.typography.bodyMedium)

                is MdBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(block.marker, color = color, style = MaterialTheme.typography.bodyMedium)
                    Text(inline(block.text, linkColor), color = color, style = MaterialTheme.typography.bodyMedium)
                }

                is MdBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Code must not force the bubble wide; it scrolls on its own.
                    Box(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
                        Text(
                            block.code,
                            color = color,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            softWrap = false
                        )
                    }
                }

                is MdBlock.Quote -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(3.dp).padding(vertical = 2.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {}
                    }
                    Text(
                        inline(block.text, linkColor),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                MdBlock.Rule -> HorizontalDivider()

                is MdBlock.Table -> Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            block.header.forEach {
                                Text(
                                    inline(it, linkColor),
                                    color = color,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                        block.rows.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                row.forEach {
                                    Text(
                                        inline(it, linkColor),
                                        color = color,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(120.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
