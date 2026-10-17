package com.occaecat.hush.features

import android.content.Context
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import com.occaecat.hush.sound.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Sleep timer: at the end the headset volume fades down over [FADE_MS], playback pauses, then the headphones turn
 * off (or the volume goes back to where it was, so the next listen isn't silent). "End of track" waits for the
 * playing title to change instead of a clock.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private const val FADE_MS = 45_000L

    fun powerOffWhenDone(c: Context) = c.getSharedPreferences("sleep", Context.MODE_PRIVATE).getBoolean("powerOff", true)
    fun setPowerOffWhenDone(c: Context, on: Boolean) = c.getSharedPreferences("sleep", Context.MODE_PRIVATE).edit().putBoolean("powerOff", on).apply()

    fun start(context: Context, minutes: Int) {
        cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.value = State.Counting(endsAt)
        job = scope.launch {
            delay((endsAt - System.currentTimeMillis() - FADE_MS).coerceAtLeast(0))
            finish(context)
        }
    }

    fun startEndOfTrack(context: Context) {
        cancel()
        _state.value = State.EndOfTrack
        val controller = HeadphonesController.get(context)
        job = scope.launch {
            // Either source of the title may report the change first: the media session or the headset itself.
            val session = NowPlaying.media.map { it?.title }.distinctUntilChanged().drop(1)
            val headset = controller.state.map { it.track }.distinctUntilChanged().drop(1)
            kotlinx.coroutines.flow.merge(session, headset).first()
            finish(context, fade = false)
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

    private suspend fun finish(context: Context, fade: Boolean = true) {
        val controller = HeadphonesController.get(context)
        val start = controller.state.value.volume
        _state.value = State.Fading
        if (fade && start != null && start > 0) {
            val steps = start
            for (i in 1..steps) {
                delay(FADE_MS / steps)
                controller.setVolume(start - i)
            }
        }
        val media = NowPlaying.media.value
        if (media?.playing == true) media.playPause() else if (controller.state.value.playing == true) controller.playPause()
        delay(1_500)
        if (powerOffWhenDone(context) && controller.state.value.phase == Phase.CONNECTED) controller.powerOff()
        else if (start != null) controller.setVolume(start)
        job = null
        _state.value = State.Off
    }
}
