package com.occaecat.hush.mdr

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.occaecat.hush.mdr.v1.MdrV1
import com.occaecat.hush.mdr.v2.MdrV2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * The byte streams of one open control channel; [close] tears it down and makes reads fail. [v2]: the channel is
 * Sony's MDR v2 service, so payloads are v2 (Sound Connect picks the generation by the service, not by a reply).
 */
class MdrStreams(val input: InputStream, val output: OutputStream, val v2: Boolean = false, val close: () -> Unit)

/** Opens the control channel: RFCOMM to the headset in the app, a simulated headset in tests. */
/** Watches a session's data payloads (the headset self test's capture). */
fun interface FrameTap {
    fun frame(out: Boolean, table: Table, payload: ByteArray)
}

fun interface MdrLink {
    suspend fun open(): MdrStreams
}

/**
 * One RFCOMM session with a Sony headset.
 *
 * Mirrors lm0.c: a single outstanding DATA frame at a time, sequence number toggles 0/1,
 * the device's ACK carries the *next* sequence number, retry every 750 ms up to 10 times.
 * Incoming DATA frames are ACKed with (1 - seq) and de-duplicated by sequence number.
 */
class MdrConnection(
    private val link: MdrLink,
    private val scope: CoroutineScope,
    /** Decodes a payload of the given table; null picks the generation's parser from [v2]. */
    private val parse: ((Table, ByteArray) -> MdrEvent)? = null,
    /** Sees every data payload once: sent ones before their first try, received ones after de-duplication. */
    private val tap: FrameTap? = null,
) {

    companion object {
        private const val TAG = "MdrConnection"
        /** Sony "MDR" SPP service, v1 command tables (lm0.i TABLE_SET_1). */
        val MDR_UUID: UUID = UUID.fromString("96CC203E-5068-46ad-B32D-E316F5E069BA")
        /** The v2 service (lm0.i TABLE_SET_2): WH-1000XM5, WF-1000XM5, LinkBuds and later. */
        val MDR_V2_UUID: UUID = UUID.fromString("956C7B26-D49A-4BA8-B03F-B17D393CB6E2")
        private const val ACK_TIMEOUT_MS = 750L
        private const val MAX_RETRIES = 10
    }

    private var streams: MdrStreams? = null

    /**
     * Which generation's parser decodes payloads: set from the channel on [open], and switched by the controller
     * when a headset reached over the v1 service answers in the v2 layout (as Gadgetbridge tells them apart).
     */
    @Volatile var v2 = false
    private var readerJob: Job? = null
    private val sendLock = Mutex()
    private val writeLock = Any()

    @Volatile private var sendSeq: Byte = 0
    @Volatile private var lastRecvSeq: Byte = -1
    @Volatile private var pendingAck: CompletableDeferred<Unit>? = null

    private val _events = MutableSharedFlow<MdrEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<MdrEvent> = _events

    private val _closed = CompletableDeferred<Throwable?>()
    val closed: CompletableDeferred<Throwable?> get() = _closed

    suspend fun open() {
        val s = link.open()
        streams = s
        v2 = s.v2
        sendSeq = 0
        lastRecvSeq = -1
        readerJob = scope.launch(Dispatchers.IO) { readLoop(s) }
    }

    private fun readLoop(s: MdrStreams) {
        val parser = FrameParser()
        val buf = ByteArray(2048)
        val input = s.input
        var error: Throwable? = null
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                for (frame in parser.feed(buf, n)) onFrame(frame)
            }
        } catch (e: IOException) {
            error = e
        } catch (e: RuntimeException) {
            // A malformed frame must end the session cleanly rather than kill the reader silently.
            Log.w(TAG, "reader failed", e)
            error = e
        } finally {
            pendingAck?.completeExceptionally(IOException("connection closed"))
            _closed.complete(error)
        }
    }

    private fun onFrame(f: ReceivedFrame) {
        when (f.type) {
            Frame.Type.ACK -> {
                if (f.seq != sendSeq) {
                    sendSeq = f.seq
                    pendingAck?.complete(Unit)
                }
            }
            Frame.Type.DATA_MDR, Frame.Type.DATA_MDR_NO2 -> {
                write(Frame.ack((1 - f.seq).toByte()))
                if (f.seq == lastRecvSeq) return
                lastRecvSeq = f.seq
                tap?.frame(false, if (f.type == Frame.Type.DATA_MDR_NO2) Table.TWO else Table.ONE, f.payload)
                val table = if (f.type == Frame.Type.DATA_MDR_NO2) Table.TWO else Table.ONE
                val event = parse?.invoke(table, f.payload) ?: if (v2) MdrV2.parse(table, f.payload) else MdrV1.parse(table, f.payload)
                Log.d(TAG, "<- ${f.payload.hex()} $event")
                if (event is MdrEvent.Several) event.events.forEach { _events.tryEmit(it) } else _events.tryEmit(event)
            }
            else -> Log.d(TAG, "ignoring frame type ${f.type}")
        }
    }

    private fun write(bytes: ByteArray) {
        val out = streams?.output ?: throw IOException("not connected")
        synchronized(writeLock) {
            out.write(bytes)
            out.flush()
        }
    }

    /** Sends one request and suspends until the headset ACKs it. */
    suspend fun send(request: Request) = sendLock.withLock {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "-> $request")
            tap?.frame(true, request.table, request.payload)
            val type = if (request.table == Table.TWO) Frame.Type.DATA_MDR_NO2 else Frame.Type.DATA_MDR
            repeat(MAX_RETRIES + 1) {
                val ack = CompletableDeferred<Unit>()
                pendingAck = ack
                write(Frame.encode(type, sendSeq, request.payload))
                if (withTimeoutOrNull(ACK_TIMEOUT_MS) { ack.await() } != null) return@withContext
            }
            throw IOException("headset did not acknowledge $request")
        }
    }

    fun close() {
        readerJob?.cancel()
        runCatching { streams?.close?.invoke() }
        streams = null
    }
}

