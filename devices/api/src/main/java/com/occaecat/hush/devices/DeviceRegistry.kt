package com.occaecat.hush.devices

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A model a brand supports, for adding a device by hand (brand → model). */
data class Model(
    /** Stable within the module: "wh-1000xm4". */
    val id: String,
    val name: String,
    val transport: Transport,
    /** How to get it ready, on the model's page ("Pair it in Bluetooth settings…"). */
    val howTo: String,
    /** Registered from the model alone (wired EarPods); otherwise the user picks a unit the module found. */
    val addWithoutFinding: Boolean = false,
    /** A runtime permission the module needs to find this model (BLUETOOTH_CONNECT). */
    val permission: String? = null,
    @DrawableRes val image: Int? = null,
    /** A render to download when there's no [image] (cached by the model id). */
    val imageUrl: String? = null,
    /** Shown under the name ("Tested", "Untested: features depend on the model"). */
    val note: String? = null,
    /** Section on the brand page ("Bluetooth", "Wired headphones"); models without one are listed first. */
    val group: String? = null,
)

/** A unit a module can see right now (paired, plugged in), registered or not. */
data class Candidate(
    val moduleId: String,
    val modelId: String,
    val name: String,
    val transport: Transport,
    /** Bluetooth address, or null when the model is the identity (one pair of wired EarPods). */
    val address: String? = null,
) {
    val key get() = "$moduleId:${address ?: modelId}"
}

/**
 * The devices the user added to Hush. They stay in the device switcher whether or not they're connected; modules
 * only say which ones are live ([DeviceModule.devices]) and what they can find ([DeviceModule.candidates]).
 */
object DeviceRegistry {
    data class Entry(
        val key: String,
        val moduleId: String,
        val modelId: String,
        val name: String,
        val transport: Transport,
        val address: String?,
        val added: Long,
    )

    private val _items = MutableStateFlow<List<Entry>>(emptyList())
    val items: StateFlow<List<Entry>> = _items

    /** Found units the user said "not now" to: no automatic prompt for them again. */
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed

    private var file: File? = null
    private var writer: DiskWriter? = null

    /** True on the first start with a registry: callers move what older versions remembered into it. */
    var fresh = false
        private set

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "devices.json")
        file = f
        writer = DiskWriter(f)
        fresh = !f.exists()
        if (f.exists()) runCatching {
            val o = JSONObject(f.readText())
            val a = o.optJSONArray("devices") ?: JSONArray()
            // One broken entry costs that device only, not the whole list.
            _items.value = (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val e = a.getJSONObject(i)
                    Entry(
                        e.getString("key"), e.getString("module"), e.getString("model"), e.getString("name"),
                        runCatching { Transport.valueOf(e.getString("transport")) }.getOrDefault(Transport.BLUETOOTH),
                        e.optString("address").takeIf { it.isNotEmpty() && it != "null" }, e.optLong("added"),
                    )
                }.onFailure { Log.w("Hush", "skipping a broken device entry", it) }.getOrNull()
            }
            val d = o.optJSONArray("dismissed") ?: JSONArray()
            _dismissed.value = (0 until d.length()).mapNotNull { d.optString(it).takeIf(String::isNotEmpty) }.toSet()
        }.onFailure {
            // Keep the unreadable file aside: the next save would overwrite the only copy.
            Log.w("Hush", "devices.json is unreadable, kept as devices.json.bak", it)
            runCatching { f.copyTo(File(f.parentFile, "devices.json.bak"), overwrite = true) }
        }
    }

    /** Registers what older versions remembered, once; later starts leave the list to the user. */
    fun migrate(remembered: List<Candidate>) {
        if (!fresh) return
        fresh = false
        remembered.forEach(::add)
        save()
    }

    fun contains(key: String) = _items.value.any { it.key == key }

    fun add(c: Candidate) {
        if (contains(c.key)) return
        _items.update { it + Entry(c.key, c.moduleId, c.modelId, c.name, c.transport, c.address, System.currentTimeMillis()) }
        _dismissed.update { it - c.key }
        save()
    }

    fun remove(key: String) {
        _items.update { l -> l.filterNot { it.key == key } }
        save()
    }

    fun dismiss(key: String) {
        _dismissed.update { it + key }
        save()
    }

    private fun save() {
        val w = writer ?: return
        val o = JSONObject()
            .put("devices", JSONArray(_items.value.map { e ->
                JSONObject().put("key", e.key).put("module", e.moduleId).put("model", e.modelId).put("name", e.name)
                    .put("transport", e.transport.name).put("address", e.address ?: JSONObject.NULL).put("added", e.added)
            }))
            .put("dismissed", JSONArray(_dismissed.value.toList()))
        w.write(o.toString())
    }
}
