package com.occaecat.hush.features

import android.content.Context
import android.media.AudioManager
import com.occaecat.hush.sound.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** What the sleep timer acts on: the device's own volume and controls, or the phone's. */
interface SleepTarget : com.occaecat.hush.devices.Capability {
    /** Current volume and its maximum; null when unknown. */
    val volume: Int?
    fun setVolume(v: Int)
    /** Pause playback when the phone's media session isn't readable. */
    fun pause() {}
    /** Titles the device itself reports, for "end of track". */
    val titles: Flow<String?> get() = emptyFlow()
    /** True when the device can be switched off at the end. */
    val canPowerOff: Boolean get() = false
    fun powerOff() {}
    /** Names the device in the sheet ("headphones", "EarPods"). */
    val noun: String get() = "headphones"
}

/** The phone's own music volume, for devices without a volume of their own (wired EarPods). */
class PhoneSleepTarget(context: Context, override val noun: String = "headphones") : SleepTarget {
    override val providedBy get() = com.occaecat.hush.devices.Source.PHONE
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    override val volume get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    override fun setVolume(v: Int) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceAtLeast(0), 0)
}

/**
 * Sleep timer, for any device: at the end the volume fades down over [FADE_MS], playback pauses, then the device turns
 * off when it can and the user wants that, or the volume goes back to where it was so the next listen isn't silent.
 * "End of track" waits for the playing title to change instead of a clock.
 */
object SleepTimer {

    sealed interface State {
        data object Off : State
        data class Counting(val endsAt: Long) : State
        data object EndOfTrack : State
        data object Fading : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state

    /** The device shown in the app (set by the shell), or the one a device module registered at start. */
    var target: SleepTarget? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private const val FADE_MS = 45_000L

    fun powerOffWhenDone(c: Context) = c.getSharedPreferences("sleep", Context.MODE_PRIVATE).getBoolean("powerOff", true)
    fun setPowerOffWhenDone(c: Context, on: Boolean) = c.getSharedPreferences("sleep", Context.MODE_PRIVATE).edit().putBoolean("powerOff", on).apply()

    private fun targetFor(context: Context) = target ?: PhoneSleepTarget(context)

    fun start(context: Context, minutes: Int) {
        cancel()
        val t = targetFor(context)
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.value = State.Counting(endsAt)
        job = scope.launch {
            delay((endsAt - System.currentTimeMillis() - FADE_MS).coerceAtLeast(0))
            finish(context, t)
        }
    }

    fun startEndOfTrack(context: Context) {
        cancel()
        val t = targetFor(context)
        _state.value = State.EndOfTrack
        job = scope.launch {
            // Either source of the title may report the change first: the media session or the device itself.
            val session = NowPlaying.media.map { it?.title }.distinctUntilChanged().drop(1)
            val device = t.titles.distinctUntilChanged().drop(1)
            merge(session, device).first()
            finish(context, t, fade = false)
        }
    }

    /** Adds minutes to a running timer (the "+15" chip). */
    fun extend(context: Context, minutes: Int) {
        val s = _state.value as? State.Counting ?: return start(context, minutes)
        start(context, (((s.endsAt - System.currentTimeMillis()) / 60_000L).toInt() + minutes).coerceAtLeast(1))
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Off
    }

    private suspend fun finish(context: Context, t: SleepTarget, fade: Boolean = true) {
        val start = t.volume
        _state.value = State.Fading
        if (fade && start != null && start > 0) {
            for (i in 1..start) {
                delay(FADE_MS / start)
                t.setVolume(start - i)
            }
        }
        val media = NowPlaying.media.value
        if (media?.playing == true) media.playPause() else t.pause()
        delay(1_500)
        if (t.canPowerOff && powerOffWhenDone(context)) t.powerOff()
        else if (start != null) t.setVolume(start)
        job = null
        _state.value = State.Off
    }
}
