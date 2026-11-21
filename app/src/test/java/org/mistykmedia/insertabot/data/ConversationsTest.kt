package org.mistykmedia.insertabot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationsTest {

    @Test
    fun `parses a page in the shape the Worker sends`() {
        val body = """
            {"conversations":[
              {"id":"0f9c2a11-4c3d-4a1e-9c77-2b8d6e5a1f30","title":"Plan the launch","preview":"Sounds good.",
               "messageCount":6,"createdAt":1763700000000,"updatedAt":1763703600000,"extra":"ignored"},
              {"id":"abc","title":"","preview":"","messageCount":0,"createdAt":1,"updatedAt":2}
            ],"nextCursor":"MTc2Mzcw"}
        """.trimIndent()
        val page = parseConversationPage(body)
        assertEquals(2, page.conversations.size)
        assertEquals("Plan the launch", page.conversations[0].displayTitle)
        assertEquals(6, page.conversations[0].messageCount)
        assertEquals(1763703600000L, page.conversations[0].updatedAt)
        assertEquals("New conversation", page.conversations[1].displayTitle)
        assertEquals("MTc2Mzcw", page.nextCursor)
    }

    @Test
    fun `last page has a null cursor`() {
        assertNull(parseConversationPage("""{"conversations":[],"nextCursor":null}""").nextCursor)
        assertNull(parseConversationPage("""{"conversations":[]}""").nextCursor)
    }

    @Test
    fun `parses a single renamed conversation`() {
        val c = parseConversation("""{"id":"x1","title":"Renamed","preview":"p","messageCount":2,"createdAt":1,"updatedAt":3}""")
        assertEquals("Renamed", c.title)
    }

    @Test
    fun `ids follow the Worker's rule, not just uuids`() {
        assertTrue(ConversationIds.isValid("0f9c2a11-4c3d-4a1e-9c77-2b8d6e5a1f30"))
        assertTrue(ConversationIds.isValid("legacy_thread-42"))
        assertFalse(ConversationIds.isValid(null))
        assertFalse(ConversationIds.isValid(""))
        assertFalse(ConversationIds.isValid("has space"))
        assertFalse(ConversationIds.isValid("a/b"))
        assertFalse(ConversationIds.isValid("owner~thread"))
        assertFalse(ConversationIds.isValid("x".repeat(129)))
    }
}
