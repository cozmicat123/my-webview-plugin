package com.example.webviewplugin

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.discord.utilities.rest.RestAPI

@AliucordPlugin
class WebViewPlugin : Plugin() {

    override fun start(context: Context) {
        commands.registerCommand(
            "webview",
            "Opens Discord in an in-app WebView"
        ) {
            val token = getDiscordToken()
            if (token == null) {
                return@registerCommand CommandsAPI.CommandResult("Could not find Discord token", null, false)
            }

            Utils.mainThread.post {
                val dialog = WebViewDialog(Utils.appActivity, "https://discord.com/app", token)
                dialog.show()
            }

            CommandsAPI.CommandResult("Opening Discord WebView...", null, false)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }

    private fun getDiscordToken(): String? {
        return try {
            RestAPI.AppHeadersProvider.INSTANCE.authToken
        } catch (e: Throwable) {
            logger.error("Failed to grab token via RestAPI", e)
            null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
class WebViewDialog(context: Context, private val targetUrl: String, private val token: String) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie("https://discord.com", "token=$token; Path=/; Secure; SameSite=None")
            setCookie("https://discord.com", "tokens=$token; Path=/; Secure; SameSite=None")
        }

        val webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val js = """
                        (function() {
                            try {
                                localStorage.setItem('token', '\"$token\"');
                                localStorage.setItem('tokens', '\"$token\"');
                            } catch(e) {}
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false
            }
            // FIXED: explicitly referring to targetUrl to avoid WebView.getUrl() scoping bug
            loadUrl(targetUrl)
        }

        val closeBtn = Button(context).apply {
            text = "✕"
            setOnClickListener { dismiss() }
            layoutParams = FrameLayout.LayoutParams(120, 120).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                topMargin = 32
                rightMargin = 32
            }
        }

        root.addView(webView)
        root.addView(closeBtn)
        setContentView(root)
        
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
