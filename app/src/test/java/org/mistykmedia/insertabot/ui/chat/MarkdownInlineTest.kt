package org.mistykmedia.insertabot.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inline spans. The governing rule is in the parser's own doc comment: a
 * delimiter only takes effect once its closer arrives, so a half-streamed
 * `**bo` renders as typed instead of swallowing the rest of the message.
 * These pin that behaviour down, since it is invisible in a finished reply
 * and only shows up while tokens are still arriving.
 */
class MarkdownInlineTest {

    private val link = Color.Blue

    private fun render(source: String) = inline(source, link)
    private fun text(source: String) = render(source).text

    private fun styleAt(source: String, index: Int) =
        render(source).spanStyles.firstOrNull { index >= it.start && index < it.end }?.item

    @Test
    fun `plain text passes through untouched`() {
        assertEquals("just words", text("just words"))
        assertEquals("", text(""))
    }

    @Test
    fun `bold strips its delimiters and carries weight`() {
        assertEquals("bold", text("**bold**"))
        assertEquals(FontWeight.Bold, styleAt("**bold**", 0)?.fontWeight)
        assertEquals("bold", text("__bold__"))
    }

    @Test
    fun `italic strips its delimiters and carries style`() {
        assertEquals("soft", text("*soft*"))
        assertEquals(FontStyle.Italic, styleAt("*soft*", 0)?.fontStyle)
        assertEquals("soft", text("_soft_"))
    }

    @Test
    fun `bold italic is tried before bold so no markers survive`() {
        assertEquals("both", text("***both***"))
        val style = styleAt("***both***", 0)
        assertEquals(FontWeight.Bold, style?.fontWeight)
        assertEquals(FontStyle.Italic, style?.fontStyle)
        assertEquals("both", text("___both___"))
    }

    @Test
    fun `strikethrough is decorated`() {
        assertEquals("gone", text("~~gone~~"))
        assertEquals(TextDecoration.LineThrough, styleAt("~~gone~~", 0)?.textDecoration)
    }

    @Test
    fun `code spans are monospaced and parse no emphasis inside`() {
        assertEquals("a**b**c", text("`a**b**c`"))
        assertNotNull(styleAt("`a**b**c`", 0)?.fontFamily)
    }

    @Test
    fun `links render their label and drop the target`() {
        assertEquals("Anthropic", text("[Anthropic](https://anthropic.com)"))
        assertEquals(TextDecoration.Underline, styleAt("[Anthropic](https://anthropic.com)", 0)?.textDecoration)
    }

    @Test
    fun `mixed emphasis inside a sentence keeps the surrounding text`() {
        assertEquals("a bold and italic mix", text("a **bold** and *italic* mix"))
    }

    // ── Streaming: unterminated markers stay literal ─────────────────────────

    @Test
    fun `an unterminated bold marker renders as typed`() {
        assertEquals("**bo", text("**bo"))
    }

    @Test
    fun `an unterminated italic marker renders as typed`() {
        assertEquals("*it", text("*it"))
        assertEquals("_it", text("_it"))
    }

    @Test
    fun `an unterminated code span renders as typed`() {
        assertEquals("`cod", text("`cod"))
    }

    @Test
    fun `bare delimiters are literal text`() {
        assertEquals("*", text("*"))
        assertEquals("**", text("**"))
        assertEquals("***", text("***"))
        assertEquals("_", text("_"))
        assertEquals("~~", text("~~"))
        assertEquals("`", text("`"))
    }

    @Test
    fun `an empty emphasis pair is literal rather than a zero-width span`() {
        // Without the guard these matched their own closer at zero distance and
        // rendered as nothing at all, quietly deleting the run from the reply.
        assertEquals("**", text("**"))
        assertEquals("____", text("____"))
        assertEquals("****", text("****"))
        assertEquals("~~~~", text("~~~~"))
        assertEquals("______", text("______"))
        assertEquals("******", text("******"))
    }

    @Test
    fun `a rule line reaching the inline parser stays visible`() {
        // parseBlocks claims "---" and "***" as rules, but "___" inside a
        // paragraph does not get that far, and must not vanish.
        assertEquals("___", text("___"))
    }

    @Test
    fun `an unterminated link renders as typed`() {
        assertEquals("[label](http", text("[label](http"))
        assertEquals("[label]", text("[label]"))
    }

    @Test
    fun `every prefix of a rich string renders without throwing or eating input`() {
        val source = "See **bold**, *italic*, ~~struck~~, `code`, and [a link](https://example.com/x)."
        for (end in 0..source.length) {
            val prefix = source.substring(0, end)
            val rendered = text(prefix)
            // Delimiters are only ever removed, never invented, so the visible
            // text can shrink but must never outgrow what was typed.
            assertTrue(
                "prefix of length $end grew: ${rendered.length} > $end",
                rendered.length <= prefix.length
            )
        }
    }

    @Test
    fun `a prefix never swallows text that a longer prefix shows`() {
        // The failure this guards: an opening delimiter consuming the rest of
        // the message until its closer arrives, so the reply visibly truncates
        // itself mid-stream and then springs back.
        val source = "start **middle** end"
        val complete = text(source)
        assertEquals("start middle end", complete)
        for (end in source.length - 4..source.length) {
            assertTrue(text(source.substring(0, end)).startsWith("start "))
        }
    }
}
