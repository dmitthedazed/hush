package com.occaecat.hush.features

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Sony's support "Concierge" (`POST https://concierge.support.sony.net/api/v1/browseSolutions`), as Sound Connect's
 * ConciergeWrapper / support_sdk call it: partnerInfo, appInfo with a metadata block (actionType HELP / DIRECT,
 * screenId, directId, targetModelName), environmentInfo and the product. The answer is `{"url": …}`, a one-time
 * link that redirects to the right page (a tutorial, a support article or the model's solutions page).
 */
object Concierge {
    private const val ENDPOINT = "https://concierge.support.sony.net/api/v1/browseSolutions"
    private const val PARTNER_ID = "08cd1c37-3f86-3263-a94b-d5c1e28cc343"

    /** Blocking; call from Dispatchers.IO. Null when Sony has nothing for this model. */
    fun resolve(model: String, actionType: String, screen: String, directId: String?, firmware: String?, locale: Locale = Locale.getDefault()): String? {
        if (model.contains("Jabra", ignoreCase = true)) return null
        val country = locale.country.ifEmpty { "US" }
        val meta = JSONObject().put("actionType", actionType).put("screenId", screen)
            .put("deviceBtConnectStatus", "CONNECTED").put("targetModelName", model)
        directId?.let { meta.put("directId", it) }
        val body = JSONObject()
            .put("partnerInfo", JSONObject().put("partnerId", PARTNER_ID))
            .put("appInfo", JSONObject()
                .put("name", "com.sony.songpal.mdr").put("version", "13.3.0").put("sdkVer", "2.16.2")
                .put("language", locale.language).put("country", country.uppercase()).put("build_platform", "Android")
                .put("metadata", meta))
            .put("environmentInfo", JSONObject()
                .put("country", country).put("language", locale.language)
                .put("modelName", android.os.Build.MODEL).put("os_version", android.os.Build.VERSION.RELEASE))
            .put("productSet", JSONObject().put("products", JSONArray().put(JSONObject()
                .put("modelName", model)
                .put("metadata", JSONObject().put("connected", "true").apply { firmware?.let { put("fwVer", it) } }))))
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        return try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) return null
            val text = c.inputStream.bufferedReader().readText().trim()
            if (!text.startsWith("{")) null else JSONObject(text).optString("url").ifEmpty { null }
        } finally {
            c.disconnect()
        }
    }

    /** Sony's full Help Guide for a model ("WH-1000XM4" → helpguide.sony.net/mdr/wh1000xm4/v1/<lang>/). */
    fun helpGuideUrl(model: String, locale: Locale = Locale.getDefault()): String {
        if (model.contains("Jabra", ignoreCase = true)) return Help.jabraSupportUrl(model)
        val slug = model.lowercase().replace("-", "").replace(" ", "")
        val lang = when (locale.language) {
            "en", "ru", "de", "fr", "es", "it", "nl", "pt", "sv", "fi", "ja", "ko", "tr", "pl", "cs", "uk" -> locale.language
            "zh" -> if (locale.country == "TW" || locale.country == "HK") "zh-tw" else "zh-cn"
            else -> "en"
        }
        return "https://helpguide.sony.net/mdr/$slug/v1/$lang/index.html"
    }
}
