package com.occaecat.hush.devices

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Hush's EQ library: named curves kept on the phone, for any device. A curve belongs to an equalizer layout
 * ([Equalizer.layout]: "sony-6", "phone-10"…) and stores dB per band, so it only shows for equalizers that can
 * play it back exactly.
 */
object EqLibrary {
    data class Curve(val id: String, val name: String, val layout: String, val levels: List<Int>, val source: String? = null)

    private val _items = MutableStateFlow<List<Curve>>(emptyList())
    val items: StateFlow<List<Curve>> = _items
    private var file: File? = null
    private var writer: com.occaecat.hush.devices.DiskWriter? = null

    /** Called before a curve is deleted, so automation that points at it can let go. */
    var onDelete: ((String) -> Unit)? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "eq-library.json")
        file = f
        writer = com.occaecat.hush.devices.DiskWriter(f)
        if (f.exists()) runCatching { _items.value = decode(JSONObject(f.readText())) }
    }

    fun forLayout(layout: String) = _items.value.filter { it.layout == layout }

    fun save(name: String, layout: String, levels: List<Int>, source: String? = null, id: String = UUID.randomUUID().toString()): Curve {
        val c = Curve(id, name.trim().ifEmpty { "My EQ" }, layout, levels, source)
        update { it.filterNot { e -> e.id == id } + c }
        return c
    }

    fun rename(id: String, name: String) = update { l -> l.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it } }

    fun delete(id: String) {
        onDelete?.invoke(id)
        update { l -> l.filterNot { it.id == id } }
    }

    private fun update(change: (List<Curve>) -> List<Curve>) {
        _items.update(change)
        writer?.write(encode(_items.value).toString())
    }

    // ---- share codes ----

    private const val CODE_V2 = "HUSHEQ2:"
    /** Hush 0.15–0.17 codes: the XM4's six bands, dB, no layout. */
    private const val CODE_V1 = "HUSHEQ1:"

    /** "HUSHEQ2:phone-10:3,2,0,…|Name": the layout, dB per band, then the name. */
    fun shareCode(c: Curve) = CODE_V2 + c.layout + ":" + c.levels.joinToString(",") + "|" + c.name

    /** A code for [layout] with [bandCount] bands within [range]; null when it doesn't fit. */
    fun parseCode(text: String, layout: String, bandCount: Int, range: IntRange): Pair<String, List<Int>>? {
        val t = text.trim()
        val (codeLayout, body) = when {
            t.startsWith(CODE_V2) -> t.removePrefix(CODE_V2).let { it.substringBefore(':') to it.substringAfter(':', "") }
            t.startsWith(CODE_V1) -> "sony-6" to t.removePrefix(CODE_V1)
            else -> return null
        }
        if (codeLayout != layout || body.isEmpty()) return null
        val db = body.substringBefore('|').split(',').map { it.trim().toIntOrNull() ?: return null }
        if (db.size != bandCount || db.any { it !in range }) return null
        return body.substringAfter('|', "Imported EQ") to db
    }

    // ---- JSON (also for backups) ----

    fun exportJson(): JSONObject = encode(_items.value)

    fun importJson(o: JSONObject) = update { decode(o) }

    private fun encode(l: List<Curve>) = JSONObject().put("curves", JSONArray(l.map { c ->
        JSONObject().put("id", c.id).put("name", c.name).put("layout", c.layout).put("levels", JSONArray(c.levels))
            .put("source", c.source ?: JSONObject.NULL)
    }))

    private fun decode(o: JSONObject): List<Curve> {
        val a = o.optJSONArray("curves") ?: return emptyList()
        return List(a.length()) { i ->
            val e = a.getJSONObject(i)
            val lv = e.getJSONArray("levels")
            Curve(e.getString("id"), e.getString("name"), e.getString("layout"), List(lv.length()) { lv.getInt(it) },
                e.optString("source").takeIf { it.isNotEmpty() && it != "null" })
        }
    }
}
