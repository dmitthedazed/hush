package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Frame
import com.occaecat.hush.mdr.FrameParser
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.Table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Replays what a real WH-1000XM3 sent (core/src/test/resources/mdr/WH-1000XM3-4.5.2, from mos9527's tests). */
class MdrV1CaptureTest {
    private val events: List<MdrEvent> by lazy {
        val dir = File(javaClass.classLoader!!.getResource("mdr/WH-1000XM3-4.5.2")!!.toURI())
        val parser = FrameParser()
        dir.listFiles { f -> f.name.endsWith(".bin") }!!.sortedBy { it.name }.flatMap { f ->
            val bytes = f.readBytes()
            parser.feed(bytes, bytes.size).map { frame ->
                MdrV1.parse(if (frame.type == Frame.Type.DATA_MDR_NO2) Table.TWO else Table.ONE, frame.payload)
            }
        }
    }

    private inline fun <reified T : MdrEvent> one(): T = events.filterIsInstance<T>().first()

    @Test
    fun everyFrameDecodes() {
        val unknown = events.filterIsInstance<MdrEvent.Unknown>().map { it.payload.joinToString(" ") { b -> "%02x".format(b) } }
        assertEquals(emptyList<String>(), unknown)
    }

    @Test
    fun readsTheXm3() {
        assertEquals(0x4010, one<MdrEvent.ProtocolInfo>().version)
        assertEquals("WH-1000XM3", one<MdrEvent.ModelName>().name)
        assertEquals("4.5.2", one<MdrEvent.FirmwareVersion>().version)
        val functions = one<MdrEvent.SupportedFunctions>().codes
        assertTrue(FunctionType.VPT.code in functions && FunctionType.SOUND_POSITION.code in functions)

        val caps = one<MdrEvent.NcAsmCaps>().caps
        assertEquals(NcAsmKind.NC_ASM, caps.kind)
        assertEquals(20, caps.asmSteps)
        assertTrue(caps.voice && caps.windReduction)
        assertEquals(AmbientMode.NOISE_CANCELLING, events.filterIsInstance<MdrEvent.NcAsm>().first().state.mode)

        // Clear Bass first, then 400 Hz … 16 kHz.
        val bands = one<MdrEvent.EqBands>().bands
        assertEquals(0x10, bands.first().infoType)
        assertEquals(400, bands[1].hz)

        val list = events.filterIsInstance<MdrEvent.GsCapability>().first { it.setting.title == "ASSIGNABLE_KEY_SETTING" }.setting
        assertEquals(false, list.isBoolean)
        assertEquals(3, list.options.size)
    }
}
