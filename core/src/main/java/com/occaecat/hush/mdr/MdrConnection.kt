package com.occaecat.hush.mdr

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.UUID

/**
 * One RFCOMM session with a Sony headset.
 *
 * Mirrors lm0.c: a single outstanding DATA frame at a time, sequence number toggles 0/1,
 * the device's ACK carries the *next* sequence number, retry every 750 ms up to 10 times.
 * Incoming DATA frames are ACKed with (1 - seq) and de-duplicated by sequence number.
 */
class MdrConnection(private val device: BluetoothDevice, private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "MdrConnection"
        /** Sony "MDR" SPP service (lm0.i). */
        val MDR_UUID: UUID = UUID.fromString("96CC203E-5068-46ad-B32D-E316F5E069BA")
        private const val ACK_TIMEOUT_MS = 750L
        private const val MAX_RETRIES = 10
        private const val CONNECT_TIMEOUT_MS = 8_000L
    }

    @Volatile private var socket: BluetoothSocket? = null
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

    /**
     * BluetoothSocket.connect() has no timeout: if the headset never answers the RFCOMM request (seen on the
     * XM4 right after switching the CUSTOM button to a voice assistant) it blocks forever and leaves a
     * half-open port in the stack. Close the socket from a watchdog so the caller can retry.
     */
    @SuppressLint("MissingPermission")
    private fun connectWithTimeout(create: () -> BluetoothSocket): BluetoothSocket {
        val s = create()
        // Let an explicit disconnect interrupt an in-progress Bluetooth connect(), too.
        socket = s
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
    suspend fun open() = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val s = try { connectWithTimeout { device.createRfcommSocketToServiceRecord(MDR_UUID) } }
            catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                connectWithTimeout { device.createInsecureRfcommSocketToServiceRecord(MDR_UUID) }
            }
        try { currentCoroutineContext().ensureActive() }
        catch (e: CancellationException) { s.close(); throw e }
        socket = s
        sendSeq = 0
        lastRecvSeq = -1
        readerJob = scope.launch(Dispatchers.IO) { readLoop(s) }
    }

    private fun readLoop(s: BluetoothSocket) {
        val parser = FrameParser()
        val buf = ByteArray(2048)
        val input = s.inputStream
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
                val event = Mdr.parse(f.payload, table2 = f.type == Frame.Type.DATA_MDR_NO2)
                Log.d(TAG, "<- ${f.payload.hex()} $event")
                _events.tryEmit(event)
            }
            else -> Log.d(TAG, "ignoring frame type ${f.type}")
        }
    }

    private fun write(bytes: ByteArray) {
        val out = socket?.outputStream ?: throw IOException("not connected")
        synchronized(writeLock) {
            out.write(bytes)
            out.flush()
        }
    }

    /** Sends one MDR payload and suspends until the headset ACKs it. */
    suspend fun send(payload: ByteArray, table2: Boolean = false) = sendLock.withLock {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "-> ${payload.hex()}")
            repeat(MAX_RETRIES + 1) {
                val ack = CompletableDeferred<Unit>()
                pendingAck = ack
                write(Frame.encode(if (table2) Frame.Type.DATA_MDR_NO2 else Frame.Type.DATA_MDR, sendSeq, payload))
                if (withTimeoutOrNull(ACK_TIMEOUT_MS) { ack.await() } != null) return@withContext
            }
            throw IOException("headset did not acknowledge ${payload.hex()}")
        }
    }

    fun close() {
        readerJob?.cancel()
        runCatching { socket?.close() }
        socket = null
    }
}

fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }
