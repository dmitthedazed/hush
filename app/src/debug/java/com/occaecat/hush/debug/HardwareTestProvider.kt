package com.occaecat.hush.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.jabra.JabraButtonContext
import com.occaecat.hush.jabra.JabraButtonSide
import com.occaecat.hush.jabra.JabraButtonGesture
import com.occaecat.hush.jabra.JabraButtonSlot
import com.occaecat.hush.update.JabraFirmware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.occaecat.hush.mdr.AmbientMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Debug APK only. ADB shell can exercise the same controller actions as the UI; other apps cannot. */
class HardwareTestProvider : ContentProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() in setOf(Process.SHELL_UID, Process.myUid(), 0)) { "ADB shell only" }
        val finished = CountDownLatch(1)
        var result = Bundle()
        scope.launch {
            try {
                val c = HeadphonesController.get(context!!)
                var checkedPackage: JSONObject? = null
                when (method) {
                    "paired" -> c.refreshPaired()
                    "connect" -> c.pick(requireNotNull(arg))
                    "disconnect" -> c.disconnect()
                    "mode" -> c.setAmbientMode(AmbientMode.valueOf(requireNotNull(arg)))
                    "ambient" -> c.setAmbientLevel(requireNotNull(arg).toInt(), false)
                    "anc" -> c.setAncLevel(requireNotNull(arg).toInt())
                    "eqEnabled" -> c.setEqEnabled(requireNotNull(arg).toBooleanStrict())
                    "eqBands" -> c.applyEqBands(requireNotNull(arg).split(',').map(String::toInt), c.state.value.eqCaps?.layoutId)
                    "pause" -> c.setPauseWhenRemoved(requireNotNull(arg).toBooleanStrict())
                    "sleep" -> c.setSleepMinutes(requireNotNull(arg).toInt())
                    "announcements" -> c.setAnnouncementMode(requireNotNull(arg).toInt())
                    "refresh" -> c.refreshBattery()
                    "volume" -> c.setVolume(requireNotNull(arg).toInt())
                    "playPause" -> c.playPause()
                    "buttons" -> c.refreshJabraButtons()
                    "button" -> {
                        val values = requireNotNull(arg).split(',')
                        require(values.size == 4)
                        c.setJabraButton(JabraButtonSlot(JabraButtonContext.valueOf(values[0]),
                            JabraButtonSide.valueOf(values[1]), JabraButtonGesture.valueOf(values[2])), values[3].toInt())
                    }
                    "firmwareReadiness" -> c.readJabraFirmwareReadiness()
                    "firmwarePackageCheck" -> {
                        // Read-only validation of the official package. Deliberately no install endpoint.
                        val readiness = c.readJabraFirmwareReadiness()
                        val manifest = JabraFirmware.check(context!!, readiness.productId, force = true)
                        val pack = JabraFirmware.prepare(context!!, manifest, requireNotNull(readiness.languageId))
                        checkedPackage = JSONObject().put("version", pack.version).put("productId", pack.productId)
                            .put("files", pack.files.size).put("bytes", pack.totalBytes).put("language", pack.languageId)
                    }
                    "state" -> Unit
                    else -> error("Unknown hardware test action")
                }
                val s = c.state.value
                val json = JSONObject().apply {
                    put("phase", s.phase.name); put("brand", s.brand.name)
                    putOpt("model", s.modelName); putOpt("firmware", s.firmware); putOpt("error", s.error)
                    putOpt("verifiedPackage", checkedPackage)
                    s.jabraFirmwareReadiness?.let { r -> put("readiness", JSONObject()
                        .put("ready", r.ready).put("language", r.languageId).put("locked", r.locked)
                        .put("protocols", JSONArray(r.protocols.toList())).put("blockers", JSONArray(r.blockers))) }
                    s.jabraButtons?.let { b ->
                        put("buttonsLoading", b.loading); putOpt("buttonsError", b.error)
                        put("buttons", JSONArray(b.slots.map { a -> JSONObject()
                            .put("context", a.slot.context.name).put("side", a.slot.side.name)
                            .put("gesture", a.slot.gesture.name).put("action", a.action)
                            .put("allowed", JSONArray(a.allowedActions.map { it.id })).putOpt("lock", a.lockReason) }))
                    }
                    putOpt("mode", s.ncAsm?.mode?.name); putOpt("ambient", s.ncAsm?.ambientLevel)
                    put("ambientMax", s.capabilities.ambientMax); put("ancMax", s.capabilities.ancMax); putOpt("anc", s.ancLevel)
                    putOpt("battery", s.battery)
                    put("components", JSONArray(s.batteries.map { b -> JSONObject().apply {
                        put("id", b.id); putOpt("level", b.level); putOpt("charging", b.charging)
                    } }))
                    putOpt("eqEnabled", s.eq?.enabled); put("eqBands", JSONArray(s.eq?.bands.orEmpty()))
                    put("eqSpecs", JSONArray(s.eqCaps?.bandSpecs.orEmpty().map { b -> JSONObject().apply {
                        put("hz", b.frequencyHz); put("min", b.min); put("max", b.max); put("unitsPerDb", b.unitsPerDb)
                    } }))
                    putOpt("pause", s.pauseWhenRemoved); putOpt("sleep", s.sleepMinutes); putOpt("announcements", s.announcementMode)
                    putOpt("volume", s.volume); put("volumeSteps", s.volumeSteps); putOpt("playing", s.playing)
                    if (method == "paired") put("paired", JSONArray(s.paired.filter { it.brand.name == "JABRA" }.map {
                        JSONObject().put("name", it.name).put("address", it.address)
                    }))
                }
                result = Bundle().apply { putString("state", json.toString()) }
            } catch (e: Exception) {
                result = Bundle().apply { putString("error", e.message ?: e.javaClass.simpleName) }
            } finally { finished.countDown() }
        }
        check(finished.await(90, TimeUnit.SECONDS)) { "Controller did not respond" }
        return result
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
