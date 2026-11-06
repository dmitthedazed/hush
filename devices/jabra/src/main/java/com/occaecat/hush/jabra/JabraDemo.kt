package com.occaecat.hush.jabra

import com.occaecat.hush.gnp.GnpClient
import com.occaecat.hush.gnp.GnpFrame
import com.occaecat.hush.gnp.JabraModel
import com.occaecat.hush.gnp.JabraProtocol
import com.occaecat.hush.gnp.JabraRejectedException
import com.occaecat.hush.gnp.JabraButtonProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.IOException

/**
 * A demo pair that answers GNP reads and writes like the tested hardware (Elite 10 firmware 4.6.0, Elite 85t 2.0.0),
 * so the real [JabraBackend] drives it and every change goes through the same parsing and confirming reads.
 * Values live in memory only; nothing reaches Bluetooth.
 */
internal class JabraDemo(private val model: JabraModel) : GnpClient {
    override val closed = CompletableDeferred<Throwable?>()
    override val events = MutableSharedFlow<GnpFrame>(extraBufferCapacity = 8)

    private val elite10 = model == JabraModel.ELITE_10
    private var mode = 4
    private var ambient = 1
    private var anc = 1
    // Elite 10 stores pause as on = 1; the Elite 85t inverts it (on = 0).
    private var pause = if (elite10) 1 else 0
    private var sleep = 6
    private var announcements = 1
    private var eqEnabled = 1
    private var eq = bytes(5, 0, 0,
        0x1e, 0, 0xb4, 1, 0x0e, 6, 0xc2,
        0x18, 2, 0xee, 0xff, 0x8a, 6, 0x76,
        0x18, 0x0b, 0xb8, 0, 0, 6, 0x29,
        0x18, 0x2e, 0xe0, 0, 0, 6, 0x3d,
        0x28, 0x59, 0x10, 0, 0, 8, 0x28)
    private val defaults = mapOf(
        // Context, side (0 left, 1 right), gesture (single, double, triple), action.
        0 to records(0, 5, 4, 0, 1, 2, 3),
        1 to records(1, 7, 6, 0, 7, 6, 0),
        2 to records(2, 11, 0, 0, 9, 10, 0),
    )
    private val buttons = defaults.mapValues { it.value.copyOf() }.toMutableMap()

    override suspend fun open() = Unit

    override suspend fun read(topic: Int, payload: ByteArray): ByteArray {
        delay(LATENCY)
        if (closed.isCompleted) throw IOException("closed")
        return when (topic) {
            JabraProtocol.PRODUCT_ID -> model.productId.let { bytes(it and 0xff, it shr 8) }
            JabraProtocol.FIRMWARE -> text(if (elite10) "4.6.0" else "2.0.0")
            JabraProtocol.SERIAL -> text("DEMO0000")
            JabraProtocol.EVENTS -> if (elite10) bytes(0, 0, 2, 0x96) else bytes(0, 0, 2, 0x92)
            // Modes bitmask, then HearThrough and ANC maxima: the Elite 10 has fixed ANC strength.
            JabraProtocol.AMBIENCE -> when (payload.single().toInt()) {
                0 -> if (elite10) bytes(7, 4, 0, 0, 0) else bytes(7, 5, 4, 0, 0)
                1 -> bytes(mode)
                2 -> bytes(ambient)
                3 -> bytes(anc)
                else -> throw IOException("Unexpected selector")
            }
            JabraProtocol.BATTERY -> bytes(0x80, 78, 0, 0, 2, 0x15, 0, 64, 5, 0, 81)
            JabraProtocol.EQ -> eq.copyOf()
            // The Elite 10's firmware denies the EQ switch, as on the real pair.
            JabraProtocol.EQ_ENABLED -> if (elite10) throw JabraRejectedException(ACCESS_DENIED) else bytes(eqEnabled)
            JabraProtocol.SLEEP -> bytes(sleep)
            JabraProtocol.ANNOUNCEMENTS -> bytes(announcements)
            JabraButtonProtocol.CONTEXTS -> bytes(0, 1, 2)
            JabraButtonProtocol.CURRENT -> buttons.getValue(payload.single().toInt()).copyOf()
            JabraButtonProtocol.DEFAULTS -> defaults.getValue(payload.single().toInt()).copyOf()
            JabraButtonProtocol.SPOTIFY, JabraButtonProtocol.ASSISTANT -> bytes(0)
            0x138e -> bytes(0, pause)
            0x1383 -> bytes(pause)
            else -> throw JabraRejectedException(0xfa)
        }
    }

    override suspend fun write(topic: Int, payload: ByteArray) {
        delay(LATENCY)
        if (closed.isCompleted) throw IOException("closed")
        when (topic) {
            JabraProtocol.EVENTS -> Unit
            JabraProtocol.AMBIENCE -> when (payload[0].toInt()) {
                1 -> mode = payload[1].u()
                2 -> ambient = payload[1].u()
                3 -> anc = payload[1].u()
            }
            JabraProtocol.EQ_ENABLED -> if (elite10) throw JabraRejectedException(ACCESS_DENIED) else eqEnabled = payload.single().u()
            // Writes carry count, target band, master gain and six bytes per band; reads add each band's maximum.
            JabraProtocol.EQ -> eq = eq.copyOf().also { reply ->
                reply[1] = payload[2]
                reply[2] = payload[3]
                repeat(payload[0].u()) { band ->
                    payload.copyInto(reply, destinationOffset = 4 + 7 * band, startIndex = 4 + 6 * band, endIndex = 10 + 6 * band)
                }
            }
            JabraProtocol.SLEEP -> sleep = payload.single().u()
            JabraProtocol.ANNOUNCEMENTS -> announcements = payload.single().u()
            JabraButtonProtocol.CURRENT -> {
                val records = buttons.getValue(payload[0].u())
                val at = records.indices.step(4).single { i -> (0..2).all { records[i + it] == payload[it] } }
                records[at + 3] = payload[3]
            }
            JabraButtonProtocol.DEFAULTS -> buttons[payload.single().u()] = defaults.getValue(payload.single().u()).copyOf()
            0x138e, 0x1383 -> pause = payload.last().u()
            else -> throw JabraRejectedException(0xfa)
        }
    }

    override fun close() { closed.complete(null) }

    private companion object {
        const val LATENCY = 40L
        const val ACCESS_DENIED = 0xfd

        fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
        fun text(value: String) = bytes(value.length) + value.toByteArray()
        fun Byte.u() = toInt() and 0xff

        /** Left single/double/triple, then right single/double/triple. */
        fun records(context: Int, vararg actions: Int) = (0..1).flatMap { side ->
            (0..2).flatMap { gesture -> listOf(context, side, gesture, actions[side * 3 + gesture]) }
        }.map(Int::toByte).toByteArray()
    }
}
