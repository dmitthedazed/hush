package com.occaecat.hush.sound

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the phone itself is playing, read from the media session (needs notification access, see [MediaWatcher]).
 * The headset's PLAYBACK_CONTROLLER only knows track / artist / album text; the artwork, the position and seeking
 * come from here.
 */
object NowPlaying {

    data class Media(
        val pkg: String,
        val appIcon: Bitmap?,
        val title: String?,
        val artist: String?,
        val album: String?,
        val art: Bitmap?,
        /** Average colour of [art], for tinting the controls over it. */
        val artColor: Int?,
        val durationMs: Long,
        val positionMs: Long,
        /** [SystemClock.elapsedRealtime] of [positionMs]. */
        val positionAt: Long,
        val speed: Float,
        val playing: Boolean,
        val canSeek: Boolean,
        internal val controller: MediaController,
    ) {
        /** The position now, extrapolated while playing. */
        fun positionNow(): Long {
            val p = if (playing) positionMs + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong() else positionMs
            return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
        }

        fun playPause() = controller.transportControls.run { if (playing) pause() else play() }
        fun next() = controller.transportControls.skipToNext()
        fun previous() = controller.transportControls.skipToPrevious()
        fun seekTo(ms: Long) = controller.transportControls.seekTo(ms)
    }

    private val _media = MutableStateFlow<Media?>(null)
    val media: StateFlow<Media?> = _media

    /** Artwork decoding is the expensive part; keep the last one keyed by the bitmap it came from. */
    private var lastArtSource: Bitmap? = null
    private var lastArt: Pair<Bitmap, Int>? = null
    private val icons = mutableMapOf<String, Bitmap?>()

    internal fun update(context: Context, c: MediaController?) {
        if (c == null) { _media.value = null; return }
        val meta = c.metadata
        val state = c.playbackState
        val source = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (source !== lastArtSource) {
            lastArtSource = source
            lastArt = source?.let { runCatching { scaled(it) to average(it) }.getOrNull() }
        }
        val icon = icons.getOrPut(c.packageName) {
            runCatching { context.packageManager.getApplicationIcon(c.packageName).toBitmap(96, 96) }.getOrNull()
        }
        _media.value = Media(
            pkg = c.packageName,
            appIcon = icon,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = lastArt?.first,
            artColor = lastArt?.second,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = state?.position ?: 0L,
            positionAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            playing = state?.state == PlaybackState.STATE_PLAYING,
            canSeek = (state?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L,
            controller = c,
        )
    }

    private fun scaled(b: Bitmap): Bitmap {
        val max = 720
        if (b.width <= max && b.height <= max) return b
        val f = max.toFloat() / maxOf(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    private fun average(b: Bitmap): Int {
        val soft = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
        return Bitmap.createScaledBitmap(soft, 1, 1, true).getPixel(0, 0)
    }
}
