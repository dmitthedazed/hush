package com.occaecat.hush.apple.usb

/**
 * Just enough of the USB 2.0 descriptor format to fingerprint a device: the device descriptor, every configuration
 * with its interfaces, endpoints and HID descriptors (the shape `UsbDeviceConnection.getRawDescriptors` returns,
 * which is the usbfs file: device descriptor followed by all configurations), and the BOS descriptor's
 * Configuration Summary capabilities (USB Audio 3.0, section 4.2.1).
 */
object UsbDescriptors {
    data class Device(
        val bcdUsb: Int,
        val deviceClass: Int,
        val deviceSubClass: Int,
        val deviceProtocol: Int,
        val maxPacketSize0: Int,
        val vendorId: Int,
        val productId: Int,
        /** Binary-coded device release, e.g. 0x2681 for "26.81". */
        val bcdDevice: Int,
        val numConfigurations: Int,
    ) {
        val release get() = "%x.%02x".format(bcdDevice shr 8, bcdDevice and 0xFF)
    }

    data class Endpoint(val address: Int, val attributes: Int, val maxPacketSize: Int)

    data class Interface(
        val number: Int,
        val alternate: Int,
        val interfaceClass: Int,
        val subClass: Int,
        val protocol: Int,
        val endpoints: List<Endpoint>,
        /** wDescriptorLength of the HID report descriptor, for HID interfaces. */
        val hidReportLength: Int?,
    )

    data class Config(
        val value: Int,
        val attributes: Int,
        /** bMaxPower in mA (USB 2.0 units of 2 mA). */
        val maxPowerMa: Int,
        val numInterfaces: Int,
        /** bFunctionProtocol of the first Interface Association: 0x20 for UAC2, 0x30 for UAC3. */
        val functionProtocol: Int?,
        val interfaces: List<Interface>,
    )

    data class Parsed(val device: Device, val configs: List<Config>)

    /** One Configuration Summary capability: which configurations implement a class/subclass/protocol. */
    data class ConfigSummary(val functionClass: Int, val subClass: Int, val protocol: Int, val configIndexes: List<Int>)

    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
    private fun ByteArray.u16(i: Int) = u8(i) or (u8(i + 1) shl 8)

    fun parse(raw: ByteArray): Parsed? {
        if (raw.size < 18 || raw.u8(0) != 18 || raw.u8(1) != 1) return null
        val device = Device(
            bcdUsb = raw.u16(2), deviceClass = raw.u8(4), deviceSubClass = raw.u8(5), deviceProtocol = raw.u8(6),
            maxPacketSize0 = raw.u8(7), vendorId = raw.u16(8), productId = raw.u16(10), bcdDevice = raw.u16(12),
            numConfigurations = raw.u8(17),
        )
        val configs = mutableListOf<Config>()
        var pos = 18
        while (pos + 9 <= raw.size && raw.u8(pos + 1) == 2) {
            val total = raw.u16(pos + 2)
            val end = (pos + total).coerceAtMost(raw.size)
            var functionProtocol: Int? = null
            val interfaces = mutableListOf<Interface>()
            var current: Interface? = null
            var i = pos + raw.u8(pos)
            while (i + 2 <= end) {
                val len = raw.u8(i)
                if (len < 2 || i + len > end) break
                when (raw.u8(i + 1)) {
                    11 -> if (functionProtocol == null && len >= 8) functionProtocol = raw.u8(i + 6)
                    4 -> if (len >= 9) {
                        current?.let(interfaces::add)
                        current = Interface(raw.u8(i + 2), raw.u8(i + 3), raw.u8(i + 5), raw.u8(i + 6), raw.u8(i + 7), emptyList(), null)
                    }
                    5 -> if (len >= 7) current = current?.let {
                        it.copy(endpoints = it.endpoints + Endpoint(raw.u8(i + 2), raw.u8(i + 3), raw.u16(i + 4) and 0x7FF))
                    }
                    0x21 -> if (len >= 9 && current?.interfaceClass == 3) current = current?.copy(hidReportLength = raw.u16(i + 7))
                }
                i += len
            }
            current?.let(interfaces::add)
            configs += Config(raw.u8(pos + 5), raw.u8(pos + 7), raw.u8(pos + 8) * 2, raw.u8(pos + 4), functionProtocol, interfaces)
            if (total < 9) break
            pos += total
        }
        return Parsed(device, configs)
    }

    fun parseBos(raw: ByteArray): List<ConfigSummary> {
        if (raw.size < 5 || raw.u8(1) != 15) return emptyList()
        val out = mutableListOf<ConfigSummary>()
        val end = raw.u16(2).coerceAtMost(raw.size)
        var i = raw.u8(0)
        while (i + 3 <= end) {
            val len = raw.u8(i)
            if (len < 3 || i + len > end) break
            if (raw.u8(i + 1) == 16 && raw.u8(i + 2) == 0x10 && len >= 9) {
                val count = raw.u8(i + 8)
                out += ConfigSummary(raw.u8(i + 5), raw.u8(i + 6), raw.u8(i + 7), (0 until count).mapNotNull {
                    if (i + 9 + it < i + len) raw.u8(i + 9 + it) else null
                })
            }
            i += len
        }
        return out
    }
}
