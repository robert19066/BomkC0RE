package com.example.examplewvapp20

import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException


// ── Shebang resolution result ─────────────────────────────────────────────────

sealed class ShebangResult {
    /** A valid asset URL ready to pass to WebView.loadUrl() */
    data class AssetUrl(val url: String) : ShebangResult()

    /** Something went wrong — show the user an error page */
    data class Error(val code: ErrorCode, val detail: String? = null) : ShebangResult()
}

enum class ErrorCode(val code: String, val message: String) {
    NO_PRIME_SHEBANG   ("001", "No entry-point found.\nMake sure your app was built correctly (missing _\$1 file)."),
    DUPLICATE_SHEBANG  ("002", "Duplicate entry-points detected.\nOnly one file tagged _\$1 is allowed."),
    ASSET_IO_ERROR     ("003", "Could not read app assets.\nThe app package may be corrupt."),
}


// ── MainActivity ──────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = resolveEntryPoint(assets)
        setContent {
            WebViewScreen(result)
        }
    }
}


// ── Asset resolution (pure function — easy to unit-test) ──────────────────────

fun resolveEntryPoint(assetManager: AssetManager, tag: String = "_\$1.html"): ShebangResult {
    val files = try {
        assetManager.list("") ?: emptyArray()
    } catch (e: IOException) {
        return ShebangResult.Error(ErrorCode.ASSET_IO_ERROR, e.message)
    }

    val matches = files.filter { it.endsWith(tag) }
    return when (matches.size) {
        1    -> ShebangResult.AssetUrl("file:///android_asset/${matches.first()}")
        0    -> ShebangResult.Error(ErrorCode.NO_PRIME_SHEBANG)
        else -> ShebangResult.Error(ErrorCode.DUPLICATE_SHEBANG,
                    "Found: ${matches.joinToString()}")
    }
}


// ── Error page (generated once, not scattered through business logic) ─────────

private const val SIMPLEWV_VERSION = "2.0"

fun buildErrorPage(code: ErrorCode, detail: String? = null): String {
    val detailBlock = if (detail != null)
        """<p class="detail">$detail</p>"""
    else ""

    // language=HTML
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <title>SimpleWV Error</title>
          <style>
            *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              min-height: 100vh;
              display: flex;
              align-items: center;
              justify-content: center;
              font-family: system-ui, sans-serif;
              background: #0f0f0f;
              color: #e8e8e8;
              padding: 24px;
            }
            .card {
              background: #1a1a1a;
              border: 1px solid #2e2e2e;
              border-radius: 16px;
              padding: 36px 32px;
              max-width: 400px;
              width: 100%;
              text-align: center;
            }
            .badge {
              display: inline-block;
              background: #ff3b3b22;
              color: #ff6b6b;
              border: 1px solid #ff3b3b55;
              border-radius: 8px;
              font-size: 11px;
              font-weight: 700;
              letter-spacing: .08em;
              padding: 4px 10px;
              margin-bottom: 20px;
              text-transform: uppercase;
            }
            h1 {
              font-size: 18px;
              font-weight: 600;
              margin-bottom: 10px;
              color: #fff;
            }
            .message {
              font-size: 14px;
              color: #aaa;
              line-height: 1.6;
              white-space: pre-line;
            }
            .detail {
              margin-top: 16px;
              font-size: 12px;
              color: #666;
              font-family: monospace;
              background: #111;
              border-radius: 8px;
              padding: 10px 12px;
              text-align: left;
              word-break: break-all;
            }
            .footer {
              margin-top: 28px;
              font-size: 11px;
              color: #444;
            }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="badge">Error ${code.code}</div>
            <h1>${code.message.lines().first()}</h1>
            <p class="message">${code.message.lines().drop(1).joinToString("\n")}</p>
            $detailBlock
            <div class="footer">SimpleWV $SIMPLEWV_VERSION</div>
          </div>
        </body>
        </html>
    """.trimIndent()
}


// ── WebView composable ────────────────────────────────────────────────────────

@Composable
fun WebViewScreen(result: ShebangResult) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this

                with(settings) {
                    javaScriptEnabled  = true
                    domStorageEnabled  = true
                    allowFileAccess    = true
                }

                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        canGoBack = view?.canGoBack() == true
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith("mailto:")) {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                            )
                            return true
                        }
                        return false
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        // Only show an error page for the main frame, not sub-resources
                        if (request?.isForMainFrame != true) return
                        val desc = error?.description?.toString() ?: "unknown error"
                        val code = error?.errorCode ?: -1
                        val html = buildErrorPage(
                            ErrorCode.ASSET_IO_ERROR,
                            "WebView error $code: $desc"
                        )
                        view?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        if (request?.isForMainFrame != true) return
                        val status = errorResponse?.statusCode ?: 0
                        val html = buildErrorPage(
                            ErrorCode.ASSET_IO_ERROR,
                            "HTTP error $status"
                        )
                        view?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }
                }

                when (result) {
                    is ShebangResult.AssetUrl -> loadUrl(result.url)
                    is ShebangResult.Error    -> loadDataWithBaseURL(
                        null,
                        buildErrorPage(result.code, result.detail),
                        "text/html", "UTF-8", null
                    )
                }
            }
        }
    )
}
