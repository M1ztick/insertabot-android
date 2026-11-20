package org.mistykmedia.insertabot.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.core.net.toUri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mistykmedia.insertabot.data.AccessToken

/**
 * Email sign-in through the Worker's Cloudflare Access application.
 *
 * Access already offers one-time-PIN login (the web app uses it), so there is
 * no account system to build here: the Worker's own address is opened in a
 * WebView, Access shows its login page, and once the code is accepted it sets a
 * `CF_Authorization` cookie on the Worker's domain and redirects back. That
 * cookie is the session. It is read the moment it appears, handed to
 * [onSignedIn], and the screen closes; the WebView never renders the web app.
 *
 * The app then sends the token as `cf-access-token` (see Credentials), which is
 * how Access expects non-browser clients to present it. Its lifetime is the
 * Access application's session duration.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(
    workerUrl: String,
    onSignedIn: (token: String, binding: String?) -> Unit,
    onCancel: () -> Unit
) {
    val startUrl = remember(workerUrl) { workerUrl.trim().trimEnd('/') + "/" }
    val workerHost = remember(startUrl) { startUrl.toUri().host }
    val signedIn by rememberUpdatedState(onSignedIn)

    var progress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var completed by remember { mutableStateOf(false) }

    /** Take the session if Access has set one that is still good. */
    fun tryComplete(): Boolean {
        if (completed) return true
        val cookies = CookieManager.getInstance().getCookie(startUrl)
        val token = AccessToken.cookieValue(cookies, AccessToken.COOKIE) ?: return false
        val identity = AccessToken.parse(token) ?: return false
        if (identity.isExpired(System.currentTimeMillis() / 1000)) return false
        completed = true
        CookieManager.getInstance().flush()
        signedIn(token, AccessToken.cookieValue(cookies, AccessToken.BINDING_COOKIE))
        return true
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onCancel()
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel sign-in")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Sign in", style = MaterialTheme.typography.titleMedium)
                        Text(
                            workerHost.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "Enter your email and you'll get a one-time code. " +
                        "Switch to your email app to copy it, then come back here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                )
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    // A leftover session for this Worker would be picked up
                    // before the login page ever showed. Starting clean means a
                    // sign-in always ends with the account just entered. The
                    // team-domain session is left alone, so an unexpired one
                    // still signs back in without a new code.
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setCookie(startUrl, "${AccessToken.COOKIE}=; Max-Age=0; Path=/")
                    cookies.setCookie(startUrl, "${AccessToken.BINDING_COOKIE}=; Max-Age=0; Path=/")

                    WebView(context).apply {
                        // Access's login page needs script and storage to run.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                // Login is HTTPS end to end; refuse anything else
                                // rather than hand it to another app.
                                return request.url.scheme != "https"
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                // Access sets the cookie on the redirect back to the
                                // Worker; stop there instead of loading the web app.
                                if (url.orEmpty().toUri().host == workerHost && tryComplete()) {
                                    view.stopLoading()
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                progress = 100
                                if (url.orEmpty().toUri().host == workerHost) tryComplete()
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame && !completed) {
                                    loadError = "Couldn't reach the sign-in page. Check your connection and try again."
                                }
                            }
                        }
                        webView = this
                        loadUrl(startUrl)
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                    webView = null
                }
            )

            loadError?.let { message ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    ) {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(message, textAlign = TextAlign.Center)
                        Button(onClick = {
                            loadError = null
                            webView?.loadUrl(startUrl)
                        }) { Text("Try again") }
                    }
                }
            }
        }
    }
}
