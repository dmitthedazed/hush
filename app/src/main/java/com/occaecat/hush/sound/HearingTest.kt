package com.occaecat.hush.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A quick hearing check mapped onto the headset's six bands. Each band gets a pulsed tone that climbs from
 * very quiet until the listener says they hear it; the thresholds relative to 1 kHz are compared with normal
 * hearing (ISO 226 threshold of hearing, also relative to 1 kHz), and half of any loss is made up with the
 * band's gain — the half-gain rule hearing aids start from. Never cuts, never boosts more than 6 dB.
 * It measures ear + headphones together at the current volume, so it is a comfort tweak, not an audiogram.
 */
object HearingTest {
    data class Tone(val band: Int, val hz: Double, val label: String, val isoThreshold: Double)

    /** Test frequencies per band: Clear Bass at 80 Hz, the 16k band at 12.5 kHz where most adults still hear. */
    val tones = listOf(
        Tone(2, 1000.0, "1 kHz", 2.4),
        Tone(1, 400.0, "400 Hz", 6.2),
        Tone(3, 2500.0, "2.5 kHz", -4.2),
        Tone(4, 6300.0, "6.3 kHz", 6.0),
        Tone(0, 80.0, "80 Hz", 31.5),
        Tone(5, 12500.0, "12.5 kHz", 12.3),
    )

    const val START_DB = -80.0
    const val STEP_DB = 4.0
    const val MAX_DB = -8.0
    private const val RATE = 48_000

    /**
     * Plays [tone] in bursts of three beeps, one step louder each burst, calling [onLevel] before each burst.
     * Returns when the caller's coroutine is cancelled (the listener answered) or after the loudest burst.
     */
    suspend fun play(tone: Tone, onLevel: (Double) -> Unit) = withContext(Dispatchers.Default) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            var db = START_DB
            while (coroutineContext.isActive && db <= MAX_DB) {
                onLevel(db)
                val burst = burst(tone.hz, 10.0.pow(db / 20).toFloat())
                // Short writes so an answer silences the tone at once instead of after the whole burst.
                val chunk = RATE / 20 * 2
                var off = 0
                while (off < burst.size && coroutineContext.isActive) {
                    val n = minOf(chunk, burst.size - off)
                    track.write(burst, off, n, AudioTrack.WRITE_BLOCKING)
                    off += n
                }
                db += STEP_DB
            }
        } finally {
            track.pause(); track.flush(); track.release()
        }
    }

    /** Three 220 ms beeps with soft edges and 180 ms gaps, then a 500 ms pause; stereo float frames. */
    private fun burst(hz: Double, amp: Float): FloatArray {
        val beep = (RATE * 0.22).toInt()
        val gap = (RATE * 0.18).toInt()
        val tail = (RATE * 0.5).toInt()
        val frames = 3 * (beep + gap) + tail
        val out = FloatArray(frames * 2)
        val ramp = (RATE * 0.02).toInt()
        for (b in 0 until 3) {
            val start = b * (beep + gap)
            for (i in 0 until beep) {
                val env = when { i < ramp -> i / ramp.toFloat(); i > beep - ramp -> (beep - i) / ramp.toFloat(); else -> 1f }
                val v = (sin(2 * PI * hz * i / RATE) * amp * env).toFloat()
                out[(start + i) * 2] = v
                out[(start + i) * 2 + 1] = v
            }
        }
        return out
    }

    /** Headset levels (0..20, 10 = flat) from the dBFS level each tone was first heard at. */
    fun levels(heardAt: Map<Int, Double>): List<Int> {
        val ref = tones.first { it.hz == 1000.0 }
        val refHeard = heardAt[ref.band] ?: return List(6) { 10 }
        return List(6) { band ->
            val t = tones.first { it.band == band }
            val heard = heardAt[band] ?: MAX_DB + STEP_DB // never heard: at least one step past the loudest
            val loss = (heard - refHeard) - (t.isoThreshold - ref.isoThreshold)
            val gain = if (loss > 3) (loss / 2).roundToInt().coerceIn(0, 6) else 0
            10 + gain
        }
    }
}
