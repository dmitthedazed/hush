package com.occaecat.hush.jabra.firmware

import com.occaecat.hush.jabra.GnpClient
import com.occaecat.hush.jabra.GnpFirmwareSession
import com.occaecat.hush.jabra.GnpFrame
import com.occaecat.hush.jabra.JabraProtocol
import com.occaecat.hush.jabra.JabraRejectedException
import com.occaecat.hush.jabra.u
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** Protocol 7 only. No retry, unlock, speculative recovery, or automatic installation. */
internal class JabraFirmwareUpdater(
    private val client: GnpClient,
    private val timeouts: Timeouts = Timeouts(),
) {
    internal data class Timeouts(val commandMs: Long = 5_000, val eraseMs: Long = 120_000,
                                val eventMs: Long = 15_000, val totalMs: Long = 45 * 60_000)

    suspend fun readReadiness(expectedProductId: Int): JabraFirmwareReadiness = client.firmwareSession {
        readiness(it, expectedProductId)
    }

    private suspend fun readiness(session: GnpFirmwareSession, expectedProductId: Int): JabraFirmwareReadiness {
        val pid = JabraProtocol.model(session.read(JabraProtocol.PRODUCT_ID, timeoutMs = timeouts.commandMs)).productId
        if (pid != expectedProductId) throw IOException("The connected headphones do not match the selected firmware")
        val blockers = mutableListOf<String>()
        suspend fun checkedRead(topic: Int, label: String, payload: ByteArray = byteArrayOf()): ByteArray? = try {
            session.read(topic, payload, timeoutMs = timeouts.commandMs)
        } catch (e: CancellationException) { throw e
        } catch (e: IOException) {
            blockers += "Cannot verify $label"
            null
        }
        val version = checkedRead(JabraProtocol.FIRMWARE, "the installed firmware")?.let(JabraProtocol::text).orEmpty()
        if (runCatching { JabraFirmwareVersion.parse(version, 0) }.isFailure) blockers += "The installed firmware version is unknown"
        val protocols = checkedRead(PROTOCOLS, "the update protocol")?.map(Byte::u)?.toSet().orEmpty()
        if (7 !in protocols) blockers += "Firmware update protocol 7 is unavailable"
        val lock = checkedRead(LOCK, "the firmware update lock")
        val locked = lock?.singleOrNull()?.u() != 0
        if (locked) blockers += if (lock?.singleOrNull()?.u() == 1) "Firmware updates are locked on these headphones" else "The update lock is unknown"
        val language = try {
            val value = try { session.read(LANGUAGE, timeoutMs = timeouts.commandMs) }
            catch (e: JabraRejectedException) {
                // Sound+ uses the sole available language when the current-language query is unsupported.
                val available = session.read(AVAILABLE_LANGUAGES, timeoutMs = timeouts.commandMs)
                if (available.size != 3 || available[0].u() != 1) throw IOException("Current firmware language is unknown")
                available.copyOfRange(1, 3)
            }
            if (value.size != 2) throw IOException("Invalid firmware language response")
            (value[0].u() or (value[1].u() shl 8)).takeIf { it != 0 }
        } catch (e: CancellationException) { throw e
        } catch (e: IOException) { null }
        if (language == null) blockers += "The current voice language could not be verified"

        val battery = checkedRead(JabraProtocol.BATTERY, "the battery levels", byteArrayOf(1))?.let(JabraProtocol::battery)
        val levels = battery?.components?.associate { it.id to it.level }.orEmpty()
        if (levels["primary"] == null || levels["secondary"] == null) blockers += "Both earbuds must be connected with known battery levels"
        if (levels["case"] == null) blockers += "The charging case battery must be known before updating"
        if (listOf("primary", "secondary", "case").any { levels[it] != null && levels.getValue(it)!! < 50 }) {
            blockers += "Charge both earbuds and the case to at least 50%"
        }
        // Generic XML also lists 0f35, but neither model profile advertises that capability.
        // Sound+'s protocol-7 updater does not use it and Elite 10 4.6.0 ignores the read.
        // Do not infer support from a generic schema, or turn a failed required query into success.
        return JabraFirmwareReadiness(pid, version, language, protocols, locked, blockers.distinct(), levels)
    }

    suspend fun transfer(firmware: JabraFirmwarePackage, onProgress: (JabraFirmwareProgress) -> Unit): JabraFirmwareResult {
        var wrote = false
        try {
            return withTimeout(timeouts.totalMs) {
                client.firmwareSession { session ->
                    fun progress(stage: JabraFirmwareStage, percent: Int, partition: Int? = null, message: String? = null) {
                        onProgress(JabraFirmwareProgress(stage, percent.coerceIn(0, 100), partition, message))
                    }
                    progress(JabraFirmwareStage.PREPARING, 0)
                    val ready = readiness(session, firmware.productId)
                    if (!ready.ready) throw IOException(ready.blockers.joinToString(". "))
                    if (ready.languageId != firmware.languageId) throw IOException("Firmware language does not match the headphones")
                    val current = JabraFirmwareVersion.parse(ready.currentVersion, firmware.languageId)
                    val target = JabraFirmwareVersion.parse(firmware.version, firmware.languageId)
                    fun number(v: JabraFirmwareVersion) = (v.major shl 16) or (v.minor shl 8) or v.patch
                    if (number(target) <= number(current)) throw IOException("The selected firmware must be newer than the installed version")
                    session.requireNoPendingEvents()
                    var completedBytes = 0L
                    fun percent(extra: Int = 0) = ((completedBytes + extra) * 95 / firmware.totalBytes).toInt()
                    for (file in firmware.files) {
                        currentCoroutineContext().ensureActive()
                        progress(JabraFirmwareStage.ERASING, percent(), file.partition)
                        wrote = true
                        session.writeStrict(PARTITION, byteArrayOf(file.partition.toByte()), timeouts.commandMs)
                        parseVersion(session.read(VERSION, timeoutMs = timeouts.commandMs))
                        session.requireNoPendingEvents()
                        // Always transfer the complete package, including when partial uploads are permitted.
                        session.writeStrict(ERASE, timeoutMs = timeouts.commandMs)
                        val erased = notification(session, timeouts.eraseMs)
                        if (erased.topic != ERASED || erased.payload.isNotEmpty()) throw IOException("Unexpected firmware erase response")
                        session.requireNoPendingEvents()
                        session.writeStrict(CRC, crcPayload(file, firmware.preloadCount), timeouts.commandMs)
                        progress(JabraFirmwareStage.TRANSFERRING, percent(), file.partition)
                        var sent = 0
                        suspend fun send(count: Int) {
                            repeat(minOf(count, file.chunkCount - sent)) {
                                currentCoroutineContext().ensureActive()
                                session.sendEvent(DATA, file.chunk(sent++))
                            }
                            progress(JabraFirmwareStage.TRANSFERRING, percent(minOf(sent * 52, file.size)), file.partition)
                        }
                        send(1)
                        var previousCredit: GnpFrame? = null
                        var duplicateCredits = 0
                        while (true) {
                            val event = notification(session, timeouts.eventMs)
                            when (event.topic) {
                                CREDIT -> {
                                    // This is the last received chunk's final four bytes, NOT a numeric credit count.
                                    // Sound+ grants the negotiated preload count for each such notification.
                                    if (event.payload.size != 4) throw IOException("Malformed firmware flow-control notification")
                                    if (previousCredit?.let { it.sequence == event.sequence && it.payload.contentEquals(event.payload) } == true) {
                                        if (++duplicateCredits > 3) throw IOException("Repeated firmware flow-control notification")
                                        continue
                                    }
                                    duplicateCredits = 0
                                    previousCredit = event
                                    if (sent < file.chunkCount) send(firmware.preloadCount)
                                }
                                VERIFIED -> {
                                    if (event.payload.size != 1 || event.payload[0].u() != 0) throw IOException("The headphones rejected the uploaded firmware checksum")
                                    if (sent != file.chunkCount) throw IOException("Firmware verification arrived before all bytes were transferred")
                                    break
                                }
                                else -> throw IOException("Unexpected firmware notification during transfer")
                            }
                        }
                        session.requireNoPendingEvents()
                        progress(JabraFirmwareStage.VERIFYING, percent(file.size), file.partition)
                        session.writeStrict(VERSION, file.version.encode(), timeouts.commandMs)
                        val observed = parseVersion(session.read(VERSION, timeoutMs = timeouts.commandMs))
                        if (observed != file.version) throw IOException("The staged firmware version was not confirmed")
                        completedBytes += file.size
                    }
                    // Verify every partition again before the irreversible finalization request.
                    progress(JabraFirmwareStage.VERIFYING, 96)
                    for (file in firmware.files) {
                        session.requireNoPendingEvents()
                        session.writeStrict(PARTITION, byteArrayOf(file.partition.toByte()), timeouts.commandMs)
                        if (parseVersion(session.read(VERSION, timeoutMs = timeouts.commandMs)) != file.version) {
                            throw IOException("Staged firmware partitions do not match the selected package")
                        }
                    }
                    session.requireNoPendingEvents()
                    currentCoroutineContext().ensureActive()
                    progress(JabraFirmwareStage.COMMITTING, 99)
                    session.writeStrict(GO, timeoutMs = timeouts.commandMs)
                    progress(JabraFirmwareStage.AWAITING_CASE, 100, message = "Place both earbuds in the charging case to complete installation")
                    JabraFirmwareResult.AWAITING_CASE
                }
            }
        } catch (e: TimeoutCancellationException) {
            if (wrote) client.close()
            throw IOException("Firmware transfer timed out; installation has not been confirmed", e)
        } catch (e: Throwable) {
            // Closing prevents a cancelled or failed transfer from leaking late events into settings.
            if (wrote) client.close()
            throw e
        }
    }

    private suspend fun notification(session: GnpFirmwareSession, timeoutMs: Long): GnpFrame {
        val event = session.receive(timeoutMs)
        if (event.destination != 9 || event.source != 4 || event.kind !in setOf(GnpFrame.EVENT, GnpFrame.RESPONSE)) {
            throw IOException("Invalid firmware notification endpoint or kind")
        }
        if (event.command == GnpFrame.NAK) throw JabraRejectedException(event.subcommand)
        return event
    }

    private fun parseVersion(bytes: ByteArray): JabraFirmwareVersion {
        if (bytes.size !in setOf(5, 8) || (bytes.size == 8 && bytes.drop(5).any { it != 0.toByte() })) {
            throw IOException("Malformed staged firmware version")
        }
        return JabraFirmwareVersion(bytes[0].u(), bytes[1].u(), bytes[2].u(), bytes[3].u() or (bytes[4].u() shl 8))
    }

    internal fun crcPayload(file: JabraFirmwareFile, preload: Int): ByteArray = byteArrayOf(
        (file.crc ushr 16).toByte(), (file.crc ushr 24).toByte(), file.crc.toByte(), (file.crc ushr 8).toByte(),
        file.chunkCount.toByte(), (file.chunkCount ushr 8).toByte(), preload.toByte(), (preload ushr 8).toByte(), 0, 0)

    companion object {
        internal const val PROTOCOLS = 0x0214
        internal const val LANGUAGE = 0x1308
        internal const val AVAILABLE_LANGUAGES = 0x0208
        internal const val LOCK = 0x0f34
        internal const val PARTITION = 0x0f2d
        internal const val VERSION = 0x0f1e
        internal const val ERASE = 0x0f17
        internal const val ERASED = 0x0f18
        internal const val CRC = 0x0f19
        internal const val DATA = 0x0f1a
        internal const val CREDIT = 0x0f1b
        internal const val VERIFIED = 0x0f1c
        internal const val GO = 0x0f1d
    }
}
