package com.example.examplewvapp20

import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = findPrimeShebang("_\$1.html")

        setContent {
            WebViewScreen(result.first, result.second)
        }
    }


    private fun findPrimeShebang(tag: String): Pair<String, Boolean> {

        val assetManager: AssetManager = assets

        return try {

            val files = assetManager.list("") ?: emptyArray()
            val matches = files.filter { it.endsWith(tag) }

            when (matches.size) {

                1 -> Pair("file:///android_asset/${matches.first()}", false)

                0 -> Pair(
                    """
                    <html>
                    <body style="font-family:sans-serif;text-align:center;margin-top:40%">
                    <h2>SimpleWV ERROR</h2>
                    <p>NO PRIME SHEBANG DETECTED</p>
                    <p>SimpleWV version - 2.0</p>
                    </body>
                    </html>
                    """.trimIndent(),
                    true
                )

                else -> Pair(
                    """
                    <html>
                    <body style="font-family:sans-serif;text-align:center;margin-top:40%">
                    <h2>SimpleWV ERROR</h2>
                    <p>DUBLICATE PRIME SHEBANGS</p>
                    <p>SimpleWV version - 2.0</p>
                    </body>
                    </html>
                    """.trimIndent(),
                    true
                )
            }

        } catch (e: IOException) {
            Pair("<html><body>SimpleWV ERROR</body></html>", true)
        }
    }
}

@Composable
fun WebViewScreen(content: String, isHtml: Boolean) {

    var webView: WebView? by remember { mutableStateOf(null) }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    AndroidView(
        factory = { context ->

            WebView(context).apply {

                webView = this

                val settings = settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true

                CookieManager.getInstance()
                    .setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {

                        val url = request?.url.toString()

                        if (url.startsWith("mailto:")) {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                            context.startActivity(intent)
                            return true
                        }

                        return false
                    }
                }

                if (isHtml) {
                    loadDataWithBaseURL(
                        null,
                        content,
                        "text/html",
                        "UTF-8",
                        null
                    )
                } else {
                    loadUrl(content)
                }
            }
        }
    )
}