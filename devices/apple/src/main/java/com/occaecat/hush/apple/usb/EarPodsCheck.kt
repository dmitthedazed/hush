package com.occaecat.hush.apple.usb

/**
 * Does a USB device look like genuine Apple EarPods (USB-C)? Compares what it reports against a reference unit
 * (firmware 26.81, read on 2026-10-19): an Apple-specific USB layout with three audio configurations (UAC2, a UAC3
 * BADD headset profile and full UAC3), announced through Configuration Summary capabilities, plus the remote and an
 * Apple vendor HID channel. Cheap clones are usually generic UAC1/UAC2 chips that only copy the names and IDs.
 *
 * This is a fingerprint, not cryptography: the EarPods expose no authentication challenge over USB, so a clone that
 * copies every descriptor would pass. A failed check, on the other hand, is a strong sign of a fake.
 */
object EarPodsCheck {
    const val APPLE_VENDOR_ID = 0x05AC
    const val EARPODS_USB_C_PRODUCT_ID = 0x110B

    enum class Result { PASS, FAIL, UNKNOWN }

    data class Item(val title: String, val detail: String, val result: Result)

    enum class Verdict {
        /** Everything matched the reference unit. */
        GENUINE,
        /** Nothing contradicts it, but some parts couldn't be read (usually: no USB permission yet). */
        LIKELY,
        /** At least one check failed. */
        SUSPICIOUS,
        /** Not even presenting itself as EarPods (USB-C). */
        NOT_EARPODS,
    }

    data class Report(val verdict: Verdict, val items: List<Item>) {
        val passed get() = items.count { it.result == Result.PASS }
    }

    data class Input(
        val vendorId: Int,
        val productId: Int,
        val manufacturer: String?,
        val product: String?,
        /** Needs USB permission on Android; null when it couldn't be read. */
        val serial: String?,
        /** `getRawDescriptors()`; needs USB permission. */
        val raw: ByteArray?,
        /** The BOS descriptor read with GET_DESCRIPTOR; needs USB permission. */
        val bos: ByteArray?,
    )

    fun isEarPods(vendorId: Int, productId: Int) = vendorId == APPLE_VENDOR_ID && productId == EARPODS_USB_C_PRODUCT_ID

    /** Apple's serial formats: 12 characters until 2021, 8–14 random characters since; upper-case letters and digits. */
    private val serialPattern = Regex("^[A-Z0-9]{8,14}$")

    private const val UAC2 = 0x20
    private const val UAC3 = 0x30
    private const val BADD_HEADSET = 0x24

    fun check(input: Input): Report {
        val items = mutableListOf<Item>()
        fun add(title: String, result: Result, detail: String) { items += Item(title, detail, result) }
        fun pass(ok: Boolean) = if (ok) Result.PASS else Result.FAIL

        add("Apple vendor ID", pass(input.vendorId == APPLE_VENDOR_ID), "0x%04X, expected 0x05AC".format(input.vendorId))
        add("EarPods (USB-C) product ID", pass(input.productId == EARPODS_USB_C_PRODUCT_ID),
            "0x%04X, expected 0x110B".format(input.productId))
        add("Names", pass(input.manufacturer == "Apple, Inc." && input.product == "EarPods"),
            "“${input.manufacturer ?: "—"}” · “${input.product ?: "—"}”")
        when (val s = input.serial) {
            null -> add("Serial number", Result.UNKNOWN, "Not readable without USB access")
            else -> add("Serial number", pass(serialPattern.matches(s)),
                if (serialPattern.matches(s)) "Apple format, ${s.length} characters" else "“$s” isn’t an Apple serial")
        }

        val parsed = input.raw?.let(UsbDescriptors::parse)
        if (parsed == null) {
            add("USB layout", Result.UNKNOWN, "Descriptors not readable without USB access")
        } else {
            val d = parsed.device
            add("USB device descriptor",
                pass(d.bcdUsb == 0x0201 && d.deviceClass == 0xEF && d.deviceSubClass == 2 && d.deviceProtocol == 1 &&
                    d.maxPacketSize0 == 64 && d.numConfigurations == 3),
                "USB %x.%02x, class %02X/%02X/%02X, %d configurations".format(
                    d.bcdUsb shr 8, d.bcdUsb and 0xFF, d.deviceClass, d.deviceSubClass, d.deviceProtocol, d.numConfigurations))

            val protocols = parsed.configs.map { it.functionProtocol }
            val shape = parsed.configs.all { c ->
                c.numInterfaces == 5 && c.interfaces.map { it.interfaceClass }.distinct() == listOf(1, 3)
            }
            add("Audio configurations", pass(protocols == listOf(UAC2, UAC3, UAC3) && shape),
                protocols.joinToString(" · ") { when (it) { UAC2 -> "UAC2"; UAC3 -> "UAC3"; null -> "—"; else -> "0x%02X".format(it) } } +
                    if (shape) ", audio + 2 HID each" else ", unexpected interfaces")

            val hid = parsed.configs.firstOrNull()?.interfaces?.mapNotNull { it.hidReportLength }.orEmpty()
            add("Remote and Apple HID channel", pass(hid == listOf(31, 42)),
                if (hid.isEmpty()) "No HID interfaces" else "Report descriptors of ${hid.joinToString(" and ")} bytes")

            // 48 kHz: 288 = 2 ch × 3 bytes × 48, 192 = 2 × 2 × 48; mic 96 = 1 × 2 × 48, 144 = 1 × 3 × 48.
            val packets = parsed.configs.firstOrNull()?.interfaces
                ?.filter { it.interfaceClass == 1 && it.subClass == 2 }
                ?.flatMap { it.endpoints.map { e -> e.maxPacketSize } }.orEmpty()
            add("Audio formats", pass(packets == listOf(288, 192, 96, 144)),
                if (packets == listOf(288, 192, 96, 144)) "48 kHz stereo out and mono mic, 16 and 24 bit"
                else "Packet sizes ${packets.joinToString()}")

            val power = parsed.configs.map { it.maxPowerMa to it.attributes }.distinct()
            add("Power", pass(power == listOf(300 to 0xA0)),
                power.joinToString { (ma, attr) -> "$ma mA" + if (attr and 0x40 == 0) ", bus powered" else ", self powered" })
        }

        if (input.bos == null) {
            add("UAC3 headset profile", Result.UNKNOWN, "Not readable without USB access")
        } else {
            val summaries = UsbDescriptors.parseBos(input.bos)
            val badd = summaries.any { it.functionClass == 1 && it.subClass == BADD_HEADSET && it.protocol == UAC3 }
            add("UAC3 headset profile", pass(badd && summaries.size == 3),
                if (badd) "Basic Audio Device headset profile announced" else "${summaries.size} configuration summaries, no BADD headset")
        }

        val identity = items.take(2).all { it.result == Result.PASS }
        val verdict = when {
            !identity -> Verdict.NOT_EARPODS
            items.any { it.result == Result.FAIL } -> Verdict.SUSPICIOUS
            items.any { it.result == Result.UNKNOWN } -> Verdict.LIKELY
            else -> Verdict.GENUINE
        }
        return Report(verdict, items)
    }
}
