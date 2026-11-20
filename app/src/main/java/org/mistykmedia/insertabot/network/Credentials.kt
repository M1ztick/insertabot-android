package org.mistykmedia.insertabot.network

import okhttp3.Request
import org.mistykmedia.insertabot.data.AccessToken

/**
 * Everything that can authenticate a request to the Worker.
 *
 * Two ways in, through the same Cloudflare Access application:
 *  - An email sign-in session ([accessToken], from the in-app login). This is
 *    the normal path, and what testers use.
 *  - A service token ([cfAccessClientId] / [cfAccessClientSecret]), kept for
 *    development and automated clients.
 *
 * When a session exists, the service token is NOT sent. Access would accept
 * either, but they resolve to different identities on the Worker (an email vs.
 * a service-token id), and so to different conversation lists. Sending one
 * makes it unambiguous whose conversations open.
 */
data class Credentials(
    val bearerToken: String = "",
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
    val accessToken: String = "",
    val accessBinding: String = ""
) {
    val hasSession: Boolean get() = accessToken.isNotBlank()
    val hasServiceToken: Boolean get() = cfAccessClientId.isNotBlank() && cfAccessClientSecret.isNotBlank()
    val isEmpty: Boolean get() = !hasSession && !hasServiceToken && bearerToken.isBlank()

    /**
     * Who these credentials act as, for deciding whether a reconnect is still
     * the same conversation list. A refreshed session for the same email is.
     */
    val identityKey: String
        get() = when {
            hasSession -> "usr:" + (AccessToken.parse(accessToken)?.email ?: accessToken)
            hasServiceToken -> "svc:$cfAccessClientId"
            else -> "key:$bearerToken"
        }

    fun applyTo(builder: Request.Builder): Request.Builder = builder.apply {
        if (bearerToken.isNotBlank()) header("Authorization", "Bearer $bearerToken")
        if (hasSession) {
            // The header is Access's documented form for non-browser clients.
            // The cookie carries the same token plus the binding cookie, which
            // an Access app with "binding cookie" enabled requires.
            header("cf-access-token", accessToken)
            header("Cookie", buildString {
                append(AccessToken.COOKIE).append('=').append(accessToken)
                if (accessBinding.isNotBlank()) {
                    append("; ").append(AccessToken.BINDING_COOKIE).append('=').append(accessBinding)
                }
            })
        } else {
            if (cfAccessClientId.isNotBlank()) header("CF-Access-Client-Id", cfAccessClientId)
            if (cfAccessClientSecret.isNotBlank()) header("CF-Access-Client-Secret", cfAccessClientSecret)
        }
    }

    companion object {
        /**
         * Status codes Access answers with when it will not let a request
         * through: 401/403 outright, or a redirect to the login page.
         * Retrying any of these with the same credentials cannot succeed.
         */
        fun isAuthRejection(code: Int): Boolean =
            code == 401 || code == 403 || code in 301..303 || code == 307 || code == 308
    }
}
