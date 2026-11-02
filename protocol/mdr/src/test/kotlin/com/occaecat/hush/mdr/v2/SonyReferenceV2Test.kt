package com.occaecat.hush.mdr.v2

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Frame
import com.occaecat.hush.mdr.FrameParser
import com.occaecat.hush.mdr.EqBand
import com.occaecat.hush.mdr.ListeningMode
import com.occaecat.hush.mdr.ListeningModes
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.VoiceGuidanceCapability
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.OptimizerCapability
import com.occaecat.hush.mdr.OptimizerResult
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.v1.Alert as V1Alert
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader

/**
 * Hush's v2 requests and the captured v2 replies, read by Sound Connect's own v2 message classes
 * (tools/sonyref/sound-connect.jar, see SonyReference; skipped when the jar was not made). Each table's
 * `c.b.c(bytes)` picks the class for the opcode and inquired type and rejects a payload of the wrong shape.
 */
class SonyReferenceV2Test {
    private val loader by lazy {
        listOf("../tools/sonyref/sound-connect.jar", "tools/sonyref/sound-connect.jar").map(::File).firstOrNull { it.isFile }
            ?.let { URLClassLoader(arrayOf(it.toURI().toURL()), null) }
    }

    /** Null when Sony reads [payload], otherwise why not. */
    private fun sony(table: Table, payload: ByteArray): String? {
        val pkg = "com.sony.songpal.tandemfamily.message.mdr.v2." + if (table == Table.ONE) "table1" else "table2"
        val factory = loader!!.loadClass("$pkg.c\$b").getDeclaredConstructor().newInstance()
        return try {
            factory.javaClass.getMethod("c", ByteArray::class.java).invoke(factory, payload)
            null
        } catch (e: InvocationTargetException) {
            e.targetException.toString()
        }
    }

    private fun check(requests: List<Request>) {
        assumeTrue("tools/sonyref/sound-connect.jar not made", loader != null)
        val rejected = requests.distinct().mapNotNull { r -> sony(r.table, r.payload)?.let { "$r: $it" } }
        assertEquals(emptyList<String>(), rejected)
    }

    /** Everything a WF-1000XM5-like and a WH-1000XM5-like set of functions make Hush send. */
    private val everything = listOf(
        V2Functions(
            t1 = setOf(0x11, 0x12, 0x13, 0x23, 0x25, 0x29, 0x2A, 0x50, 0x6B, 0x90, 0xA1, 0xD1, 0xD2, 0xE2, 0xF1, 0xF3, 0xFC, 0xF0, 0x51),
            t2 = setOf(0x32, 0x42),
        ),
        V2Functions(t1 = setOf(0x20, 0x24, 0x52, 0x6A, 0xE1, 0xF2, 0xFE, 0xA4, 0xD3, 0xD4), t2 = setOf(0x30, 0x40)),
        V2Functions(t1 = setOf(0x28, 0x57, 0x67, 0xE3, 0xA3), t2 = setOf(0x44)),
        V2Functions(t1 = setOf(0x53, 0x6D, 0xA2, 0x80), t2 = setOf(0x45)),
        V2Functions(t1 = setOf(0x81, 0xE4, 0xE5, 0xE8, 0xE9, 0xE7, 0x32), t2 = setOf(0x41, 0x43, 0x55)),
        V2Functions(t1 = setOf(0x82, 0xEB, 0xEC, 0x34)), V2Functions(t1 = setOf(0x35), t2 = setOf(0x42)), V2Functions(t1 = setOf(0x36)),
        V2Functions(t1 = setOf(0x38)), V2Functions(t1 = setOf(0x30)),
        V2Functions(t1 = setOf(0x64)), V2Functions(t1 = setOf(0x65)), V2Functions(t1 = setOf(0x68)), V2Functions(t1 = setOf(0x61)),
    )

    @Test
    fun sessionStart() = check(
        listOf(Connect.protocolInfo()) + Init.handshake(0x03003018, table1 = true, table2 = true) + everything.flatMap(Init::plan)
    )

