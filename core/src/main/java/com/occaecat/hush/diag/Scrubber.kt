package com.occaecat.hush.diag

import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.mdr.Table

/**
 * Takes what identifies the user out of payloads before they leave the phone, keeping every length so the result still
 * parses: Bluetooth addresses become 00:00:5E:00:53:0N (the documentation range), the serial number and device ids
 * zeros, source names, track names and the headset's own name x's.
 */
internal class Scrubber(state: HeadphonesState) {
    private val macs = LinkedHashMap<String, String>()

    /** Names the headset may repeat anywhere (peer list notifies, playback): masked wherever they turn up. */
    private val texts: List<ByteArray> = (
        state.peers.orEmpty().map { it.name } + listOfNotNull(state.track, state.artist, state.album) +
            listOfNotNull(state.deviceName?.takeIf { it != state.modelName }, state.serial)
        ).filter { it.length >= 3 }.distinct().map { it.toByteArray() }.sortedByDescending { it.size }

    private fun fakeMac(real: String) = macs.getOrPut(real.uppercase()) { "00:00:5E:00:53:%02X".format(macs.size + 1) }

    fun payload(table: Table, p: ByteArray): ByteArray {
        if (p.isEmpty()) return p
        val out = p.copyOf()
        MAC.findAll(String(out, Charsets.ISO_8859_1)).forEach { m ->
            fakeMac(m.value).toByteArray().copyInto(out, m.range.first)
        }
        for (t in texts) maskAll(out, t)
        val op = out[0].toInt() and 0xFF
        val param = out.getOrNull(1)?.toInt()?.and(0xFF)
        when {
            // UPDT_RET_PARAM serial number and device id, COMMON_RET_BLUETOOTH_DEVICE_INFO BLE id: [op, type, len, text]
            table == Table.ONE && (op == 0x37 && (param == 0x06 || param == 0x0B) || op == 0x1D && param == 0x01) ->
                fill(out, 3, out.getOrNull(2), '0')
            // PLAY_RET_PARAM / PLAY_NTFY_PARAM track, album, artist: [op, type, status, len, text]
            table == Table.ONE && (op == 0xA7 || op == 0xA9) && param in 0x00..0x02 && out.size > 4 -> fill(out, 4, out[3], 'x')
            // PERIPHERAL_RET_PARAM / NTFY_PARAM device list: n × (17-char address, connected, len, name)
            table == Table.TWO && (op == 0x37 || op == 0x39) && param == 0x01 && out.size > 2 -> {
                var i = 3
                repeat(out[2].toInt() and 0xFF) {
                    if (i + 19 > out.size) return out
                    val len = out[i + 18].toInt() and 0xFF
                    fill(out, i + 19, out[i + 18], 'x')
                    i += 19 + len
                }
            }
        }
        return out
    }

    /** Text already scrubbed by [payload] or written by Hush (the report), with any address left in it replaced. */
    fun text(s: String): String = MAC.replace(s) { fakeMac(it.value) }

    private fun fill(out: ByteArray, from: Int, len: Byte?, c: Char) {
        val n = len?.toInt()?.and(0xFF) ?: return
        for (i in from until minOf(out.size, from + n)) out[i] = c.code.toByte()
    }

    private fun maskAll(out: ByteArray, needle: ByteArray) {
        var i = 0
        while (i <= out.size - needle.size) {
            if ((needle.indices).all { out[i + it] == needle[it] }) {
                for (k in needle.indices) out[i + k] = 'x'.code.toByte()
                i += needle.size
            } else i++
        }
    }

    private companion object {
        val MAC = Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")
    }
}
