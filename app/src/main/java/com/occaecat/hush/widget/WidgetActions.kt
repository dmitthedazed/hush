package com.occaecat.hush.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.occaecat.hush.DeviceModules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ModeKey = ActionParameters.Key<String>("mode")
private val ToggleKey = ActionParameters.Key<String>("toggle")

/** Sets a mode of the current device (or the next one); when it isn't connected the tap reconnects instead. */
class SetModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withContext(Dispatchers.Main) {
            val device = DeviceModules.current.value ?: return@withContext
            val c = DeviceModules.controller(device)
            val q = c.quick
            when {
                !c.state.value.connected -> c.reconnect()
                q == null -> {}
                parameters[ModeKey] != null -> q.setMode(parameters[ModeKey]!!)
                else -> q.cycleMode()
            }
        }
}

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        withContext(Dispatchers.Main) {
            val device = DeviceModules.current.value ?: return@withContext
            parameters[ToggleKey]?.let { DeviceModules.controller(device).quick?.toggle(it) }
            Unit
        }
}

fun setMode(id: String) = actionRunCallback<SetModeAction>(actionParametersOf(ModeKey to id))

/** Next mode, or reconnect when disconnected. */
fun cycleMode() = actionRunCallback<SetModeAction>()

fun toggle(id: String) = actionRunCallback<ToggleAction>(actionParametersOf(ToggleKey to id))
