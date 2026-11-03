package com.occaecat.hush.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.occaecat.hush.DeviceModules
import com.occaecat.hush.Phase
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceState
import com.occaecat.hush.devices.NoiseKind
import com.occaecat.hush.devices.NoiseUi
import com.occaecat.hush.devices.PresetGroup
import com.occaecat.hush.devices.QuickToggle
import com.occaecat.hush.link.Command
import com.occaecat.hush.link.HushLink
import com.occaecat.hush.link.NoiseMode
import com.occaecat.hush.link.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Phone half of the watch companion route: mirrors the current device, whatever its brand, to the Data Layer so the
 * watch app, its tile and complications see it, and runs the commands the watch sends (see [PhoneWearService]).
 */
object WatchBridge {
    private const val TAG = "WatchBridge"

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start(context: Context, scope: CoroutineScope) {
        val data = Wearable.getDataClient(context)
        scope.launch {
            DeviceModules.current.flatMapLatest { device ->
                if (device == null) return@flatMapLatest flowOf(Snapshot())
                val c = DeviceModules.controller(device)
                combine(
                    c.state,
                    c.noise?.state ?: flowOf(null),
                    c.quick?.toggles ?: flowOf(emptyList()),
                    c.equalizer?.presets ?: flowOf(emptyList()),
                ) { s, noise, toggles, presets -> snapshot(c, s, noise, toggles, presets) }
            }.distinctUntilChanged()
                // Slider drags and the initial burst of reads settle into one sync.
                .debounce(250)
                .collect { snapshot ->
                    val request = PutDataMapRequest.create(HushLink.STATE_PATH).apply {
                        dataMap.putString(HushLink.KEY_SNAPSHOT, snapshot.toJson())
                    }.asPutDataRequest().setUrgent()
                    try {
                        data.putDataItem(request).await()
                    } catch (e: ApiException) {
                        // No Wear OS companion on this phone: nothing to mirror to, stop trying.
                        if (e.statusCode == CommonStatusCodes.API_NOT_CONNECTED) {
                            Log.i(TAG, "Wearable API unavailable, watch bridge off")
                            throw kotlinx.coroutines.CancellationException("no wearable api")
                        }
                        Log.w(TAG, "state sync failed", e)
                    }
                }
        }
    }

    private fun snapshot(c: DeviceController, s: DeviceState, noise: NoiseUi?, toggles: List<QuickToggle>, presets: List<PresetGroup>): Snapshot {
        val modes = c.noise?.modes.orEmpty()
        val active = modes.firstOrNull { it.id == noise?.active }
        val all = presets.flatMap { it.presets }
        return Snapshot(
            phase = when (s.connection) {
                Connection.CONNECTED -> Phase.CONNECTED
                Connection.CONNECTING -> Phase.CONNECTING
                Connection.RECONNECTING -> Phase.RECONNECTING
                Connection.DISCONNECTED -> Phase.IDLE
            },
            name = s.name,
            battery = s.battery,
            charging = s.charging,
            mode = active?.kind?.toLink(),
            modes = modes.mapNotNull { it.kind.toLink() }.distinct(),
            ambientLevel = noise?.level?.value ?: 0,
            ambientMax = noise?.level?.range?.last,
            voiceFocus = noise?.voiceFocus,
            toggles = toggles.filter { it.enabled }.map { Snapshot.Toggle(it.id, it.label, it.checked) },
            eqPreset = all.firstOrNull { it.active }?.id,
            eqPresets = all.map { Snapshot.Preset(it.id, it.name) },
            hasAddress = true,
        )
    }

    /** The watch draws three noise states; an adaptive mode has no button there. */
    private fun NoiseKind.toLink() = when (this) {
        NoiseKind.CANCELLING -> NoiseMode.NOISE_CANCELLING
        NoiseKind.AMBIENT -> NoiseMode.AMBIENT
        NoiseKind.OFF -> NoiseMode.OFF
        NoiseKind.ADAPTIVE -> null
    }

    /** Runs a watch [Command] on the current device. */
    fun run(command: Command) {
        val c = DeviceModules.current.value?.let(DeviceModules::controller) ?: return
        val noise = c.noise
        fun modeId(m: NoiseMode) = noise?.modes?.firstOrNull { it.kind.toLink() == m }?.id
        when (command) {
            Command.Connect -> if (!c.state.value.connected) c.reconnect()
            Command.Disconnect -> c.disconnect()
            Command.CycleNoise -> c.quick?.cycleMode()
            // The device keeps its own state fresh; nothing to ask for.
            Command.Refresh -> Unit
            is Command.SetMode -> modeId(command.mode)?.let { noise?.setMode(it) }
            is Command.SetAmbientLevel -> modeId(NoiseMode.AMBIENT)?.let { noise?.apply(it, command.level, null) }
            is Command.SetVoiceFocus -> noise?.setVoiceFocus(command.on)
            is Command.SetToggle -> c.quick?.let { q ->
                if (q.toggles.value.any { it.id == command.id && it.checked != command.on }) q.toggle(command.id)
            }
            is Command.SetEqPreset -> c.equalizer?.selectPreset(command.id)
        }
    }
}

/** Started by Google Play services when the watch sends a command, even if Hush is not running. */
class PhoneWearService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != HushLink.COMMAND_PATH) return
        val command = Command.decode(event.data) ?: return
        Log.d("PhoneWearService", "watch -> $command")
        android.os.Handler(mainLooper).post { WatchBridge.run(command) }
    }
}
