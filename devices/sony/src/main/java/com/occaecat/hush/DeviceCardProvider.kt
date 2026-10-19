package com.occaecat.hush

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.util.Log
import com.occaecat.hush.mdr.AmbientMode

/**
 * Data source of the ColorOS "My devices" card (see [DeviceCard]).
 *
 * Besides `query`, My Devices calls into the provider (core/connect/AppDeviceService): `connect`, `disconnect`,
 * `delete_device`, `card_show` / `card_hide`, each with a `callBinder` that expects a
 * `com.heytap.deviceinfo.DeviceAppCallbackInterface` reply; result_code 100 means success.
 */
class DeviceCardProvider : ContentProvider() {

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val controller get() = HeadphonesController.get(context!!)

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        requireHost()
        val columns = projection ?: COLUMNS
        val cursor = MatrixCursor(columns, 1)
        cursor.setNotificationUri(context!!.contentResolver, DeviceCard.URI)
        // Touching the controller also starts its A2DP watcher, so a cold query reconnects a headset that is up.
        val snapshot = CardSnapshot(controller.state.value)
        val address = snapshot.address ?: return cursor
        if (context!!.getSharedPreferences("hush", 0).getBoolean(PREF_HIDDEN, false)) return cursor
        val id = DeviceCard.deviceId(address)
        if (selection != null && selectionArgs?.firstOrNull() !in setOf(id, address, DeviceCard.AUTHORITY)) return cursor
        cursor.addRow(columns.map {
            when (it) {
                "device_id" -> id
                "device_mac" -> address
                "device_data" -> DeviceCard.deviceData(snapshot).toString()
                "authority" -> DeviceCard.AUTHORITY
                else -> null
            }
        })
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        requireHost()
        if (method == DeviceCard.METHOD_STATE) return runOnMainBundle { stateBundle() }
        Log.i(TAG, "call $method arg=$arg")
        val callback = extras?.getBinder("callBinder")
        val ok = runOnMain {
            when (method) {
                "connect" -> {
                    unhide()
                    controller.connectSaved()
                }
                "disconnect" -> { controller.disconnect(); true }
                // Removing the card in My Devices only hides it; picking the headset in Hush brings it back.
                "delete_device" -> {
                    context!!.getSharedPreferences("hush", 0).edit().putBoolean(PREF_HIDDEN, true).apply()
                    context!!.contentResolver.notifyChange(DeviceCard.URI, null)
                    true
                }
                DeviceCard.METHOD_MODE -> {
                    val mode = runCatching { AmbientMode.valueOf(extras?.getString("extra").orEmpty()) }.getOrNull()
                    if (mode != null) controller.setAmbientMode(mode)
                    mode != null
                }
                else -> true // card_show, card_hide, call: nothing to do
            }
        }
        callback?.let { reply(it, ok) }
        return Bundle().apply { putBoolean("result", ok) }
    }

    private fun stateBundle(): Bundle {
        val s = CardSnapshot(controller.state.value)
        return Bundle().apply {
            putString("address", s.address)
            putBoolean("connected", s.connected)
            putString("mode", s.mode?.name)
            s.battery?.let { putInt("battery", it) }
        }
    }

    private fun runOnMainBundle(block: () -> Bundle): Bundle {
        var result = Bundle()
        runOnMain { result = block(); true }
        return result
    }

    private fun unhide() {
        context!!.getSharedPreferences("hush", 0).edit().remove(PREF_HIDDEN).apply()
    }

    private fun runOnMain(block: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val done = java.util.concurrent.CountDownLatch(1)
        var result = false
        main.post { result = runCatching(block).onFailure { Log.w(TAG, "card call failed", it) }.getOrDefault(false); done.countDown() }
        done.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return result
    }

    private fun reply(binder: IBinder, ok: Boolean) {
        val data = Parcel.obtain()
        val out = Parcel.obtain()
        try {
            data.writeInterfaceToken(CALLBACK_DESCRIPTOR)
            data.writeInt(CALLBACK_RESULT)
            data.writeInt(1)
            Bundle().apply {
                putInt("result_code", if (ok) 100 else 1)
                putString("result_msg", if (ok) "ok" else "failed")
            }.writeToParcel(data, 0)
            binder.transact(1, data, out, 0)
            out.readException()
        } catch (e: Exception) {
            Log.w(TAG, "callback failed", e)
        } finally {
            out.recycle()
            data.recycle()
        }
    }

    private fun requireHost() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid() || uid == 0 || uid == Process.SHELL_UID) return
        val packages = context!!.packageManager.getPackagesForUid(uid).orEmpty()
        if (packages.none { it in HOSTS }) throw SecurityException("Not a ColorOS host")
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.${DeviceCard.AUTHORITY}.device_info"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val TAG = "HushCard"
        /** My Devices (card + taps) and Bluetooth settings (device page switches). */
        private val HOSTS = setOf("com.heytap.mydevices", "com.oplus.wirelesssettings")
        const val PREF_HIDDEN = "card_hidden"
        private const val CALLBACK_DESCRIPTOR = "com.heytap.deviceinfo.DeviceAppCallbackInterface"
        private const val CALLBACK_RESULT = 196611
        private val COLUMNS = arrayOf("device_id", "device_mac", "device_data", "authority")
    }
}
