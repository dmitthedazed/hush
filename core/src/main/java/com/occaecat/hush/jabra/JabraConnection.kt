package com.occaecat.hush.jabra

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface GnpClient {
    val closed: Deferred<Throwable?>
    val events: SharedFlow<GnpFrame>
    suspend fun open()
    suspend fun read(topic: Int, payload: ByteArray = byteArrayOf()): ByteArray
    suspend fun write(topic: Int, payload: ByteArray)
    suspend fun <T> firmwareSession(block: suspend (GnpFirmwareSession) -> T): T =
        throw IOException("Firmware transfer is unavailable on this connection")
    fun close()
}

/** Exclusive commands and ordered update notifications; ordinary settings cannot interleave. */
internal interface GnpFirmwareSession {
    suspend fun read(topic: Int, payload: ByteArray = byteArrayOf(), timeoutMs: Long = 5_000): ByteArray
    suspend fun writeStrict(topic: Int, payload: ByteArray = byteArrayOf(), timeoutMs: Long = 5_000)
    suspend fun sendEvent(topic: Int, payload: ByteArray)
    suspend fun receive(timeoutMs: Long): GnpFrame
    fun requireNoPendingEvents()
}

/** A bounded inbox which reports overflow rather than silently discarding update credits. */
internal class GnpFirmwareInbox(capacity: Int = 256) {
    private val channel = Channel<GnpFrame>(capacity)
    fun offer(frame: GnpFrame): Boolean = channel.trySend(frame).isSuccess
    suspend fun receive(timeoutMs: Long): GnpFrame = withTimeoutOrNull(timeoutMs) { channel.receive() }
        ?: throw IOException("Timed out waiting for a firmware notification")
    fun requireEmpty() {
        val result = channel.tryReceive()
        if (result.isSuccess) throw IOException("Unexpected firmware notification between stages")
        result.exceptionOrNull()?.let { throw IOException("Firmware session closed", it) }
    }
    fun close(error: Throwable) { channel.close(error) }
    fun cancel() { channel.cancel() }
}

