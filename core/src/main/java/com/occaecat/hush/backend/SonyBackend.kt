package com.occaecat.hush.backend

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.occaecat.hush.mdr.Mdr
import com.occaecat.hush.mdr.MdrConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException

/** Sony-specific initialization and MDR command transport. */
class SonyBackend(device: BluetoothDevice, scope: CoroutineScope) : HeadphoneBackend {
    private val connection = MdrConnection(device, scope)
    val events get() = connection.events
    override val closed get() = connection.closed
    override suspend fun open() = connection.open()
    override fun close() = connection.close()
    suspend fun send(payload: ByteArray, table2: Boolean = false) = connection.send(payload, table2)
    private suspend fun sendQuietly(payload: ByteArray, table2: Boolean = false) {
        try { send(payload, table2) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w("SonyBackend", "Request failed", e) }
    }

    override suspend fun initialize() {
        for (req in listOf(
            Mdr.enableAlerts(),
            Mdr.getProtocolInfo(), Mdr.getSupportFunction(), Mdr.getModelName(), Mdr.getFirmwareVersion(),
            Mdr.getSeriesAndColor(), Mdr.getInstructionGuide(), Mdr.getSerialNumber(), Mdr.getBattery(),
            *intArrayOf(2, 3, 4, 5, 7, 8, 9, 10, 11).map(Mdr::getUpdateParam).toTypedArray(),
            Mdr.getCodec(), Mdr.getUpscalingEffect(), Mdr.getUpscalingSetting(), Mdr.getNcAsm(),
            Mdr.getEqCapability(), Mdr.getEq(), Mdr.getSpeakToChat(), Mdr.getSpeakToChatDetail(),
            Mdr.getWearingStatus(), Mdr.getPauseWhenRemoved(), Mdr.getAutoPowerOffCapability(),
            Mdr.getAutoPowerOff(), Mdr.getCustomButton(), Mdr.getSoundQualityMode(),
            Mdr.getOptimizerStatus(), Mdr.getOptimizerResult(),
            Mdr.getGsCapability(0xD1), Mdr.getGsStatus(0xD1), Mdr.getGs(0xD1),
            Mdr.getGsCapability(0xD2), Mdr.getGsStatus(0xD2), Mdr.getGs(0xD2),
            Mdr.getGsCapability(0xD3), Mdr.getGsStatus(0xD3), Mdr.getGs(0xD3),
        )) sendQuietly(req)
        sendQuietly(Mdr.getVoiceGuidance(), table2 = true)
        sendQuietly(Mdr.getVoiceGuidanceLanguage(), table2 = true)
        for (req in listOf(Mdr.getPlayCapability(), Mdr.getPlayStatus(), Mdr.getPlayParam(0x20), Mdr.getPlayParam(0x00), Mdr.getPlayParam(0x02), Mdr.getPlayParam(0x01))) sendQuietly(req)
        // Multipoint management: harmless if unsupported (the headset just doesn't answer).
        sendQuietly(Mdr.getPeerStatus(), table2 = true)
        sendQuietly(Mdr.getPeers(), table2 = true)
    }

}
