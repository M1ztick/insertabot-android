package org.mistykmedia.insertabot.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Block-level parsing. The cases that matter are the half-arrived ones: the
 * model streams token by token, so every intermediate state is rendered, and
 * a block that only parses once its terminator lands would flicker the
 * remainder of the message in and out.
 */
class MarkdownBlockTest {

    private fun blocks(source: String) = parseBlocks(source)

    @Test
    fun `heading level comes from the hash count`() {
        assertEquals(listOf(MdBlock.Heading(1, "Title")), blocks("# Title"))
        assertEquals(listOf(MdBlock.Heading(3, "Sub")), blocks("### Sub"))
        assertEquals(listOf(MdBlock.Heading(6, "Deep")), blocks("###### Deep"))
    }

    @Test
    fun `seven hashes is not a heading`() {
        assertEquals(listOf(MdBlock.Paragraph("####### Nope")), blocks("####### Nope"))
    }

    @Test
    fun `consecutive lines join one paragraph and a blank line splits them`() {
        assertEquals(
            listOf(MdBlock.Paragraph("one\ntwo"), MdBlock.Paragraph("three")),
            blocks("one\ntwo\n\nthree")
        )
    }

    @Test
    fun `fenced code keeps its body verbatim`() {
        assertEquals(
            listOf(MdBlock.Code("fun main() {\n    println(\"hi\")\n}")),
            blocks("```kotlin\nfun main() {\n    println(\"hi\")\n}\n```")
        )
    }

    @Test
    fun `an unterminated fence still renders as code`() {
        // The normal mid-stream state: the closing fence has not arrived yet.
        assertEquals(listOf(MdBlock.Code("val x = 1")), blocks("```\nval x = 1"))
    }

    @Test
    fun `text after a closed fence returns to paragraphs`() {
        assertEquals(
            listOf(MdBlock.Code("code"), MdBlock.Paragraph("after")),
            blocks("```\ncode\n```\nafter")
        )
    }

    @Test
    fun `bullet and ordered markers are normalised`() {
        assertEquals(
            listOf(MdBlock.Item("•", "first"), MdBlock.Item("•", "second"), MdBlock.Item("•", "third")),
            blocks("- first\n* second\n+ third")
        )
        assertEquals(
            listOf(MdBlock.Item("1.", "first"), MdBlock.Item("2.", "second")),
            blocks("1. first\n2) second")
        )
    }

    @Test
    fun `horizontal rules are recognised in each marker style`() {
        assertEquals(listOf(MdBlock.Rule), blocks("---"))
        assertEquals(listOf(MdBlock.Rule), blocks("***"))
        assertEquals(listOf(MdBlock.Rule), blocks("___"))
    }

    @Test
    fun `block quote drops its marker`() {
        assertEquals(listOf(MdBlock.Quote("quoted")), blocks("> quoted"))
    }

    @Test
    fun `pipe table needs a divider row to be a table`() {
        assertEquals(
            listOf(
                MdBlock.Table(
                    header = listOf("a", "b"),
                    rows = listOf(listOf("1", "2"), listOf("3", "4"))
                )
            ),
            blocks("| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |")
        )
    }

    @Test
    fun `a pipe line without a divider stays a paragraph`() {
        assertEquals(listOf(MdBlock.Paragraph("a | b")), blocks("a | b"))
    }

    @Test
    fun `a ragged table row is kept as-is rather than dropped`() {
        val table = blocks("| a | b | c |\n|---|---|---|\n| 1 |").single() as MdBlock.Table
        assertEquals(listOf("a", "b", "c"), table.header)
        assertEquals(listOf(listOf("1")), table.rows)
    }

    @Test
    fun `a table ends at the first blank line`() {
        val parsed = blocks("| a |\n|---|\n| 1 |\n\nafter")
        assertEquals(2, parsed.size)
        assertTrue(parsed[0] is MdBlock.Table)
        assertEquals(MdBlock.Paragraph("after"), parsed[1])
    }

    @Test
    fun `carriage returns are normalised away`() {
        assertEquals(
            listOf(MdBlock.Heading(1, "Title"), MdBlock.Paragraph("body")),
            blocks("# Title\r\nbody")
        )
    }

    @Test
    fun `empty and blank input produce no blocks`() {
        assertEquals(emptyList<MdBlock>(), blocks(""))
        assertEquals(emptyList<MdBlock>(), blocks("   \n\n  "))
    }

    @Test
    fun `every prefix of a rich document parses without throwing`() {
        val document = """
            # Heading
            Some **bold** and `code`.

            - item one
            - item two

            | a | b |
            |---|---|
            | 1 | 2 |

            ```kotlin
            val x = 1
            ```
            > quoted
            ---
        """.trimIndent()
        for (end in 0..document.length) {
            val prefix = document.substring(0, end)
            // Throwing here would crash the message list mid-stream.
            parseBlocks(prefix)
        }
    }
}
