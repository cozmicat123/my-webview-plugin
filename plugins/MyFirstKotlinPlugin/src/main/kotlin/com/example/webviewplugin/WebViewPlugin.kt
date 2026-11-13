package com.example.webviewplugin

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
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
            "Opens a URL in an in-app WebView"
        ) { ctx ->
            val raw = ctx.rawContent.trim()
            
            // Extract the actual URL from the raw string using Regex
            val pattern = java.util.regex.Pattern.compile("https?://\\S+")
            val matcher = pattern.matcher(raw)
            val url = if (matcher.find()) matcher.group() else null

            if (url.isNullOrEmpty()) {
                return@registerCommand CommandsAPI.CommandResult("Usage: /webview <url>", null, false)
            }

            val token = getDiscordToken()
            if (token == null) {
                return@registerCommand CommandsAPI.CommandResult("Could not find Discord token", null, false)
            }

            WebViewDialog.newInstance(url, token).show(
                Utils.appActivity.supportFragmentManager, "webview_dialog"
            )

            CommandsAPI.CommandResult("Opening WebView...", null, false)
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
class WebViewDialog : DialogFragment() {

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TOKEN = "token"

        fun newInstance(url: String, token: String) = WebViewDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_URL, url)
                putString(ARG_TOKEN, token)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        val url = arguments?.getString(ARG_URL) ?: return dialog
        val token = arguments?.getString(ARG_TOKEN) ?: return dialog

        val root = FrameLayout(requireContext()).apply {
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

        val webView = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
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
            loadUrl(url)
        }

        val closeBtn = android.widget.Button(requireContext()).apply {
            text = "✕"
            setOnClickListener { dismiss() }
            layoutParams = FrameLayout.LayoutParams(120, 120).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                topMargin = 16
                rightMargin = 16
            }
        }

        root.addView(webView)
        root.addView(closeBtn)
        dialog.setContentView(root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return dialog
    }
}
