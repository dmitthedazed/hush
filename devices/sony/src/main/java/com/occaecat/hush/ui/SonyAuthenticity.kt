package com.occaecat.hush.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.diag.SonyCheck
import com.occaecat.hush.mdr.v1.FunctionType
import com.occaecat.hush.update.FirmwareCheck

/**
 * Sony's Authenticity section: [SonyCheck] on what the headset reported over MDR, its Bluetooth identity, and what
 * Sony's update server knows about its update service (cached by the firmware check, asked once if not).
 */
@Composable
internal fun SonyAuthenticity(state: HeadphonesState) {
    val context = LocalContext.current
    val category = state.updateInfo[2]
    val service = state.updateInfo[3]
    val server by produceState<SonyCheck.Server>(SonyCheck.Server.NotChecked, category, service, state.modelName, state.firmware) {
        val model = state.modelName
        val fw = state.firmware
        if (category == null || service == null || model == null || fw == null) return@produceState
        value = runCatching { FirmwareCheck.check(context, category, service, model, fw, state.serial) }
            .fold({ SonyCheck.Server.Known(it.models, it.latest) },
                { if (it is FirmwareCheck.NoSuchService) SonyCheck.Server.Unknown else SonyCheck.Server.NotChecked })
    }
    val (name, deviceClass) = bluetoothIdentity(context, state.address)
    val report = SonyCheck.check(SonyCheck.Input(
        model = state.modelName, protocolV2 = state.protocolV2, bluetoothName = name, address = state.address,
        deviceClass = deviceClass, firmware = state.firmware, updates = state.has(FunctionType.FW_UPDATE),
        serial = state.serial, category = category, service = service, color = state.modelColor, server = server,
    ))
    val noun = state.modelName ?: "headphones"
    val (verdict, title, body) = when (report.verdict) {
        SonyCheck.Verdict.GENUINE -> Triple(AuthVerdict.GENUINE, "Looks genuine", "Everything the $noun report matches Sony’s.")
        SonyCheck.Verdict.LIKELY -> Triple(AuthVerdict.LIKELY, "Probably genuine",
            if (server == SonyCheck.Server.NotChecked && category != null) "Nothing contradicts it so far. Sony’s update server couldn’t be asked; it needs the internet."
            else "Nothing contradicts it so far, but some of it couldn’t be read.")
        SonyCheck.Verdict.SUSPICIOUS -> Triple(AuthVerdict.SUSPICIOUS, "Might be a fake", "Some of what these headphones report doesn’t match Sony’s.")
    }
    AuthenticitySection(
        verdict, title, body,
        report.items.map { AuthItem(it.title, it.detail, when (it.result) {
            SonyCheck.Result.PASS -> true
            SonyCheck.Result.FAIL -> false
            SonyCheck.Result.UNKNOWN -> null
        }) },
        "This compares what the headphones report over Bluetooth with Sony’s own data: its control protocol, address " +
            "blocks, model catalogue and update server. Sony’s headphones have no cryptographic authentication, so a " +
            "perfect copy could still pass; a failed check is the meaningful result. For certainty, register the " +
            "serial number with Sony or ask the seller for the receipt.",
    )
}

/** The name the headset announces (not the phone's alias) and its Bluetooth class. */
@SuppressLint("MissingPermission")
private fun bluetoothIdentity(context: Context, address: String?): Pair<String?, Int?> {
    val device = address?.let { a -> runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(a) }.getOrNull() }
        ?: return null to null
    return runCatching { device.name }.getOrNull() to runCatching { device.bluetoothClass?.deviceClass }.getOrNull()
}
