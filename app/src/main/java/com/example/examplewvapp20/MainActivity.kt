package com.yoursite.app

import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class Main : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview_compontent)
        configureWebView()

        if (savedInstanceState == null) {
            val targetFile = findTaggedHtml("_$1.html")
            webView.loadUrl("file:///android_asset/$targetFile")
        }
    }

    private fun configureWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("mailto:")) {
                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(url)))
                    return true
                }
                return false
            }
        }
    }

    /**
     * Scans the assets folder for a filename containing the specified tag.
     * Defaults to "index.html" if no match is found.
     */
    private fun findTaggedHtml(tag: String): String {
        val assetManager: AssetManager = assets
        return try {
            val files = assetManager.list("")
            files?.firstOrNull { it.endsWith(tag) } ?: "index.html"
        } catch (e: IOException) {
            e.printStackTrace()
            "index.html"
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}