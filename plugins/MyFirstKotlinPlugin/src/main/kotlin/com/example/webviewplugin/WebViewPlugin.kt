package com.example.webviewplugin

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.chat.list.entries.MessageEntry

@AliucordPlugin
class WebViewPlugin : Plugin() {

    override fun start(context: Context) {
        patcher.patch(
            WidgetChatListAdapterItemMessage::class.java.getDeclaredMethod(
                "onLongClick", android.view.View::class.java
            ),
            Hook { param ->
                val entry = param.args[0] as? ChatListEntry ?: return@Hook
                if (entry is MessageEntry) {
                    val url = extractFirstUrl(entry.message.content) ?: return@Hook
                    val adapter = param.thisObject as WidgetChatListAdapterItemMessage
                    
                    // Safely unwrap context to find FragmentActivity
                    var ctx = adapter.itemView.context
                    while (ctx is ContextWrapper) {
                        if (ctx is FragmentActivity) break
                        ctx = ctx.baseContext
                    }
                    val activity = ctx as? FragmentActivity ?: return@Hook
                    
                    val token = getDiscordToken(activity)
                    if (token == null) {
                        Utils.showToast("Could not find Discord token")
                        return@Hook
                    }

                    WebViewDialog.newInstance(url, token).show(
                        activity.supportFragmentManager, "webview_dialog"
                    )
                }
            }
        )
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private fun getDiscordToken(context: Context): String? {
        val prefs = context.getSharedPreferences("com.discord", Context.MODE_PRIVATE)
        return prefs.getString("token", null)
            ?: prefs.getString("auth_token", null)
            ?: prefs.getString("access_token", null)
    }

    private fun extractFirstUrl(text: String): String? {
        val pattern = java.util.regex.Pattern.compile("https?://\\S+")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group() else null
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

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }
            }
            loadUrl("https://discord.com/app")
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