    @Test
    fun userActions() = check(everything.flatMap { f ->
        val p = V2Protocol(f)
        val states = AmbientMode.entries.flatMap { m -> listOf(false, true).map { NcAsmState(m, 12, it, ncOnValue = 1) } }
        states.flatMap { s -> listOf(true, false).mapNotNull { p.ncAsm(s, it) } } + listOfNotNull(
            p.eqPreset(0x10, true), p.eqBands(0xA0, List(6) { 10 }), p.ebb(-3), p.upscaling(true), p.soundQualityMode(true),
            p.speakToChat(true), p.speakToChatDetail(SpeakToChatDetail(1, true, 2)), p.pauseWhenRemoved(false),
            p.autoPowerOff(0x11, 0x01), p.vibrator(true), p.assignable(listOf(0x35, 0x20)), p.readAssignable(),
            p.alertAnswer(V1Alert.KEY_ASSIGN_CHANGE, true), p.general(0xD2, false), p.generalIndex(0xD1, 1), p.readGeneral(0xD1),
            p.volume(10), p.playControl(0x02), p.readPeers(), p.pairingMode(true), p.readPairingMode(),
            p.peerAction(2, "00:00:5E:00:53:01"), p.voiceGuidance(false, null), p.powerOff(), p.readSoundQualityMode(),
            p.optimizerStart(), p.optimizerCancel(), p.optimizerResult(),
            p.maxVolumeOn(true), p.maxVolumeLevel(3), p.lowLatencyMode(), p.soundQualityMode(false),
            p.voiceGuidance(true, 0x01), p.voiceGuidanceLanguage(true, 0x0A), p.voiceGuidanceVolume(-2, true), p.voiceGuidanceVolume(2, false),
        ) + p.readPlayback() + p.readBattery(emptySet())
    })

    @Test
    fun capturedReplies() {
        assumeTrue("tools/sonyref/sound-connect.jar not made", loader != null)
        val bad = listOf("WF-1000XM5-6.1.0", "WF-LC900-2.0.3", "WF-LC900-2.0.3-equalizer").flatMap { dir ->
            val parser = FrameParser()
            File(javaClass.classLoader!!.getResource("mdr/$dir")!!.toURI()).listFiles { f -> f.name.endsWith(".bin") }!!
                .sortedBy { it.name }.flatMap { f -> f.readBytes().let { parser.feed(it, it.size) } }
                .map { Request(if (it.type == Frame.Type.DATA_MDR_NO2) Table.TWO else Table.ONE, it.payload) }
        }.distinct().mapNotNull { r -> sony(r.table, r.payload)?.let { "$r: $it" } }
        assertEquals(emptyList<String>(), bad)
    }

    /** The replies the simulated WF-1000XM5 adds to its capture. */
    @Test
    fun handWrittenReplies() = check(com.occaecat.hush.sim.Profiles.WF1000XM5_EXTRA.map { Request(it.table, it.payload) })

    /** NC optimizer replies of all three types, as Sony reads them and as Hush reads them. */
    @Test
    fun optimizerReplies() {
        val replies = mapOf(
            bytes(0x81, 0x00, 30, 20, 10) to MdrEvent.OptimizerCaps(OptimizerCapability(30, personal = true, barometric = true)),
            bytes(0x81, 0x01, 20, 20) to MdrEvent.OptimizerCaps(OptimizerCapability(20, personal = true, barometric = false)),
            bytes(0x81, 0x02, 10, 10) to MdrEvent.OptimizerCaps(OptimizerCapability(10, personal = false, barometric = true)),
            bytes(0x83, 0x00, 0x00, 0x10) to MdrEvent.OptimizerState(true, 0x10),
            bytes(0x85, 0x01, 0x00, 0x11) to MdrEvent.OptimizerState(false, 0x11),
            bytes(0x85, 0x02, 0x00, 0x00) to MdrEvent.OptimizerState(false, 0x00),
            bytes(0x87, 0x00, 0x01, 0x09) to MdrEvent.Optimizer(OptimizerResult(true, 9)),
            bytes(0x87, 0x01, 0x00) to MdrEvent.Optimizer(OptimizerResult(false, null)),
            bytes(0x89, 0x02, 0x00) to MdrEvent.Optimizer(OptimizerResult(null, null)),
        )
        for ((payload, event) in replies) assertEquals(event, MdrV2.parse(Table.ONE, payload))
        check(replies.keys.map { Request(Table.ONE, it) })
    }

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    /** Band info, voice guidance capability and volume replies, as Sony reads them and as Hush reads them. */
    @Test
    fun bandsAndPromptReplies() {
        val replies = mapOf(
            bytes(0x5B, 0x00, 0x02, 0x10, 0x00, 0x01, 0x01, 0x01, 0x90) to
                MdrEvent.EqBands(listOf(EqBand(0x10, 0x0001), EqBand(0x01, 400))),
            bytes(0x41, 0x02, 0x01, 0x03, 0x01, 0x02, 0x0A) to
                MdrEvent.VoiceGuidanceCaps(VoiceGuidanceCapability(true, true, listOf(1, 2, 10))),
            bytes(0x41, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01) to MdrEvent.VoiceGuidanceCaps(VoiceGuidanceCapability(true, false, emptyList())),
            bytes(0x41, 0x01, 0x07, 0x00, 0x00, 0x00, 0x00, 0x02, 0x01, 0x0A) to
                MdrEvent.VoiceGuidanceCaps(VoiceGuidanceCapability(false, true, listOf(1, 10))),
            bytes(0x47, 0x20, 0xFE) to MdrEvent.VoiceGuidanceVolume(-2),
            bytes(0x49, 0x21, 0x01) to MdrEvent.VoiceGuidanceVolume(1),
        )
        for ((payload, event) in replies) {
            val table = if (payload[0].toInt() == 0x5B) Table.ONE else Table.TWO
            assertEquals(event, MdrV2.parse(table, payload))
        }
        check(replies.keys.map { Request(if (it[0].toInt() == 0x5B) Table.ONE else Table.TWO, it) })
    }

