package com.occaecat.hush.jabra

import android.bluetooth.BluetoothDevice
import com.occaecat.hush.gnp.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Independent Jabra GNP implementation. The controller owns reconnects and selection. */
class JabraBackend internal constructor(private val client: GnpClient, parent: CoroutineScope,
                                        private val expectedModel: JabraModel? = null) {
    constructor(device: BluetoothDevice, scope: CoroutineScope, modelHint: JabraModel? = null, debug: Boolean = false) :
        this(JabraConnection(device, scope, modelHint, debug), scope, modelHint)

    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    private val mutableState = MutableStateFlow(JabraState())
    val state: StateFlow<JabraState> get() = mutableState
    val closed: Deferred<Throwable?> get() = client.closed
    private val operations = Mutex()
    private val ticket = AtomicLong()
    private val latest = ConcurrentHashMap<String, Long>()
    private var model: JabraModel? = null
    private var equalizer: JabraEqualizer? = null
    private var equalizerBypassDenied = false
    @Volatile private var ready = false
    @Volatile private var stopped = false
    private val buttons = JabraButtons(client)

    suspend fun open() {
        checkActive()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { close() }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.events.collect { event ->
                if (!ready || stopped) return@collect
                if (event.topic == JabraProtocol.BATTERY) {
                    publishBattery(event.payload)
                } else if (event.topic == JabraProtocol.EVENTS && JabraProtocol.onHeadEvent(event.payload) != null) {
                    val (side, on) = JabraProtocol.onHeadEvent(event.payload)!!
                    mutableState.update { if (side == "left") it.copy(leftOnHead = on) else it.copy(rightOnHead = on) }
                } else if (event.topic == JabraProtocol.EVENTS || event.topic == JabraProtocol.AMBIENCE) {
                    // Pushes following writes may describe the previous mode. Re-read under the same
                    // transaction lock used by writes, so events cannot roll back a confirmed setting.
                    scope.launch {
                        try {
                            change("event-refresh") {
                                delay(600)
                                refreshNoise()
                                if (event.topic == JabraProtocol.EVENTS && event.payload.firstOrNull()?.u() != 9) {
                                    refreshSettings()
                                    refreshEqualizer()
                                    if (state.value.buttonsState != null) refreshButtons()
                                }
                            }
                        } catch (_: IOException) { /* next push/reconnect refreshes the state */ }
                    }
                } else if (event.topic == JabraProtocol.EQ || event.topic == JabraProtocol.EQ_ENABLED) {
                    scope.launch {
                        try { change("eq-refresh") { refreshEqualizer() } } catch (_: IOException) { }
                    }
                }
            }
        }
        client.open()
    }

    suspend fun initialize() = operations.withLock {
        checkActive()
        val identity = JabraProtocol.model(client.read(JabraProtocol.PRODUCT_ID))
        if (expectedModel != null && identity != expectedModel)
            throw IOException("This pair is not the selected Jabra model")
        model = identity
        val initialMode = readMode() // Required before any setting can be sent.
        val available = optional { JabraProtocol.ambienceCapabilities(client.read(JabraProtocol.AMBIENCE, byteArrayOf(0))) }
        val noiseCapabilities = JabraNoiseCapabilities(
            modes = available?.modes ?: AmbientMode.entries.toSet(),
            ambientMax = available?.ambientMax ?: 0,
            ancMax = available?.ancMax ?: 0,
        )
        mutableState.update { it.copy(model = identity, noiseCapabilities = noiseCapabilities,
            noise = JabraNoiseState(initialMode)) }
        optional {
            val subscription = JabraProtocol.eventSubscription(client.read(JabraProtocol.EVENTS))
                ?: throw IOException("Invalid Jabra supported events")
            client.write(JabraProtocol.EVENTS, subscription)
        }
        optional { JabraProtocol.text(client.read(JabraProtocol.FIRMWARE)) }?.let { firmware ->
            mutableState.update { it.copy(firmware = firmware) }
        }
        optional { JabraProtocol.text(client.read(JabraProtocol.SERIAL)) }?.let { serial ->
            mutableState.update { it.copy(serial = serial) }
        }
        optional { publishBattery(client.read(JabraProtocol.BATTERY, byteArrayOf(1))) }
        readLevels()
        refreshEqualizer()
        refreshSettings()
        optional { refreshButtons() }
        checkActive()
        ready = true
    }

    suspend fun setMode(mode: AmbientMode) = change("mode") {
        require(mode in state.value.noiseCapabilities.modes) { "Noise mode is unavailable" }
        client.write(JabraProtocol.AMBIENCE, byteArrayOf(1, JabraProtocol.modeValue(mode).toByte()))
        delay(600)
        val confirmed = readMode()
        mutableState.update { it.copy(noise = it.noise?.copy(mode = confirmed)) }
        verify(confirmed == mode)
    }

    suspend fun setAmbientLevel(level: Int) = change("ambient-level") {
        val max = state.value.noiseCapabilities.ambientMax
        require(max > 0 && level in 0..max) { "HearThrough strength is unavailable" }
        val confirmed = setLevel(2, max, level)
        mutableState.update { it.copy(noise = it.noise?.copy(ambientLevel = confirmed)) }
        verify(confirmed == level)
    }

    suspend fun setAncLevel(level: Int) = change("anc-level") {
        val max = state.value.noiseCapabilities.ancMax
        require(max > 0 && level in 0..max) { "ANC strength is unavailable" }
        val confirmed = setLevel(3, max, level)
        mutableState.update { it.copy(ancLevel = confirmed) }
        verify(confirmed == level)
    }

    private suspend fun setLevel(index: Int, max: Int, level: Int): Int {
        client.write(JabraProtocol.AMBIENCE, byteArrayOf(index.toByte(), (max - level).toByte()))
        val raw = JabraProtocol.indexedScalar(client.read(JabraProtocol.AMBIENCE, byteArrayOf(index.toByte())), index)
        if (raw == null || raw !in 0..max) throw IOException("Invalid Jabra strength readback")
        return max - raw
    }

    suspend fun setEqBands(bands: List<Int>, enabled: Boolean? = null) = change("eq-bands") {
        confirmEqBands(bands, enabled)
    }

    suspend fun setEqBand(index: Int, level: Int) = change("eq-band-$index") {
        // Resolve the untouched gains only after previous edits have completed their readback.
        // Requests for another band must not supersede this band's queued intent.
        val current = equalizer ?: throw IllegalArgumentException("Equalizer is unavailable")
        require(index in current.bands.indices) { "Equalizer band is unavailable" }
        val bands = current.bands.map { it.gain }.toMutableList().also { it[index] = level }
        confirmEqBands(bands, enabled = true)
    }

    private suspend fun confirmEqBands(bands: List<Int>, enabled: Boolean?) {
        val current = equalizer ?: throw IllegalArgumentException("Equalizer is unavailable")
        val supportsEnable = state.value.eqCaps?.supportsEnable == true
        require(enabled != false || supportsEnable) { "Equalizer bypass is unavailable" }
        client.write(JabraProtocol.EQ, current.write(bands))
        val confirmed = readEqualizer()
        equalizer = confirmed
        publishEqualizer(confirmed, state.value.eq?.enabled ?: true)
        verify(confirmed.bands.map { it.gain } == bands)
        if (enabled != null && supportsEnable) confirmEqEnabled(enabled)
    }

    suspend fun setEqEnabled(enabled: Boolean) = change("eq-enabled") {
        confirmEqEnabled(enabled)
    }

    private suspend fun confirmEqEnabled(enabled: Boolean) {
        require(state.value.eqCaps?.supportsEnable == true) { "Equalizer bypass is unavailable" }
        client.write(JabraProtocol.EQ_ENABLED, byteArrayOf(if (enabled) 1 else 0))
        val confirmed = client.read(JabraProtocol.EQ_ENABLED).singleOrNull()?.asBoolean()
            ?: throw IOException("Invalid Jabra equalizer readback")
        mutableState.update { it.copy(eq = it.eq?.copy(enabled = confirmed)) }
        verify(confirmed == enabled)
    }

    suspend fun setPauseWhenRemoved(enabled: Boolean) = change("pause") {
        require(state.value.pauseWhenRemoved != null) { "Automatic pause is unavailable" }
        val identity = checkNotNull(model)
        client.write(identity.pauseTopic, identity.pauseWrite(enabled))
        val confirmed = identity.parsePause(client.read(identity.pauseTopic, identity.pauseRead))
            ?: throw IOException("Invalid Jabra automatic pause readback")
        mutableState.update { it.copy(pauseWhenRemoved = confirmed) }
        verify(confirmed == enabled)
    }

    suspend fun setSleepMinutes(minutes: Int) = change("sleep") {
        require(state.value.sleepMinutes != null && minutes in JabraProtocol.sleepMinutes) { "Sleep timeout is unavailable" }
        client.write(JabraProtocol.SLEEP, byteArrayOf((minutes / 5).toByte()))
        val confirmed = readSleep()
        mutableState.update { it.copy(sleepMinutes = confirmed) }
        verify(confirmed == minutes)
    }

    suspend fun setAnnouncementMode(mode: Int) = change("announcements") {
        require(state.value.announcementMode != null && mode in 0..2) { "Announcement mode is unavailable" }
        client.write(JabraProtocol.ANNOUNCEMENTS, byteArrayOf(mode.toByte()))
        val confirmed = readAnnouncements()
        mutableState.update { it.copy(announcementMode = confirmed) }
        verify(confirmed == mode)
    }

    suspend fun refreshBattery() = change("battery") {
        if (!publishBattery(client.read(JabraProtocol.BATTERY, byteArrayOf(1))))
            throw IOException("Invalid Jabra battery response")
    }

    suspend fun refreshJabraButtons() = change("buttons-refresh") { refreshButtons() }

    private suspend fun refreshButtons() = buttonOperation { buttons.read() }

    suspend fun setJabraButton(slot: JabraButtonSlot, action: Int) = change("buttons", conflate = false) {
        buttonOperation { buttons.set(slot, action) }
    }

    suspend fun resetJabraButtons(context: JabraButtonContext) = change("buttons", conflate = false) {
        buttonOperation { buttons.reset(context) }
    }

    private suspend fun buttonOperation(block: suspend () -> JabraButtonState) {
        mutableState.update { it.copy(buttonsState = (it.buttonsState ?: JabraButtonState()).copy(loading = true, error = null)) }
        try {
            val confirmed = block()
            mutableState.update { it.copy(buttonsState = confirmed) }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            mutableState.update { it.copy(buttonsState = ((e as? JabraButtonConfirmationException)?.observed ?: it.buttonsState)
                ?.copy(loading = false, error = e.message ?: "Could not confirm button assignments")) }
            throw e
        }
    }

    private fun publishBattery(payload: ByteArray): Boolean {
        val battery = JabraProtocol.battery(payload) ?: return false
        if (!stopped) mutableState.update { it.copy(batteries = battery.components) }
        return true
    }

    private suspend fun readMode(): AmbientMode {
        val value = JabraProtocol.indexedScalar(client.read(JabraProtocol.AMBIENCE, byteArrayOf(1)), 1)
        return value?.let(JabraProtocol::mode) ?: throw IOException("The Jabra headphones did not report a supported noise mode")
    }

    private suspend fun refreshNoise() {
        val mode = readMode()
        mutableState.update { it.copy(noise = it.noise?.copy(mode = mode)) }
        readLevels()
    }

    private suspend fun readLevels() {
        val caps = state.value.noiseCapabilities
        if (caps.ambientMax > 0) {
            val raw = optional { JabraProtocol.indexedScalar(client.read(JabraProtocol.AMBIENCE, byteArrayOf(2)), 2) }
            if (raw != null && raw in 0..caps.ambientMax) mutableState.update {
                it.copy(noise = it.noise?.copy(ambientLevel = caps.ambientMax - raw))
            } else mutableState.update { it.copy(noiseCapabilities = it.noiseCapabilities.copy(ambientMax = 0)) }
        }
        if (caps.ancMax > 0) {
            val raw = optional { JabraProtocol.indexedScalar(client.read(JabraProtocol.AMBIENCE, byteArrayOf(3)), 3) }
            if (raw != null && raw in 0..caps.ancMax) mutableState.update { it.copy(ancLevel = caps.ancMax - raw) }
            else mutableState.update { it.copy(noiseCapabilities = it.noiseCapabilities.copy(ancMax = 0), ancLevel = null) }
        }
    }

    private suspend fun readEqualizer() = JabraEqualizer.parse(client.read(JabraProtocol.EQ, byteArrayOf(0)))
        ?: throw IOException("Invalid Jabra equalizer response")

    private suspend fun refreshEqualizer() {
        val config = optional { readEqualizer() } ?: return
        val (enabled, supportsEnable) = if (equalizerBypassDenied) true to false else try {
            val value = client.read(JabraProtocol.EQ_ENABLED).singleOrNull()?.asBoolean() ?: return
            value to true
        } catch (e: JabraRejectedException) {
            // Elite 10 firmware 4.6.0 exposes the curve but denies its optional enable command.
            // There is no app-controlled bypass on that model/firmware; keep its curve editable.
            if (e.code !in setOf(0xfd, 0xff, 0xf6)) return
            equalizerBypassDenied = true
            true to false
        } catch (e: IOException) { return }
        equalizer = config
        publishEqualizer(config, enabled, supportsEnable)
    }

    private fun publishEqualizer(config: JabraEqualizer, enabled: Boolean,
                                 supportsEnable: Boolean = state.value.eqCaps?.supportsEnable ?: true) {
        mutableState.update { it.copy(eqCaps = config.capability(supportsEnable), eq = JabraEqState(config.bands.map { band -> band.gain }, enabled)) }
    }

    private suspend fun readSleep(): Int {
        val value = client.read(JabraProtocol.SLEEP).singleOrNull()?.u()?.times(5)
        return value?.takeIf { it in JabraProtocol.sleepMinutes } ?: throw IOException("Invalid Jabra sleep timeout")
    }

    private suspend fun readAnnouncements(): Int = client.read(JabraProtocol.ANNOUNCEMENTS).singleOrNull()?.u()
        ?.takeIf { it in 0..2 } ?: throw IOException("Invalid Jabra announcement mode")

    private suspend fun refreshSettings() {
        val identity = model ?: return
        optional { identity.parsePause(client.read(identity.pauseTopic, identity.pauseRead)) }?.let { value ->
            mutableState.update { it.copy(pauseWhenRemoved = value) }
        }
        optional { readSleep() }?.let { value -> mutableState.update { it.copy(sleepMinutes = value) } }
        optional { readAnnouncements() }?.let { value -> mutableState.update { it.copy(announcementMode = value) } }
    }

    /** Serialize write + readback as a unit, dropping slider requests superseded while queued. */
    private suspend fun change(key: String, conflate: Boolean = true, action: suspend () -> Unit) = coroutineScope {
        val operationJob = currentCoroutineContext()[Job]!!
        val cancellation = job.invokeOnCompletion { operationJob.cancel() }
        val token = ticket.incrementAndGet()
        latest[key] = token
        try {
            operations.withLock {
                checkActive()
                check(ready) { "Jabra controls are not ready" }
                if (conflate && latest[key] != token) return@withLock
                try {
                    action()
                    checkActive()
                    mutableState.update { it.copy(error = null) }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    if (!stopped) mutableState.update { it.copy(error = e.message ?: "The Jabra setting could not be confirmed") }
                    throw e
                }
            }
        } finally {
            cancellation.dispose()
        }
    }

    private suspend fun checkActive() {
        currentCoroutineContext().ensureActive()
        if (stopped) throw CancellationException("Jabra session closed")
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (e: CancellationException) { throw e }
        catch (_: IOException) { null }

    private fun verify(confirmed: Boolean) {
        if (!confirmed) throw IOException("The headphones did not apply this setting. Their current setting has been restored.")
    }

    fun close() {
        stopped = true
        ready = false
        latest.clear()
        job.cancel()
        client.close()
    }
}
