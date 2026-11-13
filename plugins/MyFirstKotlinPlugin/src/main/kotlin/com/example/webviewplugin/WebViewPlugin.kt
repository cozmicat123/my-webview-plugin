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
            "Launches Aegires seamlessly"
        ) {
            val token = getDiscordToken()
            if (token == null) {
                return@registerCommand CommandsAPI.CommandResult("Could not find Discord token", null, false)
            }

            val intent = context.packageManager.getLaunchIntentForPackage("io.github.aegires")
            
            if (intent != null) {
                intent.putExtra("DISCORD_TOKEN", token)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                val options = ActivityOptions.makeScaleUpAnimation(
                    Utils.appActivity.window.decorView,
                    Utils.appActivity.window.decorView.width / 2,
                    Utils.appActivity.window.decorView.height / 2,
                    0, 0
                ).toBundle()

                Utils.appActivity.startActivity(intent, options)
                return@registerCommand CommandsAPI.CommandResult("Launching Aegires...", null, false)
            } else {
                return@registerCommand CommandsAPI.CommandResult("Aegires is not installed (io.github.aegires).", null, false)
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
