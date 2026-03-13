
package com.example.examplewvapp20

import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException

// ── Fluid Resolution System ──────────────────────────────────────────────────

sealed class ShebangResult {
    data class AssetUrl(
        val url: String, 
        val isLegacy: Boolean, 
        val warningsDisabled: Boolean
    ) : ShebangResult()

    data class Error(val code: ErrorCode, val detail: String? = null) : ShebangResult()
}

enum class ErrorCode(val code: String, val title: String, val message: String) {
    NO_PRIME_SHEBANG("001", "ENTRY POINT MISSING", "The engine searched the asset manifest but found no 'Fluid' ID or legacy shebang file."),
    DUPLICATE_SHEBANG("002", "CONFLICT DETECTED", "Multiple entry-points were found. The engine cannot decide which file to ignite."),
    ASSET_IO_ERROR("003", "FS READ FAILURE", "A critical I/O error occurred while scanning the application container."),
    RENDER_CRASH("004", "WEBVIEW PANIC", "The internal rendering engine reported a fatal frame failure.")
}

// ── Asset Logic ──────────────────────────────────────────────────────────────

fun resolveEntryPoint(assetManager: AssetManager): ShebangResult {
    val htmlFiles = try {
        (assetManager.list("") ?: emptyArray()).filter { it.endsWith(".html") }
    } catch (e: IOException) {
        return ShebangResult.Error(ErrorCode.ASSET_IO_ERROR, e.message)
    }

    var fluidEntryPoint: String? = null
    var legacyEntryPoint: String? = null
    var warningsDisabled = false

    // Fluid Scan: Look inside files for markers
    for (fileName in htmlFiles) {
        try {
            val content = assetManager.open(fileName).bufferedReader().use { it.readText() }
            
            if (content.contains("id=\"disablewarnings\"")) {
                warningsDisabled = true
            }

            // Look for <div class="id1">...</div>
            if (content.contains("class=\"id1\"")) {
                fluidEntryPoint = fileName
            }
        } catch (e: Exception) { continue }

        // Legacy check: filename-based
        if (fileName.contains("_$1.html")) {
            legacyEntryPoint = fileName
        }
    }

    return when {
        fluidEntryPoint != null -> 
            ShebangResult.AssetUrl("file:///android_asset/$fluidEntryPoint", false, warningsDisabled)
        legacyEntryPoint != null -> 
            ShebangResult.AssetUrl("file:///android_asset/$legacyEntryPoint", true, warningsDisabled)
        else -> 
            ShebangResult.Error(ErrorCode.NO_PRIME_SHEBANG)
    }
}

// ── Beautiful Error Page UI ──────────────────────────────────────────────────

fun buildErrorPage(code: ErrorCode, detail: String? = null): String {
    // language=HTML
    return """
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
        <style>
            :root { --bg: #050505; --accent: #ff3b3b; --surface: #111; --text: #eee; }
            body { 
                background: var(--bg); color: var(--text); font-family: 'Inter', system-ui, sans-serif;
                margin: 0; display: flex; align-items: center; justify-content: center; height: 100vh;
                overflow: hidden;
            }
            .container {
                width: 85%; max-width: 400px; padding: 40px 20px;
                background: linear-gradient(145deg, #161616, #0c0c0c);
                border: 1px solid #222; border-radius: 24px; text-align: center;
                box-shadow: 0 30px 60px rgba(0,0,0,0.5);
                animation: slideUp 0.6s cubic-bezier(0.2, 0.8, 0.2, 1);
            }
            @keyframes slideUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
            .icon { 
                font-size: 48px; margin-bottom: 20px; color: var(--accent);
                text-shadow: 0 0 20px rgba(255,59,59,0.3);
            }
            .code-badge {
                display: inline-block; padding: 4px 12px; background: rgba(255,59,59,0.1);
                color: var(--accent); border: 1px solid rgba(255,59,59,0.3);
                border-radius: 100px; font-size: 10px; font-weight: 800; letter-spacing: 1px; margin-bottom: 16px;
            }
            h1 { font-size: 22px; font-weight: 700; margin: 0 0 12px 0; letter-spacing: -0.5px; }
            p { font-size: 14px; color: #888; line-height: 1.6; margin-bottom: 24px; }
            .detail-box {
                background: #000; padding: 12px; border-radius: 12px; 
                font-family: monospace; font-size: 11px; color: #555;
                text-align: left; word-break: break-all; border: 1px solid #1a1a1a;
            }
            .glitch-footer { margin-top: 30px; font-size: 10px; color: #333; font-weight: 600; letter-spacing: 2px; }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="icon">⚠</div>
            <div class="code-badge">CORE ERROR ${code.code}</div>
            <h1>${code.title}</h1>
            <p>${code.message}</p>
            ${if (detail != null) "<div class='detail-box'>$detail</div>" else ""}
            <div class="glitch-footer">B0MK CORE // FLUID_MODE</div>
        </div>
    </body>
    </html>
    """.trimIndent()
}

// ── WebView Implementation ────────────────────────────────────────────────────

@Composable
fun WebViewScreen(result: ShebangResult) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        canGoBack = view?.canGoBack() == true
                        
                        // Handle Warnings Injection
                        if (result is ShebangResult.AssetUrl && !result.warningsDisabled) {
                            if (result.isLegacy) {
                                val warningJs = """
                                    (function() {
                                        var warn = document.createElement('div');
                                        warn.style.position = 'fixed';
                                        warn.style.bottom = '10px';
                                        warn.style.left = '10px';
                                        warn.style.right = '10px';
                                        warn.style.background = '#ffeb3b';
                                        warn.style.color = '#000';
                                        warn.style.padding = '12px';
                                        warn.style.fontSize = '12px';
                                        warn.style.fontWeight = 'bold';
                                        warn.style.borderRadius = '8px';
                                        warn.style.zIndex = '999999';
                                        warn.style.border = '2px solid #000';
                                        warn.innerText = 'B0MK CORE WARNING - OUTDATED NAMING SCHEME, PLEASE CHANGE TO THE "Fluid" NAMING SCHEME.';
                                        document.body.appendChild(warn);
                                        console.warn('B0MK CORE: Outdated naming scheme detected.');
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(warningJs, null)
                            }
                        }
                    }

                    override fun onReceivedError(view: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                        if (req?.isForMainFrame != true) return
                        val html = buildErrorPage(ErrorCode.ASSET_IO_ERROR, err?.description?.toString())
                        view?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }
                }

                when (result) {
                    is ShebangResult.AssetUrl -> loadUrl(result.url)
                    is ShebangResult.Error -> {
                        val html = buildErrorPage(result.code, result.detail)
                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }
                }
            }
        }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = resolveEntryPoint(assets)
        setContent { WebViewScreen(result) }
    }
}
