package com.occaecat.hush.apple.usb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Fixtures were read from a genuine pair (firmware 26.81): the usbfs blob and the BOS descriptor. */
class EarPodsCheckTest {
    private fun fixture(name: String): ByteArray {
        val hex = javaClass.classLoader!!.getResource("usb/$name")!!.readText().trim()
        return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private val raw = fixture("earpods-usbc-raw.hex")
    private val bos = fixture("earpods-usbc-bos.hex")
    private val genuine = EarPodsCheck.Input(0x05AC, 0x110B, "Apple, Inc.", "EarPods", "ABCD12EFGH", raw, bos)

    @Test fun parsesDevice() {
        val p = UsbDescriptors.parse(raw)!!
        assertEquals("26.81", p.device.release)
        assertEquals(3, p.configs.size)
        assertEquals(listOf(0x20, 0x30, 0x30), p.configs.map { it.functionProtocol })
        assertEquals(listOf(31, 42), p.configs[0].interfaces.mapNotNull { it.hidReportLength })
    }

    @Test fun parsesBos() {
        val s = UsbDescriptors.parseBos(bos)
        assertEquals(listOf(0x01 to 0x30, 0x24 to 0x30, 0x00 to 0x20), s.map { it.subClass to it.protocol })
    }

    @Test fun genuinePasses() {
        val r = EarPodsCheck.check(genuine)
        assertEquals(r.items.filter { it.result != EarPodsCheck.Result.PASS }.toString(), EarPodsCheck.Verdict.GENUINE, r.verdict)
    }

    @Test fun withoutPermissionIsLikely() {
        val r = EarPodsCheck.check(genuine.copy(serial = null, raw = null, bos = null))
        assertEquals(EarPodsCheck.Verdict.LIKELY, r.verdict)
    }

    @Test fun cloneWithCopiedIdsIsSuspicious() {
        // A generic UAC1 chip that only borrowed the IDs and names: one configuration, no BOS.
        val clone = raw.copyOf(18).also { it[17] = 1 }
        val r = EarPodsCheck.check(genuine.copy(raw = clone, bos = byteArrayOf(5, 15, 5, 0, 0), serial = "0123456789abcdef"))
        assertEquals(EarPodsCheck.Verdict.SUSPICIOUS, r.verdict)
    }

    @Test fun otherDevice() {
        assertEquals(EarPodsCheck.Verdict.NOT_EARPODS, EarPodsCheck.check(genuine.copy(productId = 0x1234)).verdict)
    }
}