    /** Every listening-mode switch both kinds of headset can make, and the replies they send back. */
    @Test
    fun listeningModes() {
        val a = V2Functions(t1 = setOf(0xE4, 0xE5, 0xE8, 0xE9))
        val b = V2Functions(t1 = setOf(0xEB, 0xEC))
        val sa = ListeningModes(bgm = false, cinema = false, voiceBoost = false, leakageReduction = false)
        val sb = ListeningModes(bgm = false, upmix = 0)
        val writes = listOf(a to sa, b to sb).flatMap { (f, s) ->
            s.available.flatMap { from -> s.available.flatMap { to -> Audio.listening(f, s.with(from), s.with(to)) } } +
                Audio.listening(f, s.with(ListeningMode.BGM), s.with(ListeningMode.BGM).copy(roomSize = 2))
        }
        check(writes)

        // Off first, then on.
        assertEquals(listOf("e8 03 01 00", "e8 06 00"),
            Audio.listening(a, sa.with(ListeningMode.BGM), sa.with(ListeningMode.VOICE_BOOST)).map { it.payload.hex() })
        assertEquals(listOf("e8 0a 02"), Audio.listening(b, sb.with(ListeningMode.CINEMA), sb.with(ListeningMode.GAME)).map { it.payload.hex() })

        val replies = listOf(
            bytes(0xE7, 0x03, 0x00, 0x02), bytes(0xE9, 0x09, 0x01, 0x00), bytes(0xE7, 0x04, 0x00), bytes(0xE9, 0x0A, 0x03),
            bytes(0xE7, 0x06, 0x00), bytes(0xE9, 0x07, 0x01),
        )
        check(replies.map { Request(Table.ONE, it) })
        val state = replies.fold(ListeningModes()) { s, p -> (MdrV2.parse(Table.ONE, p) as MdrEvent.Listening).update(s) }
        assertEquals(ListeningModes(bgm = false, roomSize = 0, cinema = true, upmix = 3, voiceBoost = true, leakageReduction = false), state)
    }

    private fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }

    /** Max Volume Control, the UPDT identity and the LE Audio connection priority. */
    @Test
    fun volumeLimitIdentityAndLeAudio() {
        val t1 = listOf(
            bytes(0x37, 0x02, 3, *"HPC".codes(), 2, *"01".codes(), 2, *"JP".codes(), 2, *"ja".codes(), 4, *"1234".codes(), 0, 0, 2, *"01".codes()),
            bytes(0xE7, 0x05, 0x02),
        ).map { Request(Table.ONE, it) }
        val t2 = listOf(bytes(0x53, 0x05, 0x00), bytes(0x55, 0x05, 0x01), bytes(0x57, 0x05, 0x04), bytes(0x59, 0x05, 0x00)).map { Request(Table.TWO, it) }
        check(t1 + t2)
        assertEquals(
            MdrEvent.Several(listOf(MdrEvent.UpdateParam(2, "HPC"), MdrEvent.UpdateParam(3, "01"), MdrEvent.UpdateParam(4, "JP"),
                MdrEvent.UpdateParam(5, "ja"), MdrEvent.SerialNumber("1234"))),
            MdrV2.parse(Table.ONE, t1[0].payload),
        )
        assertEquals(MdrEvent.SoundQualityMode(false, lowLatency = true), MdrV2.parse(Table.ONE, t1[1].payload))
        assertEquals(listOf(MdrEvent.MaxVolumeOn(true), MdrEvent.MaxVolumeOn(false), MdrEvent.MaxVolumeLevel(4), MdrEvent.MaxVolumeLevel(0)),
            t2.map { MdrV2.parse(Table.TWO, it.payload) })
    }

    private fun String.codes() = map { it.code }.toIntArray()
}
