package com.occaecat.hush.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ModeKey = ActionParameters.Key<String>("mode")

/** Sets a noise mode; when no session is up the tap reconnects instead. */
class SetModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withContext(Dispatchers.Main) {
            val c = HeadphonesController.get(context)
            val mode = parameters[ModeKey]?.let { runCatching { AmbientMode.valueOf(it) }.getOrNull() }
            if (c.state.value.phase != Phase.CONNECTED) c.connectSaved()
            else if (mode != null) c.setAmbientMode(mode)
            else c.cycleNoiseMode()
            Unit
        }
}

class ToggleDseeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withContext(Dispatchers.Main) {
            val c = HeadphonesController.get(context)
            c.state.value.dseeAuto?.let { c.setDsee(!it) }
            Unit
        }
}

class ToggleSpeakToChatAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withContext(Dispatchers.Main) {
            val c = HeadphonesController.get(context)
            c.state.value.speakToChat?.let { c.setSpeakToChat(!it) }
            Unit
        }
}

fun setMode(mode: AmbientMode) = actionRunCallback<SetModeAction>(actionParametersOf(ModeKey to mode.name))

/** Next mode, or reconnect when disconnected. */
fun cycleMode() = actionRunCallback<SetModeAction>()