/**
 * The headset's MDR service over RFCOMM, secure first, then insecure (lm0.i). Like Sound Connect (ep0/a), the
 * generation follows the services the headset advertises: the v2 one first when it is there, the v1 one otherwise.
 * When the SDP record is unknown (no cached UUIDs) both are tried, v1 first.
 */
class BluetoothMdrLink(private val device: BluetoothDevice, private val scope: CoroutineScope) : MdrLink {

    /**
     * BluetoothSocket.connect() has no timeout: if the headset never answers the RFCOMM request (seen on the
     * XM4 right after switching the CUSTOM button to a voice assistant) it blocks forever and leaves a
     * half-open port in the stack. Close the socket from a watchdog so the caller can retry.
     */
    private fun connectWithTimeout(create: () -> BluetoothSocket): BluetoothSocket {
        val s = create()
        val watchdog = scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(CONNECT_TIMEOUT_MS)
            runCatching { s.close() }
        }
        try {
            s.connect()
            return s
        } catch (e: IOException) {
            runCatching { s.close() }
            throw e
        } finally {
            watchdog.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun open(): MdrStreams = withContext(Dispatchers.IO) {
        val order = services(runCatching { device.uuids?.map { it.uuid } }.getOrNull())
        var failure: Throwable? = null
        for (uuid in order) {
            val s = runCatching { connectWithTimeout { device.createRfcommSocketToServiceRecord(uuid) } }
                .recoverCatching { connectWithTimeout { device.createInsecureRfcommSocketToServiceRecord(uuid) } }
                .onFailure { failure = it }
                .getOrNull() ?: continue
            return@withContext MdrStreams(s.inputStream, s.outputStream, v2 = uuid == MdrConnection.MDR_V2_UUID) { s.close() }
        }
        throw failure ?: IOException("no MDR service")
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000L

        /** The services to try, in order, for the UUIDs the phone has cached for the headset (null: none known). */
        fun services(advertised: List<UUID>?): List<UUID> {
            val v1 = advertised?.contains(MdrConnection.MDR_UUID) == true
            val v2 = advertised?.contains(MdrConnection.MDR_V2_UUID) == true
            return when {
                v2 && v1 -> listOf(MdrConnection.MDR_V2_UUID, MdrConnection.MDR_UUID)
                v2 -> listOf(MdrConnection.MDR_V2_UUID)
                v1 -> listOf(MdrConnection.MDR_UUID)
                else -> listOf(MdrConnection.MDR_UUID, MdrConnection.MDR_V2_UUID)
            }
        }
    }

}
