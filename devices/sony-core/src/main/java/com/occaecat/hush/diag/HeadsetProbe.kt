package com.occaecat.hush.diag

import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.hex
import com.occaecat.hush.mdr.v1.Command1
import com.occaecat.hush.mdr.v1.Command2
import com.occaecat.hush.mdr.v1.FunctionType
import com.occaecat.hush.mdr.v1.Init
import com.occaecat.hush.mdr.v1.MdrV1
import kotlinx.coroutines.delay

/**
 * The self test for headsets nobody has checked with Hush: re-sends the whole session start and notes what each
 * request got back, then switches every setting the headset offers to another value and back, and checks the headset
 * confirmed each change (its notify, then a fresh read of the setting). Settings that make the headset reconnect or
 * need the user (buttons, sound quality, multipoint, the optimizer) are left alone. The report and capture are
 * scrubbed ([Scrubber]) and meant to be mailed to the developer.
 */
class HeadsetProbe(private val vm: HeadphonesController) {
    enum class Verdict { PASS, PARTIAL, FAIL, SKIPPED }

    data class Check(val name: String, val verdict: Verdict, val notes: List<String>)

    data class Answer(val request: Request, val acked: Boolean, val replies: List<SessionLog.Entry>)

    class Report(
        val model: String?,
        val firmware: String?,
        val checks: List<Check>,
        /** Human-readable: headset, requests and replies, checks. */
        val text: String,
        /** Every payload of the session, in order with times (`+ms -> T1 hex`). */
        val capture: String,
        /** Each distinct reply once, in the format of the simulator's profiles (test resources mdr/…/replies.txt). */
        val replies: String,
    ) {
        val passed get() = checks.count { it.verdict == Verdict.PASS }
        val failed get() = checks.count { it.verdict == Verdict.FAIL || it.verdict == Verdict.PARTIAL }
        val skipped get() = checks.count { it.verdict == Verdict.SKIPPED }
        val fileStem get() = listOfNotNull("hush", model ?: "headset", firmware).joinToString("-") { it.replace(Regex("[^A-Za-z0-9._-]"), "_") }
    }

    private val log get() = vm.sessionLog
    private val state get() = vm.state.value

    private val connected get() = state.phase == Phase.CONNECTED

    /** The check list for the current state: what [run] will go through, for the progress count. */
    fun plan(): List<Pair<String, suspend () -> Check>> = buildChecks(state)

    /**
     * Runs everything; [progress] gets (done, total, what is running). Without a session (a v2 headset Hush gave up
     * on, a dropped link) only the report of what the log already holds is made.
     */
    suspend fun run(about: String, progress: (Int, Int, String) -> Unit = { _, _, _ -> }): Report {
        if (!connected) return report(about, emptyList(), emptyList(), "No session: only what Hush saw before is included.")
        // The checks write v1 commands; on a v2 headset only the session log goes into the report.
        if (state.protocolV2) return report(about, emptyList(), emptyList(), "Protocol v2: the checks are v1-only, so only the session log is included.")
        val requests = Init.handshake() + Init.plan(state.protocolVersion ?: 0, state.functions)
        val checks = plan()
        val total = requests.size + checks.size
        val answers = requests.mapIndexed { i, req ->
            progress(i, total, "Reading ${nameOf(req.table, req.payload[0].u) ?: req.toString()}")
            ask(req)
        }
        // Let late notifies of the session start land before anything is switched.
        delay(500)
        val results = checks.mapIndexed { i, (name, check) ->
            progress(requests.size + i, total, name)
            if (!connected) Check(name, Verdict.SKIPPED, listOf("the session ended")) else check()
        }
        progress(total, total, "Done")
        return report(about, answers, results, null)
    }

    /** What is in the log now, without touching the headset. */
    fun collect(about: String): Report = report(about, emptyList(), emptyList(), "Only what Hush saw; the check was not run.")

    // ---- requests and replies ----

    private suspend fun ask(req: Request): Answer {
        val mark = log.lastSeq
        val acked = vm.sendRaw(req)
        val ret = counterpart(req.table, req.payload[0].u, "GET", "RET")
        if (!acked || ret == null) return Answer(req, acked, emptyList())
        val param = req.payload.getOrNull(1)?.u
        val reply = awaitRx(mark, REPLY_MS) { it.table == req.table && it.code == ret && (param == null || it.param == param) }
        // A reply with another first parameter still says something (and is what a wrong guess here looks like).
        val other = if (reply == null) log.since(mark).filter { !it.out && it.note == null && it.table == req.table && it.code == ret } else emptyList()
        return Answer(req, true, listOfNotNull(reply) + other)
    }

