package com.occaecat.hush.features

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sony support articles, reached the way Sound Connect reaches them: a Concierge request with a screen id (HELP)
 * or a direct article id (DIRECT), answered with a one-time link that opens in the browser. The XM4 gets all of
 * these; Sony's site blocks non-browser clients, so they open in the browser rather than in-app.
 */
object Help {
    enum class Article(val title: String, val subtitle: String, val action: String, val screen: String, val directId: String?) {
        TROUBLESHOOTING("Troubleshooting", "Sony's solutions for this model", "HELP", "dashboard", null),
        CONNECTION("Connection problems", "When the headphones won't connect or keep dropping", "HELP", "connectionErrorDialog", null),
        MULTIPOINT("Using two devices", "How multipoint and switching between sources work", "DIRECT", "dashboard", "2DEVICE_MULTIPOINT_GUIDE"),
        FIRMWARE("Updating the firmware", "Which phones can update and what to watch out for", "DIRECT", "dashboard", "HPC_FW_UPDATE_COMPATIBLE_INFO"),
        APP_GUIDE("Sound Connect help guide", "Sony's guide to every app setting", "DIRECT", "dashboard", "SETTING_COMPATIBLE_DEVICE_LIST"),
    }

    suspend fun open(context: Context, model: String, firmware: String?, article: Article): Boolean {
        val url = withContext(Dispatchers.IO) {
            runCatching { Concierge.resolve(model, article.action, article.screen, article.directId, firmware) }.getOrNull()
        }
        if (url == null) {
            Toast.makeText(context, "Couldn't reach Sony's support", Toast.LENGTH_SHORT).show()
            return false
        }
        return openUrl(context, url)
    }

    fun openHelpGuide(context: Context, model: String) = openUrl(context, Concierge.helpGuideUrl(model))

    fun openUrl(context: Context, url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
