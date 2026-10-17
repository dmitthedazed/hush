package com.occaecat.hush

import android.app.Application
import android.content.Intent
import com.occaecat.hush.wear.WatchBridge
import com.occaecat.hush.widget.HushWidgets
import com.occaecat.hush.widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Everything outside the app UI that follows the shared [HeadphonesController] on the phone: the foreground
 * service while a session runs, ColorOS surfaces, Fluid Cloud alerts, home screen widgets and the watch.
 */
object PhoneIntegrations {

    @Volatile private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        val controller = HeadphonesController.get(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = controller.state

        // Picking a headset in the app also brings back a card removed in My Devices.
        controller.onPick = { app.getSharedPreferences("hush", 0).edit().remove(DeviceCardProvider.PREF_HIDDEN).apply() }

        scope.launch {
            state.map { it.phase != Phase.IDLE }.distinctUntilChanged().collect { active ->
                if (active) HushService.start(app) else HushService.stop(app)
            }
        }
        scope.launch {
            // ColorOS quick device connect card when a session first comes up (not after a planned reconnect).
            var previous = Phase.IDLE
            state.map { it.phase }.distinctUntilChanged().collect { phase ->
                if (phase == Phase.CONNECTED && previous == Phase.CONNECTING) announceConnection(app, controller, scope)
                // Lost for good (the reconnect window ran out) or disconnected on purpose; planned reconnects
                // pass through RECONNECTING back to CONNECTED and stay quiet.
                if ((phase == Phase.IDLE || phase == Phase.FAILED) &&
                    (previous == Phase.CONNECTED || previous == Phase.RECONNECTING)
                ) LiveAlerts.disconnected(app, headsetName(controller))
                previous = phase
            }
        }
        scope.launch {
            // Low battery capsule once per drop to the threshold; re-armed after charging above it.
            var warned = false
            state.map { it.battery to it.charging }.distinctUntilChanged().collect { (level, charging) ->
                if (level == null) return@collect
                if (level > LiveAlerts.LOW_BATTERY || charging) warned = false
                else if (!warned) {
                    warned = true
                    LiveAlerts.lowBattery(app, headsetName(controller), level)
                }
            }
        }
        scope.launch {
            // Home screen widgets: a live Glance session recomposes by itself; this covers ended sessions.
            state.map(WidgetState::from).distinctUntilChanged().collect { HushWidgets.updateAll(app) }
        }
        scope.launch {
            state.map(::CardSnapshot).distinctUntilChanged().collect { snapshot ->
                DeviceCard.publish(app, snapshot)
                HushTileService.refresh(app)
            }
        }
        WatchBridge.start(app, controller, scope)
    }

    private fun headsetName(c: HeadphonesController) = c.state.value.displayName

    /** Waits briefly for the first battery read so the card can show it. */
    private fun announceConnection(app: Application, controller: HeadphonesController, scope: CoroutineScope) = scope.launch {
        withTimeoutOrNull(2_000) { controller.state.first { it.battery != null } }
        val s = controller.state.value
        if (s.phase != Phase.CONNECTED) return@launch
        LiveAlerts.connected(app, headsetName(controller), s.battery)
        app.sendBroadcast(
            Intent("com.occaecat.hush.action.SHOW_CONNECT_CARD")
                .setPackage("com.heytap.accessory")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("title", s.displayName)
                .putExtra("status", s.battery?.let { "Battery $it%" } ?: "Connected"),
        )
    }
}
