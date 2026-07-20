package org.neteinstein.instamaps.feature.instagramauth.presentation

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.neteinstein.instamaps.core.designsystem.component.LoadingIndicator
import org.neteinstein.instamaps.feature.instagramauth.R

private const val INSTAGRAM_LOGIN_URL = "https://www.instagram.com/accounts/login/"
private const val SESSION_COOKIE_MARKER = "sessionid="

// Instagram's WebView experience degrades noticeably under the WebView's default user agent
// (extra "install the app"/"use the app" prompts, broken layouts) - presenting as a mobile
// browser avoids that, same rationale a real browser-based login flow would need.
//
// Deliberately omits a "Chrome/<version>" token. Instagram serves a modern React/StyleX bundle
// to any user agent it recognizes as a current Chrome, and on some WebView builds that bundle's
// login modal never paints (confirmed via Chrome DevTools Protocol: the DOM/layout/CSSOM for the
// dialog are all correct, but Blink itself never rasterizes a single pixel for it - visible even
// in a raw `Page.captureScreenshot`, so it isn't an Android/Compose compositing issue). Dropping
// the Chrome token - while keeping a modern, real device string so this still isn't detected as
// an embedded WebView - makes Instagram fall back to its simpler, legacy-browser-compatible login
// page, which renders correctly and exposes the same real login form (same backend, same
// `sessionid` cookie on success).
private const val MOBILE_LOGIN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Version/4.0 Mobile Safari/537.36"

// Instagram shows a cookie-consent dialog before the login form on first visit. On the WebView
// builds this app has been tested against, that dialog's own content (its buttons and text) never
// paints - confirmed via Chrome DevTools Protocol: real layout/DOM, zero rasterized pixels - even
// though its full-screen backdrop renders fine. That leaves an invisible-but-fully-functional
// modal sitting on top of (and intercepting every touch for) the real login form underneath, even
// once the form itself is visible. Auto-accepting it here is equivalent to the user tapping the
// (invisible) "Allow all cookies" button themselves, and immediately unblocks the real form.
private const val AUTO_ACCEPT_COOKIES_SCRIPT =
    """
    (function() {
        function tryAccept() {
            var candidates = document.querySelectorAll('button, [role="button"]');
            for (var i = 0; i < candidates.length; i++) {
                var text = (candidates[i].innerText || candidates[i].textContent || '').trim();
                if (text === 'Allow all cookies') {
                    candidates[i].click();
                    return true;
                }
            }
            return false;
        }
        if (tryAccept()) return;
        var observer = new MutationObserver(function() {
            if (tryAccept()) observer.disconnect();
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
        setTimeout(function() { observer.disconnect(); }, 15000);
    })();
    """

/** Plain (non-Compose-state) holder so [InstagramLoginScreen]'s `BackHandler` can reach the WebView without tying its lifecycle to recomposition. */
private class WebViewHolder {
    var webView: WebView? = null
}

/**
 * Stateful entry point: wires [InstagramLoginViewModel] to [InstagramLoginScreen]. Navigates back
 * automatically via [onLoginSuccess] once the ViewModel confirms the session was persisted.
 */
@Composable
fun InstagramLoginRoute(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InstagramLoginViewModel = koinViewModel(),
) {
    val loginCompleted by viewModel.loginCompleted.collectAsStateWithLifecycle()
    LaunchedEffect(loginCompleted) {
        if (loginCompleted) onLoginSuccess()
    }
    InstagramLoginScreen(
        onBack = onBack,
        onCookieCaptured = viewModel::onSessionCaptured,
        modifier = modifier,
    )
}

/**
 * Hosts a real `WebView` pointed at Instagram's own login page - InstaMaps never sees the
 * entered password, only the resulting session cookie. [onCookieCaptured] fires at most once,
 * from [WebViewClient.onPageFinished], the first time the cookie jar for the current URL contains
 * the `sessionid` cookie (the only one of Instagram's cookies that's set exclusively *after* a
 * successful login - `csrftoken`/`mid`/`ig_did` are all present pre-login too).
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginScreen(
    onBack: () -> Unit,
    onCookieCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val webViewHolder = remember { WebViewHolder() }
    var isLoading by remember { mutableStateOf(true) }

    BackHandler {
        val webView = webViewHolder.webView
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.instagram_login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.instagram_login_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Text(
                text = stringResource(R.string.instagram_login_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = MOBILE_LOGIN_USER_AGENT
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            var cookieCaptured = false
                            webViewClient =
                                object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?,
                                    ) {
                                        isLoading = true
                                        view?.evaluateJavascript(AUTO_ACCEPT_COOKIES_SCRIPT, null)
                                    }

                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?,
                                    ) {
                                        isLoading = false
                                        // Re-inject (idempotent) rather than relying solely on onPageStarted: that
                                        // callback can fire before the new document is fully committed, and
                                        // evaluateJavascript silently applies to the wrong context when it does.
                                        view?.evaluateJavascript(AUTO_ACCEPT_COOKIES_SCRIPT, null)
                                        if (cookieCaptured) return
                                        val cookies = CookieManager.getInstance().getCookie(url)
                                        if (cookies != null && cookies.contains(SESSION_COOKIE_MARKER)) {
                                            cookieCaptured = true
                                            onCookieCaptured(cookies)
                                        }
                                    }
                                }
                            loadUrl(INSTAGRAM_LOGIN_URL)
                            webViewHolder.webView = this
                        }
                    },
                )
                if (isLoading) {
                    LoadingIndicator()
                }
            }
        }
    }
}
