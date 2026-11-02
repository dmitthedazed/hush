package com.occaecat.hush.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.occaecat.hush.devices.Model
import com.occaecat.hush.wired.RemoteRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** A product picture ready to draw, and whether it is mostly light (it then sits on a dark disc). */
@Immutable
class ProductArt(val image: ImageBitmap, val light: Boolean)

/**
 * The add screens' product pictures, decoded once off the main thread and kept in memory: a long brand list (Sony's
 * fifty renders) used to decode and trim every full-size render on the main thread while the page slid in. Thumbnails
 * are scaled down to [THUMB_PX]; the model page's large picture starts from the thumbnail (so the shared element has
 * something to grow from) and swaps to the full render when it's ready.
 */
object ProductArtCache {
    const val THUMB_PX = 160

    private val thumbs = LruCache<String, ProductArt>(80)
    private val full = LruCache<String, ProductArt>(4)

    /** Two decodes at a time: fifty at once would fight the page transition for CPU and memory. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val decoder = Dispatchers.IO.limitedParallelism(2)

    fun peek(model: Model, large: Boolean): ProductArt? = (if (large) full else thumbs).get(model.id)
        ?: if (large) thumbs.get(model.id) else null

    suspend fun load(context: Context, model: Model, large: Boolean): ProductArt? {
        (if (large) full else thumbs).get(model.id)?.let { return it }
        return withContext(decoder) {
            val bitmap = model.image?.let { BitmapFactory.decodeResource(context.resources, it) }
                ?: model.imageUrl?.let { RemoteRender.load(context, model.id, it, trim = true) }
                ?: return@withContext null
            val light = isLight(bitmap)
            val thumb = ProductArt(bitmap.scaledTo(THUMB_PX).also { it.prepareToDraw() }.asImageBitmap(), light)
            thumbs.put(model.id, thumb)
            if (!large) return@withContext thumb
            ProductArt(bitmap.also { it.prepareToDraw() }.asImageBitmap(), light).also { full.put(model.id, it) }
        }
    }

    private fun Bitmap.scaledTo(px: Int): Bitmap {
        val s = px.toFloat() / maxOf(width, height)
        return if (s >= 1f) this else Bitmap.createScaledBitmap(this, (width * s).toInt().coerceAtLeast(1), (height * s).toInt().coerceAtLeast(1), true)
    }

    /** Whether the visible pixels of a product picture are mostly light (sampled on a coarse grid). */
    private fun isLight(b: Bitmap): Boolean {
        var sum = 0.0; var n = 0
        val step = maxOf(1, minOf(b.width, b.height) / 48)
        for (y in 0 until b.height step step) for (x in 0 until b.width step step) {
            val p = b.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 128) continue
            sum += 0.2126 * android.graphics.Color.red(p) + 0.7152 * android.graphics.Color.green(p) + 0.0722 * android.graphics.Color.blue(p)
            n++
        }
        return n > 0 && sum / n > 160
    }
}

/** [model]'s picture: what's in memory at once (nothing is decoded on the main thread), the rest when it's loaded. */
@Composable
fun rememberProductArt(model: Model?, large: Boolean = false): ProductArt? {
    if (model == null || (model.image == null && model.imageUrl == null)) return null
    val context = LocalContext.current.applicationContext
    val art by produceState(ProductArtCache.peek(model, large), model.id, large) {
        ProductArtCache.load(context, model, large)?.let { value = it }
    }
    return art
}
