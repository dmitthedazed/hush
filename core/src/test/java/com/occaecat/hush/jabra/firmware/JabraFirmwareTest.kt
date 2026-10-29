package com.occaecat.hush.jabra.firmware

import com.occaecat.hush.jabra.GnpClient
import com.occaecat.hush.jabra.GnpFirmwareSession
import com.occaecat.hush.jabra.GnpFrame
import com.occaecat.hush.jabra.JabraProtocol
import com.occaecat.hush.jabra.JabraRejectedException
import com.occaecat.hush.jabra.u
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class JabraFirmwareTest {
    @Test fun realNonceCrcIsValidatedAndBytesAreOwned() {
        val bytes = ByteArray(16)
        assertEquals(0xffffffffL, JabraFirmwareCrc.calculate(bytes))
        val file = JabraFirmwareFile.verified(0, version, 0xffffffffL, bytes)
        bytes[0] = 1
        assertArrayEquals(ByteArray(16), file.chunk(0).copyOfRange(4, 20))
        assertTrue(runCatching { JabraFirmwareFile.verified(0, version, 0xffffffffL, bytes) }.isFailure)
        assertTrue(runCatching { JabraFirmwareCrc.calculate(ByteArray(3)) }.isFailure)
    }

    @Test fun signedHeaderIsExcludedFromChecksumButIncludedInTransfer() {
        val bytes = ByteArray(52) { 0x77 } + ByteArray(16)
        val file = JabraFirmwareFile.verified(0, version, 0xffffffffL, bytes, crcOffset = 52)
        assertArrayEquals(ByteArray(52) { 0x77 }, file.chunk(0).copyOfRange(4, 56))
        assertEquals(68, file.size)
    }

    @Test fun packageRejectsMissingDuplicateOrWrongModelPartitionsAndLanguages() {
        val file = file(0)
        assertTrue(runCatching { pack(9421, listOf(file)) }.isFailure)
        assertTrue(runCatching { pack(11846, listOf(file, file)) }.isFailure)
        assertTrue(runCatching { pack(11846, listOf(file(1))) }.isFailure)
        assertTrue(runCatching { pack(11846, listOf(file(0, language = 0x0407))) }.isFailure)
        val mutable = mutableListOf(file)
        val checked = pack(11846, mutable)
        mutable.clear()
        assertEquals(1, checked.files.size)
    }

    @Test fun readinessUsesLittleEndianLanguageAndFreshBatteryAndBlocksUnknownComponents() = runBlocking {
        val fake = FakeFirmware()
        val updater = JabraFirmwareUpdater(fake)
        assertTrue(updater.readReadiness(11846).ready)
        assertEquals(0x0409, updater.readReadiness(11846).languageId)
        fake.battery = byteArrayOf(0, 90)
        val unknown = updater.readReadiness(11846)
        assertFalse(unknown.ready)
        assertTrue(unknown.blockers.any { "Both earbuds" in it })
        fake.battery = battery(49, 80, 80)
        assertFalse(updater.readReadiness(11846).ready)
        assertTrue(fake.writes.isEmpty())
    }

    @Test fun soleAvailableLanguageFallbackIsBoundedAndDoesNotGuess() = runBlocking {
        val fake = FakeFirmware().apply { languageUnsupported = true }
        assertEquals(0x0409, JabraFirmwareUpdater(fake).readReadiness(11846).languageId)
        fake.availableLanguages = byteArrayOf(2, 9, 4, 7, 4)
        val unknown = JabraFirmwareUpdater(fake).readReadiness(11846)
        assertNull(unknown.languageId)
        assertFalse(unknown.ready)
    }

    @Test fun mismatchedIdentityProtocolLockLanguageAndLowBatteryNeverWrite() = runBlocking {
        for (alter in listOf<(FakeFirmware) -> Unit>(
            { it.pid = 9421 }, { it.protocols = byteArrayOf(6) }, { it.lock = byteArrayOf(1) },
            { it.language = byteArrayOf(7, 4) }, { it.battery = battery(80, 49, 80) },
            { it.installedVersion = "5.0.0" }, { it.installedVersion = "4.6.0" },
        )) {
            val fake = FakeFirmware().also(alter)
            assertTrue(runCatching { JabraFirmwareUpdater(fake).transfer(pack(), {}) }.isFailure)
            assertTrue(fake.writes.isEmpty())
            assertFalse(fake.didClose)
        }
    }

    @Test fun readinessDoesNotInferUnadvertisedCommandsFromGenericXml() = runBlocking {
        for (pid in listOf(11846, 9421)) {
            val fake = FakeFirmware().apply { this.pid = pid }
            val readiness = JabraFirmwareUpdater(fake).readReadiness(pid)
            assertTrue(readiness.ready)
            assertNull(readiness.startConditionFlags)
            assertTrue(fake.writes.isEmpty())
        }
    }

    @Test fun transfersChunksUnderCreditsAndConfirmsVersionBeforeCommit() = runBlocking {
        val fake = FakeFirmware()
        val firmware = pack(files = listOf(file(0, size = 218)))
        val progress = mutableListOf<JabraFirmwareProgress>()
        assertEquals(JabraFirmwareResult.AWAITING_CASE, JabraFirmwareUpdater(fake).transfer(firmware, progress::add))
        assertEquals(listOf(0, 1, 2, 3, 4), fake.chunks.map { it.second[0].u() })
        assertEquals(listOf(52, 52, 52, 52, 10), fake.chunks.map { it.second[2].u() })
        assertTrue(fake.chunks.all { it.second[3] == 0.toByte() })
        assertEquals(JabraFirmwareUpdater.GO, fake.writes.last().first)
        assertTrue(fake.writes.last().second.isEmpty())
        assertArrayEquals(byteArrayOf(4, 6, 0, 9, 4, 0, 0, 0), fake.writes.single { it.first == JabraFirmwareUpdater.VERSION }.second)
        assertTrue(progress.zipWithNext().all { (a, b) -> a.percent <= b.percent })
        assertEquals(JabraFirmwareStage.AWAITING_CASE, progress.last().stage)
        assertFalse(fake.didClose)
    }

    @Test fun all85tPartitionsAreUploadedAndRecheckedInPackageOrder() = runBlocking {
        val fake = FakeFirmware().apply { pid = 9421 }
        val order = listOf(0, 1, 4, 5, 6, 7, 9, 254)
        JabraFirmwareUpdater(fake).transfer(pack(9421, order.map { file(it) }), {})
        assertEquals(order + order, fake.writes.filter { it.first == JabraFirmwareUpdater.PARTITION }.map { it.second.single().u() })
        assertEquals(order, fake.chunks.map { it.first })
        assertEquals(8, fake.writes.count { it.first == JabraFirmwareUpdater.ERASE })
        assertEquals(1, fake.writes.count { it.first == JabraFirmwareUpdater.GO })
    }

    @Test fun crcCommandUsesWordSwappedChecksumAndLittleEndianCounters() {
        val part = file(0, size = 218)
        val payload = JabraFirmwareUpdater(FakeFirmware()).crcPayload(part, 10)
        assertArrayEquals(byteArrayOf((part.crc ushr 16).toByte(), (part.crc ushr 24).toByte(),
            part.crc.toByte(), (part.crc ushr 8).toByte(), 5, 0, 10, 0, 0, 0), payload)
    }

    @Test fun repeatedIdenticalCreditDoesNotSendASecondBatch() = runBlocking {
        val fake = FakeFirmware().apply { duplicateFirstCredit = true }
        JabraFirmwareUpdater(fake).transfer(pack(files = listOf(file(0, 218))), {})
        assertEquals(5, fake.chunks.size)
        assertEquals(5, fake.chunks.map { it.second[0].u() }.distinct().size)
    }

    @Test fun creditSequenceWrapWithIdenticalPayloadDoesNotDiscardValidGrants() = runBlocking {
        val fake = FakeFirmware().apply { creditSequence = 254 }
        JabraFirmwareUpdater(fake).transfer(pack(files = listOf(file(0, 218))), {})
        assertEquals(listOf(0, 1, 2, 3, 4), fake.chunks.map { it.second[0].u() })
        assertEquals(0, fake.creditSequence)
    }

    @Test fun firstMutatingCommandFollowsNonCancellableStageAnnouncement() = runBlocking {
        val fake = FakeFirmware()
        var stage = JabraFirmwareStage.PREPARING
        fake.beforeWrite = { assertTrue(stage != JabraFirmwareStage.PREPARING) }
        JabraFirmwareUpdater(fake).transfer(pack()) { stage = it.stage }
        Unit
    }

    @Test fun verificationErrorMalformedCreditAndEarlySuccessStopWithoutGo() = runBlocking {
        for (alter in listOf<(FakeFirmware) -> Unit>(
            { it.verifyStatus = 1 }, { it.malformedCredit = true }, { it.earlyVerify = true },
            { it.wrongEndpoint = true }, { it.wrongStagedVersion = true }, { it.corruptFinalRead = true },
        )) {
            val fake = FakeFirmware().also(alter)
            assertTrue(runCatching { JabraFirmwareUpdater(fake).transfer(pack(files = listOf(file(0, 218))), {}) }.isFailure)
            assertTrue(fake.didClose)
            assertTrue(fake.writes.none { it.first == JabraFirmwareUpdater.GO })
        }
    }

    @Test fun missingAckDoesNotEraseOrContinueAndIsNeverRetried() = runBlocking {
        val fake = FakeFirmware().apply { rejectTopic = JabraFirmwareUpdater.PARTITION }
        assertTrue(runCatching { JabraFirmwareUpdater(fake).transfer(pack(), {}) }.isFailure)
        assertEquals(listOf(JabraFirmwareUpdater.PARTITION), fake.writes.map { it.first })
        assertTrue(fake.didClose)
    }

    @Test fun missingVerificationTimesOutClosesSessionAndNeverCommits() = runBlocking {
        val fake = FakeFirmware().apply { omitVerification = true }
        val updater = JabraFirmwareUpdater(fake, JabraFirmwareUpdater.Timeouts(eventMs = 10))
        assertTrue(runCatching { updater.transfer(pack(), {}) }.isFailure)
        assertTrue(fake.didClose)
        assertTrue(fake.writes.none { it.first == JabraFirmwareUpdater.GO })
    }

    @Test fun cancellationDuringTransferClosesSessionWithoutCommit() = runBlocking {
        val fake = FakeFirmware().apply { blockData = true }
        val task = async(start = CoroutineStart.UNDISPATCHED) { JabraFirmwareUpdater(fake).transfer(pack(), {}) }
        fake.dataStarted.await()
        task.cancelAndJoin()
        assertTrue(fake.didClose)
        assertTrue(fake.writes.none { it.first == JabraFirmwareUpdater.GO })
    }

    @Test fun missingFinalAckNeverReportsAwaitingCaseOrRetriesCommit() = runBlocking {
        val fake = FakeFirmware().apply { rejectTopic = JabraFirmwareUpdater.GO }
        val progress = mutableListOf<JabraFirmwareProgress>()
        assertTrue(runCatching { JabraFirmwareUpdater(fake).transfer(pack(), progress::add) }.isFailure)
        assertTrue(progress.none { it.stage == JabraFirmwareStage.AWAITING_CASE })
        assertEquals(1, fake.writes.count { it.first == JabraFirmwareUpdater.GO })
        assertTrue(fake.didClose)
    }

    private class FakeFirmware : GnpClient, GnpFirmwareSession {
        override val closed = CompletableDeferred<Throwable?>()
        override val events = MutableSharedFlow<GnpFrame>()
        val writes = mutableListOf<Pair<Int, ByteArray>>()
        val chunks = mutableListOf<Pair<Int, ByteArray>>()
        val dataStarted = CompletableDeferred<Unit>()
        private val queue = Channel<GnpFrame>(Channel.UNLIMITED)
        private val versions = mutableMapOf<Int, ByteArray>()
        private val selects = mutableMapOf<Int, Int>()
        private var partition = 0
        private var sequence = 0
        var creditSequence = 0
        private var expectedChunks = 0
        private var preload = 0
        private var sent = 0
        var pid = 11846
        var installedVersion = "4.5.0"
        var protocols = byteArrayOf(3, 7)
        var lock = byteArrayOf(0)
        var language = byteArrayOf(9, 4)
        var languageUnsupported = false
        var availableLanguages = byteArrayOf(1, 9, 4)
        var battery = battery(90, 90, 90)
        var duplicateFirstCredit = false
        var malformedCredit = false
        var earlyVerify = false
        var wrongEndpoint = false
        var verifyStatus = 0
        var wrongStagedVersion = false
        var corruptFinalRead = false
        var rejectTopic = -1
        var omitVerification = false
        var blockData = false
        var didClose = false
        var beforeWrite: () -> Unit = {}
        override suspend fun open() = Unit
        override suspend fun read(topic: Int, payload: ByteArray) = read(topic, payload, 5_000)
        override suspend fun write(topic: Int, payload: ByteArray) { error("Updater must use strict writes") }
        override suspend fun <T> firmwareSession(block: suspend (GnpFirmwareSession) -> T): T = block(this)
        override suspend fun read(topic: Int, payload: ByteArray, timeoutMs: Long): ByteArray = when (topic) {
            JabraProtocol.PRODUCT_ID -> byteArrayOf(pid.toByte(), (pid ushr 8).toByte())
            JabraProtocol.FIRMWARE -> byteArrayOf(installedVersion.length.toByte()) + installedVersion.toByteArray()
            JabraProtocol.BATTERY -> { assertArrayEquals(byteArrayOf(1), payload); battery }
            JabraFirmwareUpdater.PROTOCOLS -> protocols
            JabraFirmwareUpdater.LOCK -> lock
            0x0f35 -> error("Neither current model advertises this generic command")
            JabraFirmwareUpdater.LANGUAGE -> if (languageUnsupported) throw JabraRejectedException(0xfb) else language
            JabraFirmwareUpdater.AVAILABLE_LANGUAGES -> availableLanguages
            JabraFirmwareUpdater.VERSION -> if (corruptFinalRead && selects.getValue(partition) > 1) ByteArray(5) else versions[partition] ?: ByteArray(5)
            else -> error("Unexpected read ${topic.toString(16)}")
        }
        override suspend fun writeStrict(topic: Int, payload: ByteArray, timeoutMs: Long) {
            beforeWrite()
            writes += topic to payload.copyOf()
            if (topic == rejectTopic) throw IOException("Missing ACK")
            when (topic) {
                JabraFirmwareUpdater.PARTITION -> {
                    partition = payload.single().u()
                    selects[partition] = selects.getOrDefault(partition, 0) + 1
                }
                JabraFirmwareUpdater.ERASE -> emit(JabraFirmwareUpdater.ERASED, byteArrayOf())
                JabraFirmwareUpdater.CRC -> {
                    expectedChunks = payload[4].u() or (payload[5].u() shl 8)
                    preload = payload[6].u() or (payload[7].u() shl 8)
                    sent = 0
                }
                JabraFirmwareUpdater.VERSION -> versions[partition] = if (wrongStagedVersion) ByteArray(5) else payload.copyOfRange(0, 5)
                JabraFirmwareUpdater.GO -> Unit
                else -> error("Unexpected write")
            }
        }
        override suspend fun sendEvent(topic: Int, payload: ByteArray) {
            check(topic == JabraFirmwareUpdater.DATA)
            dataStarted.complete(Unit)
            if (blockData) awaitCancellation()
            chunks += partition to payload.copyOf()
            assertEquals(sent, payload[0].u() or (payload[1].u() shl 8))
            sent++
            if (earlyVerify || sent == expectedChunks) {
                if (!omitVerification) emit(JabraFirmwareUpdater.VERIFIED, byteArrayOf(verifyStatus.toByte()))
            } else if (sent == 1 || (sent - 1) % preload == 0) {
                val credit = emit(JabraFirmwareUpdater.CREDIT, ByteArray(if (malformedCredit) 3 else 4))
                if (duplicateFirstCredit && sent == 1) queue.send(credit)
            }
        }
        private suspend fun emit(topic: Int, payload: ByteArray): GnpFrame {
            val seq = if (topic == JabraFirmwareUpdater.CREDIT) { creditSequence = (creditSequence + 1) % 256; creditSequence } else ++sequence
            val frame = GnpFrame(9, if (wrongEndpoint) 5 else 4, seq, GnpFrame.EVENT, topic ushr 8, topic and 255, payload)
            queue.send(frame)
            return frame
        }
        override suspend fun receive(timeoutMs: Long): GnpFrame = withTimeoutOrNull(timeoutMs) { queue.receive() } ?: throw IOException("No firmware notification")
        override fun requireNoPendingEvents() { if (queue.tryReceive().isSuccess) throw IOException("Unexpected queued event") }
        override fun close() { didClose = true; closed.complete(null); queue.close() }
    }

    companion object {
        private val version = JabraFirmwareVersion(4, 6, 0, 0x0409)
        private fun file(partition: Int, size: Int = 16, language: Int = 0x0409): JabraFirmwareFile {
            val bytes = ByteArray(size) { (it * 17).toByte() }
            return JabraFirmwareFile.verified(partition, version.copy(languageId = language), JabraFirmwareCrc.calculate(bytes), bytes)
        }
        private fun pack(productId: Int = 11846, files: List<JabraFirmwareFile> = listOf(file(0))) =
            JabraFirmwarePackage.verified(productId, "4.6.0", 0x0409, files, 2, false, "fixture-package")
        private fun battery(primary: Int, secondary: Int, case: Int) =
            byteArrayOf(0x80.toByte(), primary.toByte(), 0, 0, 2, 4, 0, secondary.toByte(), 0x15, 0, case.toByte())
    }
}
