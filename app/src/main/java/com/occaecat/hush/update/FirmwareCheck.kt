package com.occaecat.hush.update

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Sony's firmware-update check, as Sound Connect's AutoMagic does it (com.sony.songpal.automagic.e, kh.d):
 * `https://info.update.sony.net/<category>/<service id>/info/info.xml` — the category and service id come from the
 * headset (UPDT_GET_PARAM 2 / 3). The file is a text header (`eaid:ENC0003`, `daid:HAS0003`, `digest:<sha1>`),
 * a blank line and an AES-128-ECB body (zero padded) with a fixed key. The digest is
 * sha1(hex(sha1(body)) + serviceId + category). The body is an XML InformationFile: ApplyConditions with rules on
 * Model / FirmwareVersion / SerialNo, each with a "FW" distribution (the binary) and a "notice" (release notes,
 * one CDATA text per locale). Hush only reads it; installing stays with Sound Connect.
 */
object FirmwareCheck {

    data class Result(
        val current: String,
        val latest: String?,
        /** True when an ApplyCondition matches this headset: Sony offers it [latest]. */
        val available: Boolean,
        val sizeBytes: Long?,
        val published: String?,
        /** The "[Update details in version …]" block of the notice, in the phone's language when Sony has it. */
        val changes: String?,
        /** The whole notice (precautions, notes, how to check the version), same language. */
        val notice: String?,
        val checkedAt: Long,
    )

    private val KEY = byteArrayOf(79, -94, 121, -103, -1, -48, -117, 31, -28, -46, 96, -43, 123, 109, 60, 23)
    private const val TTL_MS = 12 * 60 * 60 * 1000L

    fun cached(context: Context, service: String): Result? =
        context.getSharedPreferences("fw", Context.MODE_PRIVATE).getString(service, null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }

    /** Uses the cached answer for up to 12 hours unless [force]. */
    suspend fun check(context: Context, category: String, service: String, model: String, firmware: String, serial: String?, force: Boolean = false): Result =
        withContext(Dispatchers.IO) {
            cached(context, service)?.takeIf { !force && it.current == firmware && System.currentTimeMillis() - it.checkedAt < TTL_MS }
                ?.let { return@withContext it }
            val raw = download("https://info.update.sony.net/$category/$service/info/info.xml")
            val xml = decode(raw, category, service)
            val result = evaluate(xml, model, firmware, serial)
            context.getSharedPreferences("fw", Context.MODE_PRIVATE).edit().putString(service, toJson(result).toString()).apply()
            result
        }

    private fun download(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.readBytes()
        } finally {
            c.disconnect()
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b)

