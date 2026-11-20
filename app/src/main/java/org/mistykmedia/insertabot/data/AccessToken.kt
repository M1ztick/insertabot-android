package org.mistykmedia.insertabot.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/**
 * What the app needs from a Cloudflare Access application token: who it
 * belongs to and when it runs out.
 *
 * Read from the JWT payload WITHOUT checking the signature. That is fine here:
 * the Worker verifies every token it receives, so this is only used to label
 * the account in the UI and to skip connecting with a token that has visibly
 * expired. Nothing is granted on the strength of it.
 */
data class AccessIdentity(
    val email: String?,
    val expiresAtEpochSeconds: Long?
) {
    /**
     * True once the token is within [skewSeconds] of its expiry. The margin
     * avoids opening a socket with a token that dies during the handshake.
     */
    fun isExpired(nowEpochSeconds: Long, skewSeconds: Long = 60): Boolean =
        expiresAtEpochSeconds != null && expiresAtEpochSeconds <= nowEpochSeconds + skewSeconds
}

object AccessToken {
    /** The cookie Access sets on the application's own domain after login. */
    const val COOKIE = "CF_Authorization"

    /**
     * Set alongside [COOKIE] when the Access app has "binding cookie" enabled;
     * the token is then only accepted together with it.
     */
    const val BINDING_COOKIE = "CF_Binding"

    /** Returns null for anything that is not a readable three-part JWT. */
    fun parse(jwt: String?): AccessIdentity? {
        val parts = jwt?.trim()?.split('.') ?: return null
        if (parts.size != 3 || parts[1].isEmpty()) return null
        val payload = runCatching {
            // The URL-safe decoder accepts the unpadded form JWTs use.
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val claims = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return null
        val email = (claims["email"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val exp = (claims["exp"] as? JsonPrimitive)?.longOrNull
        return AccessIdentity(email, exp)
    }

    /** One cookie's value from a `Cookie`-header-shaped string, as CookieManager returns. */
    fun cookieValue(cookieHeader: String?, name: String): String? =
        cookieHeader
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
}
