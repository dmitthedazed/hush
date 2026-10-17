package com.occaecat.hush.assistant

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

/**
 * Talk to ChatGPT or Claude from the headphones. The XM4's "touch and hold" (with the CUSTOM button not set to
 * Gemini or Alexa) asks the phone for voice recognition, which Android delivers as VOICE_COMMAND;
 * [VoiceCommandActivity] takes it and opens the chosen app straight in voice mode. The activity is only enabled
 * while an AI assistant is chosen, so Android never asks about Hush otherwise.
 */
enum class Assistant(val label: String, val pkg: String?) {
    PHONE("Phone default", null),
    CHATGPT("ChatGPT", "com.openai.chatgpt"),
    CLAUDE("Claude", "com.anthropic.claude"),
    // Not assistants, but the same gesture: Hush does them itself when the VOICE_COMMAND arrives.
    NOISE_CYCLE("Switch noise mode", null),
    NEXT_SCENE("Next scene", null),
    SLEEP_TIMER("Sleep timer (30 min)", null),
    RECOGNIZE("Recognize song", null),
    RECORDER("Voice recorder", null),
    FLASHLIGHT("Flashlight", null),
    BROADCAST("Automation (Tasker)", null),
}

object Assistants {
    private const val PREFS = "assistant"
    const val ACTION_ASK = "com.occaecat.hush.ASK"
    const val EXTRA_WHO = "who"

    fun current(c: Context): Assistant =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("who", null)
            ?.let { runCatching { Assistant.valueOf(it) }.getOrNull() } ?: Assistant.PHONE

    fun set(c: Context, a: Assistant) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("who", a.name).apply()
        c.packageManager.setComponentEnabledSetting(
            ComponentName(c, VoiceCommandHandler::class.java),
            if (a == Assistant.PHONE) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun installed(c: Context, a: Assistant) = a.pkg == null ||
        runCatching { c.packageManager.getPackageInfo(a.pkg, 0) }.isSuccess

    /** Whether the headset's voice button will reach Hush (Hush is the only or the "Always" VOICE_COMMAND app). */
    fun handlesVoiceButton(c: Context): Boolean {
        val r = c.packageManager.resolveActivity(Intent(Intent.ACTION_VOICE_COMMAND), PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == c.packageName
    }

    /** Broadcast for Tasker / MacroDroid profiles when the hold action is "Automation". */
    const val ACTION_HOLD = "com.occaecat.hush.action.HOLD"

    private var torchOn = false
    private var torchWatched = false

    /** Runs the in-app hold actions; returns true when [a] was one of them. */
    private fun runLocal(c: Context, a: Assistant): Boolean {
        val controller = com.occaecat.hush.HeadphonesController.get(c)
        when (a) {
            Assistant.NOISE_CYCLE -> controller.cycleNoiseMode()
            Assistant.NEXT_SCENE -> com.occaecat.hush.features.Scenes.next(c)
            Assistant.SLEEP_TIMER -> com.occaecat.hush.features.SleepTimer.let {
                if (it.state.value == com.occaecat.hush.features.SleepTimer.State.Off) it.start(c, 30) else it.cancel()
            }
            Assistant.FLASHLIGHT -> {
                val cm = c.getSystemService(android.hardware.camera2.CameraManager::class.java)
                val id = cm.cameraIdList.firstOrNull {
                    cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return true
                if (!torchWatched) {
                    torchWatched = true
                    cm.registerTorchCallback(object : android.hardware.camera2.CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == id) torchOn = enabled }
                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                }
                runCatching { cm.setTorchMode(id, !torchOn); torchOn = !torchOn }
            }
            Assistant.BROADCAST -> c.sendBroadcast(Intent(ACTION_HOLD).putExtra("headset", controller.state.value.displayName))
            else -> return false
        }
        return true
    }

    /** Opens [a] ready to listen. Returns false when the app isn't installed (the Play Store page opens instead). */
    fun launch(c: Context, a: Assistant): Boolean {
        if (runLocal(c, a)) return true
        val intent = when (a) {
            // ChatGPT handles chatgpt.com/voice by opening Advanced Voice directly.
            Assistant.CHATGPT -> Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/voice")).setPackage(a.pkg)
            // Claude's assistant overlay starts a voice conversation.
            Assistant.CLAUDE -> Intent("android.intent.action.VOICE_ASSIST").setPackage(a.pkg)
            Assistant.PHONE -> Intent("android.intent.action.VOICE_ASSIST")
            // Shazam tags straight away; without it Google's "what's this song" listens instead.
            Assistant.RECOGNIZE -> Intent("com.shazam.android.intent.actions.START_TAGGING").setPackage("com.shazam.android")
                .takeIf { c.packageManager.resolveActivity(it, 0) != null }
                ?: Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH")
            Assistant.RECORDER -> Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION)
            else -> return false
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            c.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            if (a.pkg != null) runCatching {
                c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            false
        }
    }
}

/** Invisible trampoline for VOICE_COMMAND (the headset button) and Hush's own "Ask …" shortcuts. */
class VoiceCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val who = intent.getStringExtra(Assistants.EXTRA_WHO)?.let { runCatching { Assistant.valueOf(it) }.getOrNull() }
            ?: Assistants.current(this)
        Assistants.launch(this, who)
        finish()
    }
}

/** Separate component so the VOICE_COMMAND filter can be switched off while "Phone default" is chosen. */
class VoiceCommandHandler : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Assistants.launch(this, Assistants.current(this))
        finish()
    }
}
