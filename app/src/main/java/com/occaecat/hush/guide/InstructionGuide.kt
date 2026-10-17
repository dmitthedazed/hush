package com.occaecat.hush.guide

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Sound Connect's instruction guide ("PTOUR" pages). The headset lists the GuidanceCategory codes it has
 * (DeviceInfo INSTRUCTION_GUIDE); for each, Sony's Concierge service (`/api/v1/browseSolutions`, directId
 * `PTOUR_<code>`, the same request Sound Connect's pj/v + ConciergeWrapper make) answers with a link that redirects
 * to a small helpguide.sony.net tutorial page: a title, an animated GIF (with a dark variant) and either a sentence
 * or a table of gestures. Those pages are parsed here and shown natively.
 */
object InstructionGuide {

    data class Gesture(val icon: String, val gesture: String, val actions: List<String>)

    data class Topic(
        val code: String,
        val title: String,
        val description: String?,
        val gif: String?,
        val gifDark: String?,
        val gestures: List<Gesture>,
        val pageUrl: String,
    )


    /** Every PTOUR tail Sound Connect knows (InstructionGuideContents), tried when the headset doesn't list its own. */
    private val ALL = listOf(0x00, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80, 0x90, 0xA0)

    private val cache = mutableMapOf<String, List<Topic>>()

    /** MAIN_BODY_OPERATION has two pages (40 and 41); every other category maps to its own code. */
    private fun tails(code: Int) = if (code == 0x40) listOf("40", "41") else listOf("%02X".format(code))

    suspend fun load(model: String, categories: List<Int>?, firmware: String?): List<Topic> = withContext(Dispatchers.IO) {
        val locale = Locale.getDefault()
        val key = "$model/${locale.toLanguageTag()}"
        cache[key]?.let { return@withContext it }
        val codes = categories?.takeIf { it.isNotEmpty() } ?: ALL
        val topics = coroutineScope {
            codes.flatMap(::tails).map { tail -> async { runCatching { topic(model, tail, firmware, locale) }.getOrNull() } }.awaitAll()
        }.filterNotNull()
        if (topics.isNotEmpty()) cache[key] = topics
        topics
    }

    private fun topic(model: String, tail: String, firmware: String?, locale: Locale): Topic? {
        val link = resolve(model, "PTOUR_$tail", firmware, locale) ?: return null
        val (finalUrl, page) = get(link) ?: return null
        return parse(tail, finalUrl, page)
    }

    private fun resolve(model: String, directId: String, firmware: String?, locale: Locale) =
        com.occaecat.hush.features.Concierge.resolve(model, "DIRECT", "DASHBOARD", directId, firmware, locale)

    /** GET following redirects (Concierge → helpguide.sony.net); null on 404, which models without the page get. */
    private fun get(url: String): Pair<String, String>? {
        var u = URL(url)
        repeat(5) {
            val c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            try {
                when (c.responseCode) {
                    in 300..399 -> u = URL(u, c.getHeaderField("Location") ?: return null)
                    200 -> return u.toString() to c.inputStream.bufferedReader().readText()
                    else -> return null
                }
            } finally {
                c.disconnect()
            }
        }
        return null
    }

    private fun text(html: String) = Html.fromHtml(html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""), Html.FROM_HTML_MODE_COMPACT)
        .toString().replace("\uFFFC", "").replace(Regex("\\s+"), " ").trim()

    internal fun parse(code: String, pageUrl: String, html: String): Topic? {
        val base = URL(pageUrl)
        fun abs(src: String) = URL(base, src.replace(" ", "%20")).toString()
        val title = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.let(::text)
            ?.takeIf { it.isNotEmpty() } ?: return null
        val picture = Regex("<picture>(.*?)</picture>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1).orEmpty()
        val gif = Regex("<img[^>]*src=\"([^\"]+)\"").find(picture)?.groupValues?.get(1)?.let(::abs)
        val gifDark = Regex("<source[^>]*srcset=\"([^\"]+)\"[^>]*prefers-color-scheme: dark").find(picture)?.groupValues?.get(1)?.let(::abs)
        val description = Regex("<p(?:\\s[^>]*)?>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.let(::text)?.ifEmpty { null }
        val gestures = Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(html).mapNotNull { row ->
            val r = row.groupValues[1]
            val icon = Regex("<img[^>]*src=\"[^\"]*/([^/\"]+)\"").find(r)?.groupValues?.get(1).orEmpty()
            val gesture = Regex("<dt[^>]*>(.*?)</dt>", RegexOption.DOT_MATCHES_ALL).find(r)?.groupValues?.get(1)?.let(::text) ?: return@mapNotNull null
            val actions = Regex("<li[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL).findAll(r).map { text(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()
            Gesture(icon, gesture, actions)
        }.toList()
        return Topic(code, title, description, gif, gifDark, gestures, pageUrl)
    }

    /** The GIF as a local file (downloaded once into the cache dir). */
    suspend fun gifFile(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".gif"
        val f = File(File(context.cacheDir, "guide").apply { mkdirs() }, name)
        if (f.length() > 0) return@withContext f
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 30_000
            try {
                if (c.responseCode != 200) return@runCatching null
                val tmp = File(f.parentFile, "$name.part")
                c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(f)
                f
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
