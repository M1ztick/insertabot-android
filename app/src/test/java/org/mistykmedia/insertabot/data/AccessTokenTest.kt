package org.mistykmedia.insertabot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AccessTokenTest {

    private fun jwt(payloadJson: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return listOf(
            enc.encodeToString("""{"alg":"RS256"}""".toByteArray()),
            enc.encodeToString(payloadJson.toByteArray()),
            "signature"
        ).joinToString(".")
    }

    @Test
    fun `reads email and expiry from an access token`() {
        val identity = AccessToken.parse(jwt("""{"email":"me@example.com","exp":1900000000,"aud":["x"]}"""))
        assertEquals("me@example.com", identity?.email)
        assertEquals(1900000000L, identity?.expiresAtEpochSeconds)
    }

    @Test
    fun `service token payload has no email`() {
        // Access puts the client id in common_name and leaves sub empty.
        val identity = AccessToken.parse(jwt("""{"common_name":"abc.access","sub":"","exp":1}"""))
        assertNull(identity?.email)
        assertEquals(1L, identity?.expiresAtEpochSeconds)
    }

    @Test
    fun `payload with url-safe characters and no padding decodes`() {
        // Long enough values push the base64 into using - and _ and odd lengths.
        val identity = AccessToken.parse(jwt("""{"email":"a.b+c~?>>>@example.com","exp":2}"""))
        assertEquals("a.b+c~?>>>@example.com", identity?.email)
    }

    @Test
    fun `rejects things that are not jwts`() {
        assertNull(AccessToken.parse(null))
        assertNull(AccessToken.parse(""))
        assertNull(AccessToken.parse("not-a-token"))
        assertNull(AccessToken.parse("a..c"))
        assertNull(AccessToken.parse("a.%%%.c"))
        assertNull(AccessToken.parse(jwt("[1,2,3]")))
    }

    @Test
    fun `expiry honours the skew margin`() {
        val identity = AccessIdentity(email = null, expiresAtEpochSeconds = 1_000)
        assertFalse(identity.isExpired(nowEpochSeconds = 900, skewSeconds = 60))
        assertTrue(identity.isExpired(nowEpochSeconds = 940, skewSeconds = 60))
        assertTrue(identity.isExpired(nowEpochSeconds = 2_000))
        // No exp claim: nothing to judge by, so not treated as expired.
        assertFalse(AccessIdentity(null, null).isExpired(Long.MAX_VALUE / 2))
    }

    @Test
    fun `cookie values are pulled from a cookie header string`() {
        val header = "foo=bar; CF_Authorization=eyJ.abc.def; CF_Binding=bind123"
        assertEquals("eyJ.abc.def", AccessToken.cookieValue(header, AccessToken.COOKIE))
        assertEquals("bind123", AccessToken.cookieValue(header, AccessToken.BINDING_COOKIE))
        assertNull(AccessToken.cookieValue(header, "missing"))
        assertNull(AccessToken.cookieValue(null, AccessToken.COOKIE))
        // A prefix match on another cookie's name must not count.
        assertNull(AccessToken.cookieValue("XCF_Authorization=nope", AccessToken.COOKIE))
        assertNull(AccessToken.cookieValue("CF_Authorization=", AccessToken.COOKIE))
    }
}
