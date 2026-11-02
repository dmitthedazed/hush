package com.occaecat.hush.fallback

import android.content.Context
import com.occaecat.hush.devices.BatteryLevel
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceState
import com.occaecat.hush.devices.Equalizer
import com.occaecat.hush.devices.Source
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.devices.isDemo
import com.occaecat.hush.features.PhoneSleepTarget
import com.occaecat.hush.features.SleepTarget
import com.occaecat.hush.wired.PhoneEqualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The phone standing in for what a device can't do itself (docs/capabilities.md): the shell wraps every real
 * controller in this once, so a module only reports its hardware and the UI still gets an equalizer, a battery and
 * a sleep timer, each marked [Source.PHONE]. Demos are left alone: they must never touch the phone's audio.
 */
fun DeviceController.withPhoneFallbacks(context: Context, device: Device): DeviceController =
    if (device.isDemo) this else PhoneFallbacks(this, context.applicationContext, device)

private class PhoneFallbacks(
    private val inner: DeviceController,
    private val app: Context,
    private val device: Device,
) : DeviceController by inner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val state: StateFlow<DeviceState> = device.address
        ?.takeIf { device.transport == Transport.BLUETOOTH }
        ?.let { address ->
            combine(inner.state, BluetoothBattery.levels(app).map { it[address] }) { s, level -> withBattery(s, level) }
                .stateIn(scope, SharingStarted.Eagerly, inner.state.value)
        } ?: inner.state

    private fun withBattery(s: DeviceState, level: Int?) =
        if (s.connected && s.batteries.isEmpty() && level != null) s.copy(batteries = listOf(BatteryLevel(level, providedBy = Source.PHONE)))
        else s

    /** A phone-side EQ for headphones without one, under its own preferences file and owner key. */
    private val phoneEq: PhoneEqualizer? by lazy {
        if (inner.equalizer != null) return@lazy null
        PhoneEqualizer(
            app, device.key, device.name, "phone_eq_" + device.key.replace(Regex("[^A-Za-z0-9_]"), "_"),
            inner.state.map { it.connected }.stateIn(scope, SharingStarted.Eagerly, inner.state.value.connected),
            inner.state.value.model ?: device.name,
        )
    }

    override val equalizer: Equalizer? get() = inner.equalizer ?: phoneEq

    private val phoneSleep by lazy { PhoneSleepTarget(app, device.name) }
    override val sleepTarget: SleepTarget get() = inner.sleepTarget ?: phoneSleep

    override fun onHeroTap() {
        inner.onHeroTap()
        if (device.transport == Transport.BLUETOOTH) device.address?.let { BluetoothBattery.refresh(app, it) }
    }
}
