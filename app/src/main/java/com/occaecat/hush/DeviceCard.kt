package com.occaecat.hush

import android.content.Context
import android.net.Uri
import com.occaecat.hush.mdr.AmbientMode
import org.json.JSONArray
import org.json.JSONObject

/** The part of [HeadphonesState] that the device card, the tile and the notification show. */
data class CardSnapshot(
    val address: String?,
    val name: String?,
    val connected: Boolean,
    val connecting: Boolean,
    val battery: Int?,
    val charging: Boolean,
    val mode: AmbientMode?,
) {
    constructor(s: HeadphonesState) : this(
        address = s.address,
        name = s.displayName,
        connected = s.phase == Phase.CONNECTED,
        connecting = s.phase == Phase.CONNECTING || s.phase == Phase.RECONNECTING,
        battery = s.battery,
        charging = s.charging,
        mode = s.ncAsm?.mode,
    )
}

/**
 * ColorOS "My devices" card, served by [DeviceCardProvider].
 *
 * My Devices (com.heytap.mydevices 17.4.10) finds third-party device apps through a content provider with the
 * `oplus.devicecards.action.DEVICE_INFO` intent filter, queries `device_info_table_new`, and parses the
 * `device_data` column with Gson into `com.oplus.mydevices.domain.entities.compat.DeviceModel`. Only packages in
 * its built-in allowlist are read; the Xposed hook in [com.occaecat.hush.xposed.HushModule] adds ours.
 */
object DeviceCard {
    const val AUTHORITY = "com.occaecat.hush.devicecard"
    const val TABLE = "device_info_table_new"
    val URI: Uri = Uri.parse("content://$AUTHORITY/$TABLE")

    /** Provider `call` method behind the card's mode shortcuts; `extra` is an [AmbientMode] name. */
    const val METHOD_MODE = "hush_mode"

    /** Provider `call` returning connected / mode / battery / address, for the Bluetooth settings hook. */
    const val METHOD_STATE = "hush_state"

    /** Bumped on every change so the hook can tell an update from a no-op refresh. */
    @Volatile var revision = 0L
        private set

    /**
     * Same id My Devices gives the headset's own Bluetooth card (md5 of the upper-case MAC,
     * OplusBluetoothAdapterWrapper), so the hook can mark it linked and the Bluetooth card steps aside.
     */
    fun deviceId(address: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(address.uppercase().toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Id used by 0.2.0 builds, removed from My Devices by the hook. */
    fun legacyDeviceId(address: String) = "hush_" + address.replace(":", "").lowercase()

    fun publish(context: Context, snapshot: CardSnapshot) {
        revision++
        context.contentResolver.notifyChange(URI, null)
    }

    fun deviceData(s: CardSnapshot, now: Long = System.currentTimeMillis()): JSONObject {
        val address = s.address.orEmpty()
        val state = if (s.connected) "CONNECTED" else "DISCONNECTED"
        val batteries = JSONArray()
        val levels = JSONArray()
        s.battery?.takeIf { it in 0..100 }?.let {
            batteries.put(JSONObject().put("batteryType", "SINGLE").put("value", it).put("charge", s.charging))
            levels.put(it)
        }
        return JSONObject()
            .put("mDeviceId", deviceId(address))
            .put("mDeviceName", s.name ?: "Headphones")
            .put("mMacAddress", address)
            // HEADSET makes My Devices drop mDeviceIcon and use the picture below.
            .put("mDeviceType", "HEADSET")
            .put("iconUrl", ICON)
            .put("mIconUrl", ICON)
            .put("mBatteryInfoList", batteries)
            .put("mBatteryList", levels)
            .put("connection", JSONObject()
                .put("connectState", state)
                .put("coordinationState", "DEFAULT")
                .put("lastConnectTime", if (s.connected) now else 0)
                .put("lastDisconnectTime", if (s.connected) 0 else now))
            .put("mConnectState", state)
            .put("mAuthority", AUTHORITY)
            .put("isActive", s.connected)
            .put("cardStyle", 1)
            .put("timestamp", now)
            .put("versionCode", 1L)
            .put("feature", 0)
            .put("linkageVersion", 0)
            .put("autoSwitch", false)
            .put("shortcuts", if (s.connected) shortcuts(s.mode) else JSONArray())
            .put("revision", revision)
    }

    /**
     * Card buttons. componentType 2 (provider) makes My Devices call `call(content://AUTHORITY, method, …)` with
     * `extra` in the bundle (remotecards/utils/CardsUtils.b); type 1 would need a signature-only permission.
     */
    private fun shortcuts(current: AmbientMode?): JSONArray {
        val out = JSONArray()
        for ((mode, label) in listOf(
            AmbientMode.NOISE_CANCELLING to "Noise cancelling",
            AmbientMode.AMBIENT to "Ambient sound",
            AmbientMode.OFF to "Off",
        )) {
            val extra = JSONObject().put("authority", AUTHORITY).put("method", METHOD_MODE).put("extra", mode.name)
            out.put(JSONObject()
                .put("shortcutId", "hush_mode_" + mode.name.lowercase())
                .put("shortcutName", label)
                .put("action", "")
                .put("links", "")
                .put("componentType", 2)
                .put("componentExtra", extra.toString())
                .put("style", 0)
                .put("state", if (mode == current) 1 else 0))
        }
        return out
    }

    private const val ICON = "android.resource://com.occaecat.hush/drawable/card_xm4"
}