    private suspend fun awaitRx(mark: Long, timeoutMs: Long, match: (SessionLog.Entry) -> Boolean): SessionLog.Entry? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            log.since(mark).firstOrNull { !it.out && it.note == null && match(it) }?.let { return it }
            if (System.currentTimeMillis() >= deadline || !connected) return null
            delay(POLL_MS)
        }
    }

    /** SETs the controller sent after [mark]; it sends from its own coroutine and debounces NC and EQ. */
    private suspend fun awaitSets(mark: Long): List<SessionLog.Entry> {
        val deadline = System.currentTimeMillis() + SEND_MS
        while (System.currentTimeMillis() < deadline) {
            val sets = log.since(mark).filter { it.out && isSet(it) }
            if (sets.isNotEmpty()) {
                delay(DEBOUNCE_MS)
                return log.since(mark).filter { it.out && isSet(it) }
            }
            delay(POLL_MS)
        }
        return emptyList()
    }

    private fun isSet(e: SessionLog.Entry) = nameOf(e.table, e.code)?.contains("_SET_") == true

    /** The session's own request for that GET: it carries the parameters the headset wants. */
    private fun lastRequest(table: Table, code: Int, param: Int): Request? =
        log.snapshot().lastOrNull { it.out && it.table == table && it.code == code && it.param == param }?.let { Request(it.table, it.payload) }

    // ---- function checks ----

    private fun buildChecks(s: HeadphonesState): List<Pair<String, suspend () -> Check>> = buildList {
        fun <T : Any> change(name: String, read: (HeadphonesState) -> T?, target: (T) -> T?, apply: (T) -> Unit) {
            val now = read(s) ?: return
            if (target(now) == null) return
            add(name to suspend { roundTrip(name, read, target, apply) })
        }
        fun toggle(name: String, read: (HeadphonesState) -> Boolean?, apply: (Boolean) -> Unit) = change(name, read, { !it }, apply)

        val modes = s.noiseModes()
        if (s.ncAsm != null) for (mode in modes) {
            change("Noise control: ${mode.label}", { it.ncAsm?.mode }, { if (it == mode) null else mode }, vm::setAmbientMode)
        }
        val steps = s.ncAsmCaps?.asmSteps ?: 0
        if (AmbientMode.AMBIENT in modes && steps > 1) s.ncAsm?.let { nc ->
            val origin = nc.mode
            val level = nc.ambientLevel
            change("Ambient sound level", { st -> st.ncAsm?.let { it.mode to (if (it.mode == AmbientMode.AMBIENT) it.ambientLevel else null) } },
                { (mode, _) -> if (mode == origin) AmbientMode.AMBIENT to (if (level > 1) level - 1 else level + 1) else null },
                { (mode, l) -> vm.setNoiseControl(mode, l ?: level) })
        }
        val presets = s.eqCaps?.presets.orEmpty().map { it.id }
        change("Equalizer preset", { it.eq?.presetId }, { cur ->
            presets.firstOrNull { it != cur && it !in HeadphonesController.WRITABLE_PRESETS } ?: presets.firstOrNull { it != cur }
        }, vm::selectEqPreset)
        s.eq?.takeIf { s.eqCustomizable && it.presetId in HeadphonesController.WRITABLE_PRESETS && it.bands.isNotEmpty() }?.let { eq ->
            change("Equalizer bands", { st -> st.eq?.takeIf { it.presetId == eq.presetId }?.bands }, { bands ->
                bands.toMutableList().also { it[0] = if (it[0] < 2 * HeadphonesController.EQ_FLAT) it[0] + 1 else it[0] - 1 }
            }, vm::applyEqBands)
        }
        s.ebb?.let { ebb ->
            change("Clear Bass", { it.ebb?.level }, { l -> (if (l + 1 in ebb.range) l + 1 else l - 1).takeIf { it in ebb.range } }, vm::setEbb)
        }
        if (s.vptPresets.size > 1) change("Surround (VPT)", { it.vpt }, { cur -> s.vptPresets.map { it.id }.firstOrNull { it != cur } }, vm::setVpt)
        toggle("DSEE", { it.dseeAuto }, vm::setDsee)
        toggle("Speak-to-Chat", { it.speakToChat }, vm::setSpeakToChat)
        if (s.pauseWhenRemovedAvailable) toggle("Pause when taken off", { it.pauseWhenRemoved }, vm::setPauseWhenRemoved)
        toggle("Power saving", { it.powerSaving }, vm::setPowerSaving)
        toggle("Vibration", { it.vibrator }, vm::setVibrator)
        toggle("Training mode", { it.trainingMode }, vm::setTrainingMode)
        if (s.voiceGuidanceCaps?.onOff != false) toggle("Voice guidance", { it.voiceGuidance }, vm::setVoiceGuidance)
        change("Automatic power off", { it.autoPowerOff?.first }, { cur -> s.autoPowerOffOptions.firstOrNull { it != cur } }, vm::setAutoPowerOff)
        for (g in s.generalSettings.values.sortedBy { it.id }) {
            if (!g.isBoolean || g.title == "MULTIPOINT_SETTING" || s.generalEnabled[g.id] == false) continue
            toggle("Setting ${g.title} (0x%02X)".format(g.id), { it.generalValues[g.id] }) { vm.setGeneralSetting(g.id, it) }
        }
        change("Volume", { it.volume }, { v -> if (v > 0) v - 1 else v + 1 }, vm::setVolume)
    }

    private suspend fun <T : Any> roundTrip(name: String, read: (HeadphonesState) -> T?, target: (T) -> T?, apply: (T) -> Unit): Check {
        val original = read(state) ?: return Check(name, Verdict.SKIPPED, listOf("no value from the headset"))
        val other = target(original) ?: return Check(name, Verdict.SKIPPED, listOf("nothing else to switch to"))
        val notes = mutableListOf<String>()
        val there = step(other, read, apply, notes)
        val back = step(original, read, apply, notes)
        val verdict = when {
            there && back -> Verdict.PASS
            there || back -> Verdict.PARTIAL
            else -> Verdict.FAIL
        }
        return Check(name, verdict, notes)
    }

    /** One change: sent, confirmed by a notify or a read back, and the state shows it. */
    private suspend fun <T : Any> step(value: T, read: (HeadphonesState) -> T?, apply: (T) -> Unit, notes: MutableList<String>): Boolean {
        val mark = log.lastSeq
        notes += "set $value"
        apply(value)
        val sets = awaitSets(mark)
        if (sets.isEmpty()) {
            notes += "  nothing was sent"
            return false
        }
        var confirmed = false
        for (set in sets) {
            notes += "  -> ${describe(set)}"
            val ntfy = counterpart(set.table, set.code, "SET", "NTFY")
            val n = ntfy?.let { code -> awaitRx(mark, NOTIFY_MS) { it.table == set.table && it.code == code && it.param == set.param } }
            notes += if (n != null) "  <- ${describe(n)}" else if (ntfy == null) "  (no notify in the protocol)" else "  no notify"
            if (n != null) confirmed = true
            val get = counterpart(set.table, set.code, "SET", "GET") ?: continue
            val req = lastRequest(set.table, get, set.param) ?: continue
            val before = log.lastSeq
            val ret = counterpart(set.table, get, "GET", "RET")
            val r = if (vm.sendRaw(req) && ret != null) awaitRx(before, REPLY_MS) { it.table == set.table && it.code == ret && it.param == set.param } else null
            notes += if (r != null) "  read back <- ${describe(r)}" else "  read back: no reply"
            if (r != null) confirmed = true
        }
        delay(SETTLE_MS)
        val now = read(state)
        if (now != value) notes += "  Hush shows $now"
        if (!confirmed) notes += "  the headset never confirmed it"
        return confirmed && now == value
    }

    // ---- the report ----

    private fun report(about: String, answers: List<Answer>, checks: List<Check>, note: String?): Report {
        val s = state
        val scrub = Scrubber(s)
        val entries = log.snapshot()
        val clean = entries.associate { it.seq to scrub.payload(it.table, it.payload) }
        fun hexOf(e: SessionLog.Entry) = "${if (e.table == Table.TWO) "T2" else "T1"} ${clean[e.seq]!!.hex()}"
        fun event(e: SessionLog.Entry) =
            if (s.protocolV2) com.occaecat.hush.mdr.v2.MdrV2.parse(e.table, clean[e.seq]!!) else MdrV1.parse(e.table, clean[e.seq]!!)

        val text = buildString {
            appendLine("Hush headset report")
            appendLine(about)
            note?.let { appendLine(it) }
            appendLine()
            appendLine("== Headset ==")
            appendLine("Model: ${s.modelName ?: "?"}   firmware: ${s.firmware ?: "?"}   protocol: ${s.protocolVersion?.let { "0x%04X".format(it) } ?: "?"}" +
                (if (s.protocolV2) " (v2)" else ""))
            appendLine("Series: ${s.modelSeries?.let { "0x%02X".format(it) } ?: "?"}   color: ${s.modelColor?.let { "0x%02X".format(it) } ?: "?"}   phase: ${s.phase}")
            appendLine("Functions: " + s.functions.sorted().joinToString(", ") { c -> FunctionType.of(c)?.let { "${it.name}(%02X)".format(c) } ?: "UNKNOWN(%02X)".format(c) })
            s.v2Functions?.let { f ->
                appendLine("v2 table 1: " + f.t1.sorted().joinToString(", ") { c -> com.occaecat.hush.mdr.v2.FunctionType1.of(c)?.let { "${it.name}(%02X)".format(c) } ?: "UNKNOWN(%02X)".format(c) })
                appendLine("v2 table 2: " + f.t2.sorted().joinToString(", ") { c -> com.occaecat.hush.mdr.v2.FunctionType2.of(c)?.let { "${it.name}(%02X)".format(c) } ?: "UNKNOWN(%02X)".format(c) })
            }
            listOf(
                "Battery" to (s.battery?.let { "$it%${if (s.charging) " charging" else ""}" }),
                "Earbuds" to listOfNotNull(s.leftBattery, s.rightBattery, s.caseBattery).joinToString().ifEmpty { null },
                "Codec" to s.codec?.label,
                "NC/ASM caps" to s.ncAsmCaps?.toString(), "NC/ASM" to s.ncAsm?.toString(), "Sense" to s.senseAvailable.toString(),
                "EQ caps" to s.eqCaps?.toString(), "EQ bands" to s.eqBands.ifEmpty { null }?.toString(), "EQ" to s.eq?.toString(),
                "Clear Bass" to s.ebb?.toString(), "VPT" to s.vptPresets.ifEmpty { null }?.let { "$it, active ${s.vpt}" },
                "Sound position" to s.soundPositionType?.let { "type $it, active ${s.soundPosition}" },
                "Upscaling" to s.upscalingType?.let { "type $it, auto ${s.dseeAuto}" },
                "Speak-to-Chat" to s.speakToChat?.let { "$it ${s.speakToChatDetail ?: ""}" },
                "Pause when taken off" to s.pauseWhenRemoved?.toString(),
                "Auto power off" to s.autoPowerOff?.let { "$it of ${s.autoPowerOffOptions}" },
                "Assignable keys" to s.assignableKeys.ifEmpty { null }?.let { "$it, set ${s.assignable}" },
                "Custom button" to s.customButton?.toString(), "Sound quality prefers connection" to s.soundQualityPreferConnection?.toString(),
                "General settings" to s.generalSettings.values.ifEmpty { null }?.joinToString { g ->
                    "${g.title}(0x%02X)=${s.generalValues[g.id] ?: s.generalListValues[g.id]}${if (s.generalEnabled[g.id] == false) " disabled" else ""}".format(g.id)
                },
                "Optimizer" to s.optimizerCaps?.toString(),
                "Voice guidance" to s.voiceGuidanceCaps?.let { "$it, on ${s.voiceGuidance}, language ${s.voiceGuidanceLanguage}" },
                "Power saving / vibration / training" to listOf(s.powerSaving, s.vibrator, s.trainingMode).joinToString(" / "),
                "Volume" to s.volume?.let { "$it of ${s.volumeSteps}" },
                "Sources listed" to s.peers?.size?.toString(),
            ).forEach { (k, v) -> if (v != null) appendLine("$k: $v") }
            s.error?.let { appendLine("Error: $it") }

            if (answers.isNotEmpty()) {
                appendLine()
                appendLine("== Session start, sent again (${answers.count { it.replies.isNotEmpty() }} of ${answers.size} answered) ==")
                for (a in answers) {
                    val name = nameOf(a.request.table, a.request.payload[0].u) ?: "?"
                    val head = when {
                        !a.acked -> "NO ACK"
                        a.replies.isEmpty() -> if (counterpart(a.request.table, a.request.payload[0].u, "GET", "RET") == null) "SENT  " else "NO REPLY"
                        else -> "OK    "
                    }
                    appendLine("$head -> $name ${a.request}")
                    for (r in a.replies) appendLine("         <- ${hexOf(r)}  ${event(r).short()}")
                }
            }

            val received = entries.filter { !it.out && it.note == null }
            val unknown = received.filter { event(it) is MdrEvent.Unknown }.distinctBy { hexOf(it) }
            appendLine()
            appendLine("== Replies Hush could not read (${unknown.size}) ==")
            for (e in unknown) appendLine("${nameOf(e.table, e.code) ?: "unknown opcode"}  ${hexOf(e)}")

            if (checks.isNotEmpty()) {
                appendLine()
                appendLine("== Function checks (${checks.count { it.verdict == Verdict.PASS }} passed, " +
                    "${checks.count { it.verdict == Verdict.FAIL || it.verdict == Verdict.PARTIAL }} failed) ==")
                for (c in checks) {
                    appendLine("${c.verdict.name.padEnd(8)} ${c.name}")
                    for (n in c.notes) appendLine("         $n")
                }
                appendLine()
                appendLine("Not switched by the check: buttons, sound quality mode, multipoint and list settings (the headset " +
                    "reconnects), the NC optimizer, playback, pairing and power off.")
            }
        }.let(scrub::text)

        val capture = buildString {
            appendLine("# Hush capture: ${s.modelName ?: "?"} ${s.firmware ?: ""}; ms since connecting, -> phone to headset, <- headset to phone.")
            appendLine("# Addresses, names, serial and ids are replaced.")
            for (e in entries) {
                val t = "+%7d".format(e.atMs)
                if (e.note != null) appendLine("$t # ${e.note}") else appendLine("$t ${if (e.out) "->" else "<-"} ${hexOf(e)}")
            }
        }.let(scrub::text)

        val replies = buildString {
            appendLine("# ${s.modelName ?: "?"} firmware ${s.firmware ?: "?"}: every distinct reply Hush got (table, payload hex), from the self test.")
            appendLine("# Addresses, names, serial and ids are replaced.")
            entries.filter { !it.out && it.note == null }.map(::hexOf).distinct().forEach(::appendLine)
        }.let(scrub::text)

        return Report(s.modelName, s.firmware, checks, text, capture, replies)
    }

    private fun describe(e: SessionLog.Entry) = "${nameOf(e.table, e.code) ?: "?"} ${if (e.table == Table.TWO) "T2 " else ""}${e.payload.hex()}"

    private fun MdrEvent.short() = toString().let { if (it.length > 160) it.take(160) + "…" else it }

    private val AmbientMode.label get() = when (this) {
        AmbientMode.NOISE_CANCELLING -> "noise cancelling"
        AmbientMode.AMBIENT -> "ambient sound"
        AmbientMode.OFF -> "off"
    }

    companion object {
        private const val REPLY_MS = 1_500L
        private const val NOTIFY_MS = 2_500L
        private const val SEND_MS = 2_000L
        private const val DEBOUNCE_MS = 250L
        private const val SETTLE_MS = 200L
        private const val POLL_MS = 40L

        private val Byte.u get() = toInt() and 0xFF

        fun nameOf(table: Table, code: Int): String? = if (table == Table.ONE) Command1.of(code)?.name else Command2.of(code)?.name

        private fun codeOf(table: Table, name: String): Int? =
            if (table == Table.ONE) Command1.entries.firstOrNull { it.name == name }?.code else Command2.entries.firstOrNull { it.name == name }?.code

        /** The same command's other side, by Sony's names: SET → NTFY, GET → RET, SET → GET. */
        fun counterpart(table: Table, code: Int, from: String, to: String): Int? =
            nameOf(table, code)?.takeIf { "_${from}_" in it }?.let { codeOf(table, it.replace("_${from}_", "_${to}_")) }
    }
}