/** Cancellation must close the socket while its blocking write is still running. */
internal suspend fun cancellableGnpWrite(scope: CoroutineScope, closeOnCancel: () -> Unit, write: () -> Unit) =
    suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation { runCatching(closeOnCancel) }
        val writer = scope.launch(Dispatchers.IO) {
            try {
                write()
                if (continuation.isActive) continuation.resume(Unit)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
        writer.invokeOnCompletion { error ->
            if (error != null && continuation.isActive) continuation.resumeWithException(error)
        }
    }

internal class JabraRejectedException(val code: Int) : IOException("The headphones rejected this setting (GNP ${code.toString(16)})")

/** One outstanding GNP request; no vendor SDK, polling scan, or persistent Bluetooth thread. */
internal class JabraConnection(
    private val device: BluetoothDevice,
    parent: CoroutineScope,
    private val debug: Boolean = false,
) : GnpClient {
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    private val completion = CompletableDeferred<Throwable?>()
    override val closed: Deferred<Throwable?> get() = completion
    private val notifications = MutableSharedFlow<GnpFrame>(extraBufferCapacity = 32)
    override val events: SharedFlow<GnpFrame> get() = notifications
    private val requests = Mutex()
    private val guard = Any()
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var stopped = false
    private var sequence = 0
    private var pending: Pair<GnpFrame, CompletableDeferred<GnpFrame>>? = null
    private var firmwareEvents: GnpFirmwareInbox? = null

    init {
        // Closing the blocking socket must run when cancellation starts, before the reader exits.
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { finish(null) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun open() {
        check(!stopped)
        val spp = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        val name = runCatching { device.name }.getOrNull().orEmpty()
        val channels = if (name.contains("85t", true)) listOf(3) else listOf(29, 3)
        val factories = mutableListOf<Pair<String, () -> BluetoothSocket>>(
            "SPP insecure" to { device.createInsecureRfcommSocketToServiceRecord(spp) },
            "SPP secure" to { device.createRfcommSocketToServiceRecord(spp) },
        )
        channels.forEach { channel -> factories += "channel $channel insecure" to {
            device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, channel) as BluetoothSocket
        } }
        var failure: Throwable? = null
        for ((endpoint, factory) in factories) {
            currentCoroutineContext().ensureActive()
            if (stopped) throw IOException("Jabra session closed")
            trace("connect attempt endpoint=$endpoint")
            try {
                val connected = withTimeout(5_000) { connectSocket(factory) }
                synchronized(guard) {
                    if (stopped) { connected.close(); throw IOException("Jabra session closed") }
                    socket = connected
                }
                trace("connect success endpoint=$endpoint")
                scope.launch(Dispatchers.IO) { readLoop(connected) }
                return
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                failure = IOException("Jabra connection timed out", e)
                trace("connect failure endpoint=$endpoint reason=timeout")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                failure = e
                // Exception messages can contain a Bluetooth address; log only the exception type.
                trace("connect failure endpoint=$endpoint reason=${e.javaClass.simpleName}")
            }
        }
        throw IOException("Cannot open Jabra controls. Wake the earbuds and close Jabra Sound+.", failure)
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectSocket(factory: () -> BluetoothSocket): BluetoothSocket = suspendCancellableCoroutine { continuation ->
        val candidate = try { factory() } catch (e: Exception) {
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }
        // Publish the connecting socket so explicit disconnect can interrupt connect(), too.
        synchronized(guard) {
            if (stopped) { candidate.close(); continuation.resumeWithException(IOException("Jabra session closed")); return@suspendCancellableCoroutine }
            socket = candidate
        }
        continuation.invokeOnCancellation { runCatching { candidate.close() } }
        scope.launch(Dispatchers.IO) {
            try {
                candidate.connect()
                if (continuation.isActive) continuation.resume(candidate) else candidate.close()
            } catch (e: Exception) {
                runCatching { candidate.close() }
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }

    override suspend fun read(topic: Int, payload: ByteArray): ByteArray = requests.withLock {
        exchange(GnpFrame.READ, topic, payload, 1_800, required = true)!!.payload
    }

    override suspend fun write(topic: Int, payload: ByteArray) = requests.withLock {
        // Some firmware acknowledges a write with a state event only. The backend always reads back.
        exchange(GnpFrame.WRITE, topic, payload, 350, required = false)
        Unit
    }

    override suspend fun <T> firmwareSession(block: suspend (GnpFirmwareSession) -> T): T = requests.withLock {
        // The reader never drops an update event: overflow terminates the session instead.
        val queue = GnpFirmwareInbox()
        synchronized(guard) {
            if (stopped || socket == null) throw IOException("Jabra session closed")
            check(firmwareEvents == null)
            firmwareEvents = queue
        }
        var changedDevice = false
        val session = object : GnpFirmwareSession {
            private fun checkOwner() {
                synchronized(guard) {
                    if (stopped || firmwareEvents !== queue) throw IOException("Firmware session closed")
                }
            }
            override suspend fun read(topic: Int, payload: ByteArray, timeoutMs: Long): ByteArray {
                checkOwner()
                return exchange(GnpFrame.READ, topic, payload, timeoutMs, required = true)!!.payload
            }
            override suspend fun writeStrict(topic: Int, payload: ByteArray, timeoutMs: Long) {
                checkOwner()
                changedDevice = true
                val response = exchange(GnpFrame.WRITE, topic, payload, timeoutMs, required = true)!!
                if (response.command != GnpFrame.ACK) throw IOException("Missing firmware command acknowledgement")
            }
            override suspend fun sendEvent(topic: Int, payload: ByteArray) {
                checkOwner()
                changedDevice = true
                currentCoroutineContext().ensureActive()
                val frame = synchronized(guard) {
                    sequence = sequence % 255 + 1
                    GnpFrame.request(sequence, GnpFrame.EVENT, topic, payload)
                }
                withTimeout(5_000) { sendFrame(frame) }
            }
            override suspend fun receive(timeoutMs: Long): GnpFrame {
                checkOwner()
                return queue.receive(timeoutMs)
            }
            override fun requireNoPendingEvents() {
                checkOwner()
                queue.requireEmpty()
            }
        }
        try { block(session) } catch (e: Throwable) {
            // Close before unlocking, so a waiting ordinary request cannot run after an aborted update.
            if (changedDevice) finish(e)
            throw e
        } finally {
            synchronized(guard) { if (firmwareEvents === queue) firmwareEvents = null }
            queue.cancel()
        }
    }

    private suspend fun sendFrame(frame: GnpFrame) = cancellableGnpWrite(scope, { finish(IOException("Jabra write cancelled")) }) {
        val active = socket ?: throw IOException("Jabra session closed")
        traceFrame("TX", frame)
        active.outputStream.write(frame.encode())
        active.outputStream.flush()
    }

    private suspend fun exchange(kind: Int, topic: Int, payload: ByteArray, timeout: Long, required: Boolean): GnpFrame? {
        currentCoroutineContext().ensureActive()
        val result = CompletableDeferred<GnpFrame>()
        val frame = synchronized(guard) {
            if (stopped || socket == null) throw IOException("Jabra session closed")
            sequence = sequence % 255 + 1
            GnpFrame.request(sequence, kind, topic, payload).also { pending = it to result }
        }
        try {
            val response = withTimeoutOrNull(timeout) {
                sendFrame(frame)
                result.await()
            }
            if (response == null && required) throw IOException("Jabra did not answer topic ${topic.toString(16)}")
            return response
        } finally {
            synchronized(guard) { if (pending?.second === result) pending = null }
            result.cancel()
        }
    }

    private fun readLoop(active: BluetoothSocket) {
        val parser = GnpParser()
        val buffer = ByteArray(1024)
        var failure: Throwable? = null
        try {
            val input = active.inputStream
            while (!stopped) {
                val count = input.read(buffer)
                if (count < 0) break
                parser.feed(buffer, count).forEach { frame ->
                    traceFrame("RX", frame)
                    val transaction = synchronized(guard) { pending }
                    val matched = transaction != null && frame.acknowledges(transaction.first)
                    if (matched && transaction != null) {
                        when (frame.command) {
                            GnpFrame.NAK -> transaction.second.completeExceptionally(JabraRejectedException(frame.subcommand))
                            GnpFrame.ACK -> if (transaction.first.kind == GnpFrame.WRITE) transaction.second.complete(frame)
                            else -> transaction.second.complete(frame)
                        }
                    }
                    val updateQueue = synchronized(guard) { firmwareEvents }
                    val updateNotification = !matched && (frame.command == 0x0f ||
                        (frame.command == GnpFrame.NAK && frame.payload.size >= 5 && frame.payload[4].u() == 0x0f))
                    if (updateQueue != null && updateNotification && frame.destination == 9 && frame.source == 4) {
                        if (!updateQueue.offer(frame)) throw IOException("Firmware event queue overflow")
                    } else if (frame.kind == GnpFrame.EVENT) notifications.tryEmit(frame)
                }
            }
        } catch (e: Exception) { if (!stopped) failure = e
        } finally {
            trace("reader stopped reason=${if (stopped) "closed" else failure?.javaClass?.simpleName ?: "EOF"}")
            finish(failure)
        }
    }

    private fun trace(message: String) {
        if (debug) Log.d("JabraGnp", message)
    }

    private fun traceFrame(direction: String, frame: GnpFrame) {
        if (!debug) return
        val safePayload = frame.topic in setOf(
            JabraProtocol.PRODUCT_ID, JabraProtocol.AMBIENCE, JabraProtocol.EQ,
            JabraProtocol.EQ_ENABLED, JabraProtocol.SLEEP, JabraProtocol.ANNOUNCEMENTS,
            JabraProtocol.BATTERY, JabraProtocol.EVENTS, 0x138e, 0x1383,
        ) || frame.command == GnpFrame.ACK || frame.command == GnpFrame.NAK
        val payload = if (safePayload) frame.payload.joinToString(" ") { "%02x".format(it.u()) }
            else "<redacted ${frame.payload.size} bytes>"
        val kind = when (frame.kind) {
            GnpFrame.EVENT -> "event"
            GnpFrame.READ -> "read"
            GnpFrame.WRITE -> "write"
            else -> "response"
        }
        trace("$direction kind=$kind seq=${frame.sequence} topic=${"%04x".format(frame.topic)} payload=[$payload]")
    }

    private fun finish(error: Throwable?) {
        synchronized(guard) {
            stopped = true
            pending?.second?.completeExceptionally(error ?: IOException("Jabra connection closed"))
            pending = null
            firmwareEvents?.close(error ?: IOException("Jabra connection closed"))
            runCatching { socket?.close() }
            socket = null
        }
        completion.complete(error)
        job.cancel()
    }

    override fun close() = finish(null)
}
