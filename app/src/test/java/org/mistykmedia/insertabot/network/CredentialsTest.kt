package org.mistykmedia.insertabot.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CredentialsTest {

    private fun sessionFor(email: String, exp: Long = 1_900_000_000): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val payload = """{"email":"$email","exp":$exp}"""
        return "${enc.encodeToString("{}".toByteArray())}.${enc.encodeToString(payload.toByteArray())}.sig"
    }

    private fun headersFor(credentials: Credentials) =
        credentials.applyTo(Request.Builder().url("https://worker.example/health")).build().headers

    @Test
    fun `a session is sent as cf-access-token and as the access cookie`() {
        val token = sessionFor("me@example.com")
        val headers = headersFor(Credentials(accessToken = token, accessBinding = "b1"))
        assertEquals(token, headers["cf-access-token"])
        assertEquals("CF_Authorization=$token; CF_Binding=b1", headers["Cookie"])
    }

    @Test
    fun `a session suppresses the service token`() {
        // Both are valid to Access but map to different owners on the Worker.
        val headers = headersFor(
            Credentials(
                cfAccessClientId = "id.access",
                cfAccessClientSecret = "secret",
                accessToken = sessionFor("me@example.com")
            )
        )
        assertNull(headers["CF-Access-Client-Id"])
        assertNull(headers["CF-Access-Client-Secret"])
    }

    @Test
    fun `without a session the service token is sent`() {
        val headers = headersFor(Credentials(cfAccessClientId = "id.access", cfAccessClientSecret = "secret"))
        assertEquals("id.access", headers["CF-Access-Client-Id"])
        assertEquals("secret", headers["CF-Access-Client-Secret"])
        assertNull(headers["cf-access-token"])
        assertNull(headers["Cookie"])
    }

    @Test
    fun `empty credentials send nothing and say so`() {
        val credentials = Credentials()
        assertTrue(credentials.isEmpty)
        assertEquals(0, headersFor(credentials).size)
    }

    @Test
    fun `a half-entered service token does not count as a credential`() {
        assertTrue(Credentials(cfAccessClientId = "id.access").isEmpty)
    }

    @Test
    fun `refreshing a session for the same email keeps the same identity`() {
        val first = Credentials(accessToken = sessionFor("me@example.com", exp = 1))
        val refreshed = Credentials(accessToken = sessionFor("me@example.com", exp = 2))
        val someoneElse = Credentials(accessToken = sessionFor("them@example.com"))
        assertEquals(first.identityKey, refreshed.identityKey)
        assertNotEquals(first.identityKey, someoneElse.identityKey)
    }

    @Test
    fun `access refusals are recognised and other failures are not`() {
        listOf(301, 302, 303, 307, 308, 401, 403).forEach { assertTrue("$it", Credentials.isAuthRejection(it)) }
        listOf(200, 404, 500, 502, 503).forEach { assertFalse("$it", Credentials.isAuthRejection(it)) }
    }
}
