package com.example.webviewplugin

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
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
                    WebViewDialog.newInstance(url).show(
                        (adapter.itemView.context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return@Hook, "webview_dialog"
                    )
                }
            }
        )
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
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
        fun newInstance(url: String) = WebViewDialog().apply {
            arguments = Bundle().apply { putString(ARG_URL, url) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        val url = arguments?.getString(ARG_URL) ?: return dialog

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val webView = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean = false
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
