package com.occaecat.hush.diag

import com.occaecat.hush.diag.SonyCheck.Result
import com.occaecat.hush.diag.SonyCheck.Server
import com.occaecat.hush.diag.SonyCheck.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class SonyCheckTest {
    /** The reference WH-1000XM4 (serial made up), with the update server's answer of 2025-10-08. */
    private val xm4 = SonyCheck.Input(
        model = "WH-1000XM4", bluetoothName = "WH-1000XM4", address = "80:99:E7:29:7E:19", deviceClass = 0x240404,
        firmware = "3.0.1", serial = "1234567", category = "HP002", service = "MDRID294301", color = 0x01,
        server = Server.Known(listOf("WH-1000XM4"), "3.0.1"),
    )

    private fun failed(input: SonyCheck.Input) = SonyCheck.check(input).items.filter { it.result == Result.FAIL }.map { it.title }

    @Test fun referenceUnitIsGenuine() {
        val r = SonyCheck.check(xm4)
        assertEquals(r.items.joinToString { "${it.title}: ${it.result}" }, Verdict.GENUINE, r.verdict)
    }

    @Test fun offlineIsOnlyLikely() = assertEquals(Verdict.LIKELY, SonyCheck.check(xm4.copy(server = Server.NotChecked)).verdict)

    @Test fun cloneAddress() = assertEquals(listOf("Bluetooth address"), failed(xm4.copy(address = "41:42:1A:00:00:01")))

    @Test fun renamedCloneAnnouncesAnotherName() = assertEquals(listOf("Bluetooth name"), failed(xm4.copy(bluetoothName = "TWS-XM4 Pro")))

    @Test fun unknownService() = assertEquals(listOf("Sony update server"), failed(xm4.copy(server = Server.Unknown)))

    @Test fun firmwareFromTheFuture() = assertEquals(listOf("Sony update server"), failed(xm4.copy(firmware = "9.9.9")))

    @Test fun anotherModelsService() =
        assertEquals(listOf("Update service", "Sony update server"),
            failed(xm4.copy(service = "MDRID123456", server = Server.Known(listOf("WH-CH710N"), "1.0.0"))))

    @Test fun colourSonyNeverMade() = assertEquals(listOf("Colour"), failed(xm4.copy(color = 0x0C)))

    @Test fun badSerial() = assertEquals(listOf("Serial number"), failed(xm4.copy(serial = "ABC")))

    @Test fun notAudio() = assertEquals(listOf("Device class"), failed(xm4.copy(deviceClass = 0x5A020C)))

    /** A model without FW_UPDATE: no serial or update service to doubt, the rest still counts. */
    @Test fun modelWithoutUpdates() {
        val r = SonyCheck.check(SonyCheck.Input(
            model = "WI-C100", bluetoothName = "WI-C100", address = "AC:80:0A:01:02:03", firmware = "1.1.0", updates = false,
            serial = null, category = null, service = null, color = null,
        ))
        assertEquals(r.items.joinToString { "${it.title}: ${it.result}" }, Verdict.GENUINE, r.verdict)
    }

    @Test fun protocolV2() {
        val r = SonyCheck.check(SonyCheck.Input(
            model = null, protocolV2 = true, bluetoothName = "WH-1000XM5", address = "AC:80:0A:01:02:03",
            firmware = null, serial = null, category = null, service = null, color = null,
        ))
        assertEquals(Verdict.LIKELY, r.verdict)
        assertEquals(listOf("Sony control protocol", "Bluetooth name", "Bluetooth address"), r.items.map { it.title })
    }
}
