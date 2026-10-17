package com.occaecat.hush.features

import android.content.Context
import android.net.Uri
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.assistant.Assistant
import com.occaecat.hush.assistant.Assistants
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.sound.SoundStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * One JSON file with everything Hush keeps: the sound config (EQ library, scenes, Adaptive Sound Control, places,
 * app profiles), small preferences, optionally the insights history, and a snapshot of the headset's own settings.
 * Plain JSON on purpose, so the laptop's sony-mdr helper can read the same file.
 */
object Backup {
    const val FORMAT = "hush-backup"
    const val VERSION = 1

    data class Summary(val created: Long, val model: String?, val eqCount: Int, val sceneCount: Int, val hasInsights: Boolean, val hasHeadset: Boolean)

    fun export(context: Context, state: HeadphonesState, includeInsights: Boolean): String {
        val o = JSONObject()
            .put("format", FORMAT).put("version", VERSION).put("created", System.currentTimeMillis())
            .put("model", state.modelName ?: JSONObject.NULL)
            .put("sound", SoundStore.exportJson())
            .put("prefs", JSONObject()
                .put("holdAction", Assistants.current(context).name)
                .put("sleepPowerOff", SleepTimer.powerOffWhenDone(context)))
            .put("headset", headsetSnapshot(state))
        if (includeInsights) o.put("insights", Insights.exportJson())
        return o.toString(2)
    }

    fun read(context: Context, uri: Uri): JSONObject {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "Not a Hush backup" }
        return o
    }

    fun summary(o: JSONObject): Summary {
        val sound = o.optJSONObject("sound")
        return Summary(
            o.optLong("created"), o.optString("model").takeIf { it.isNotEmpty() && it != "null" },
            sound?.optJSONArray("library")?.length() ?: 0, sound?.optJSONArray("scenes")?.length() ?: 0,
            o.has("insights"), o.optJSONObject("headset")?.length()?.let { it > 0 } == true,
        )
    }

    /** Restores the phone side; with [applyHeadset] also writes the headset settings that don't force a reconnect. */
    fun restore(context: Context, o: JSONObject, applyHeadset: Boolean, restoreInsights: Boolean) {
        o.optJSONObject("sound")?.let(SoundStore::importJson)
        o.optJSONObject("prefs")?.let { p ->
            p.optString("holdAction").takeIf { it.isNotEmpty() }?.let { runCatching { Assistant.valueOf(it) }.getOrNull() }
                ?.let { Assistants.set(context, it) }
            if (p.has("sleepPowerOff")) SleepTimer.setPowerOffWhenDone(context, p.getBoolean("sleepPowerOff"))
        }
        if (restoreInsights) o.optJSONObject("insights")?.let(Insights::importJson)
        if (applyHeadset) o.optJSONObject("headset")?.let { applyHeadset(HeadphonesController.get(context), it) }
    }

    private fun headsetSnapshot(s: HeadphonesState) = JSONObject().apply {
        s.ncAsm?.let { put("nc", JSONObject().put("mode", it.mode.name).put("level", it.ambientLevel).put("voice", it.voiceFocus)) }
        s.eq?.let { put("eq", JSONObject().put("preset", it.presetId).put("bands", JSONArray(it.bands))) }
        s.speakToChat?.let { put("stc", it) }
        s.speakToChatDetail?.let { put("stcDetail", JSONObject().put("sensitivity", it.sensitivity).put("voice", it.voiceFocus).put("out", it.modeOutTime)) }
        s.pauseWhenRemoved?.let { put("pauseWhenRemoved", it) }
        s.autoPowerOff?.let { put("autoPowerOff", it.first) }
        s.dseeAuto?.let { put("dsee", it) }
        s.voiceGuidance?.let { put("voiceGuidance", it) }
        // General settings except multipoint (that one restarts the Bluetooth link).
        val gs = JSONObject()
        s.generalValues.forEach { (id, on) ->
            if (s.generalSettings[id]?.title != "MULTIPOINT_SETTING") gs.put(s.generalSettings[id]?.title ?: id.toString(), on)
        }
        if (gs.length() > 0) put("general", gs)
        s.customButton?.let { put("customButton", it) } // recorded, not restored automatically (reconnects)
    }

    private fun applyHeadset(c: HeadphonesController, h: JSONObject) {
        h.optJSONObject("nc")?.let { nc ->
            runCatching { AmbientMode.valueOf(nc.getString("mode")) }.getOrNull()?.let { mode ->
                c.setNoiseControl(mode, nc.optInt("level", 10), nc.optBoolean("voice"))
            }
        }
        h.optJSONObject("eq")?.let { eq ->
            val id = eq.getInt("preset")
            val bands = eq.optJSONArray("bands")?.let { a -> List(a.length()) { a.getInt(it) } }.orEmpty()
            if (id in HeadphonesController.WRITABLE_PRESETS && bands.isNotEmpty()) c.writeEqPreset(id, bands) else c.selectEqPreset(id)
        }
        if (h.has("stc")) c.setSpeakToChat(h.getBoolean("stc"))
        h.optJSONObject("stcDetail")?.let { c.setSpeakToChatDetail(SpeakToChatDetail(it.getInt("sensitivity"), it.getBoolean("voice"), it.getInt("out"))) }
        if (h.has("pauseWhenRemoved")) c.setPauseWhenRemoved(h.getBoolean("pauseWhenRemoved"))
        if (h.has("autoPowerOff")) c.setAutoPowerOff(h.getInt("autoPowerOff"))
        if (h.has("dsee")) c.setDsee(h.getBoolean("dsee"))
        if (h.has("voiceGuidance")) c.setVoiceGuidance(h.getBoolean("voiceGuidance"))
        h.optJSONObject("general")?.let { g ->
            val settings = c.state.value.generalSettings
            g.keys().forEach { key ->
                val id = settings.values.firstOrNull { it.title == key }?.id ?: key.toIntOrNull()
                if (id != null && settings[id]?.title != "MULTIPOINT_SETTING") c.setGeneralSetting(id, g.getBoolean(key))
            }
        }
    }
}