    internal fun decode(file: ByteArray, category: String, service: String): String {
        val split = (0 until file.size - 1).firstOrNull { file[it] == '\n'.code.toByte() && file[it + 1] == '\n'.code.toByte() }
            ?: error("no header")
        val header = String(file, 0, split, Charsets.US_ASCII).lines().associate { it.substringBefore(':') to it.substringAfter(':', "") }
        var body = file.copyOfRange(split + 2, file.size)
        body = when (header["eaid"]) {
            "ENC0003" -> Cipher.getInstance("AES/ECB/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES")); doFinal(body)
            }
            "ENC0001" -> body
            else -> error("unsupported encryption ${header["eaid"]}")
        }
        header["digest"]?.let { want ->
            if (header["daid"] == "HAS0003") {
                val got = hex(sha1((hex(sha1(body)) + service + category).toByteArray()))
                if (!got.equals(want, ignoreCase = true)) error("digest mismatch")
            }
        }
        return String(body, Charsets.UTF_8).trimEnd('\u0000')
    }

    private class Condition(val order: Int, val rules: List<Map<String, String>>, val dists: List<Map<String, String>>)

    private fun evaluate(xml: String, model: String, firmware: String, serial: String?): Result {
        val conditions = mutableListOf<Condition>()
        var published: String? = null
        val p = Xml.newPullParser().apply { setInput(xml.reader()) }
        var rules = mutableListOf<Map<String, String>>()
        var dists = mutableListOf<Map<String, String>>()
        var order = 0
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) {
                val attrs = (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }
                when (p.name) {
                    "InformationFile" -> published = attrs["LastUpdate"]
                    "ApplyCondition" -> { order = attrs["ApplyOrder"]?.toIntOrNull() ?: 0; rules = mutableListOf(); dists = mutableListOf() }
                    "Rule" -> rules += attrs
                    "Distribution" -> dists += attrs
                }
            } else if (p.eventType == XmlPullParser.END_TAG && p.name == "ApplyCondition") {
                conditions += Condition(order, rules, dists)
            }
        }
        val facts = mapOf("Model" to model, "FirmwareVersion" to firmware, "SerialNo" to (serial ?: "0"))
        val match = conditions.sortedBy { it.order }.firstOrNull { c -> c.rules.all { rule(it, facts) } }
        fun fwVersion(c: Condition) = c.dists.firstOrNull { it["ID"] == "FW" }?.get("Version") ?: "0"
        val offered = match ?: conditions.maxWithOrNull { a, b -> compare(fwVersion(a), fwVersion(b)) }
        val fw = offered?.dists?.firstOrNull { it["ID"] == "FW" || it["InstallType"] == "binary" }
        val latest = fw?.get("Version")
        val noticeUrl = offered?.dists?.firstOrNull { it["InstallType"] == "notice" }?.get("URI")
        val notice = noticeUrl?.let { runCatching { noticeText(String(download(it), Charsets.UTF_8)) }.getOrNull() }
        val available = match != null && latest != null && compare(latest, firmware) > 0
        return Result(firmware, latest, available, fw?.get("Size")?.toLongOrNull(), published,
            notice?.let { changesOf(it, latest) }, notice, System.currentTimeMillis())
    }

    private fun rule(r: Map<String, String>, facts: Map<String, String>): Boolean {
        val have = facts[r["Key"]] ?: return true // unknown keys don't block, as with Sound Connect's optional rules
        val want = r["Value"] ?: return true
        val c = if (r["Key"] == "Model") have.compareTo(want) else compare(have, want)
        return when (r["Operator"]) {
            "Equal" -> c == 0
            "LessThan" -> c < 0
            "LessThanEqual" -> c <= 0
            "GreaterThan" -> c > 0
            "GreaterThanEqual" -> c >= 0
            "Include" -> have.contains(want)
            "Exclude" -> !have.contains(want)
            "EndWith" -> have.endsWith(want)
            else -> true
        }
    }

    /** Dotted numeric compare ("3.0.10" > "3.0.9"); plain numbers (serials) compare as numbers. */
    internal fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toLongOrNull() ?: 0 }
        val y = b.split('.').map { it.toLongOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** The notice's text for the phone's language (Sony keys them by country name). */
    private fun noticeText(xml: String): String? {
        val texts = Regex("<Notice Locale=\"([^\"]+)\">\\s*<Text><!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).associate { it.groupValues[1] to it.groupValues[2].trim() }
        val l = Locale.getDefault()
        val want = when (l.language) {
            "ru" -> "Russia"; "ja" -> "Japan"; "fr" -> "France"; "de" -> "Germany"; "es" -> "Spain"; "it" -> "Italy"
            "pt" -> if (l.country == "BR") "Brazil" else "Portugal"; "nl" -> "Netherlands"; "sv" -> "Sweden"; "fi" -> "Finland"
            "zh" -> if (l.country == "TW" || l.country == "HK") "Taiwan" else "China"; "ko" -> "Korea"; "tr" -> "Turkey"
            else -> "UK"
        }
        return texts[want] ?: texts["UK"] ?: texts.values.firstOrNull()
    }

    /** The bracketed block that names the version ("[Update details in version 3.0.1]") up to the next heading. */
    private fun changesOf(notice: String, version: String?): String? {
        val lines = notice.lines()
        val start = lines.indexOfFirst { it.trim().startsWith("[") && (version == null || it.contains(version)) }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { lines[it].trim().startsWith("[") } ?: lines.size
        return lines.subList(start + 1, end).joinToString("\n").trim().ifEmpty { null }
    }

    private fun toJson(r: Result) = JSONObject().put("current", r.current).put("latest", r.latest).put("available", r.available)
        .put("size", r.sizeBytes).put("published", r.published).put("changes", r.changes).put("notice", r.notice).put("checkedAt", r.checkedAt)

    private fun fromJson(o: JSONObject) = Result(
        o.getString("current"), o.optString("latest").ifEmpty { null }, o.optBoolean("available"),
        if (o.has("size") && !o.isNull("size")) o.getLong("size") else null, o.optString("published").ifEmpty { null },
        o.optString("changes").ifEmpty { null }, o.optString("notice").ifEmpty { null }, o.getLong("checkedAt"),
    )
}
