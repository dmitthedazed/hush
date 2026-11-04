package com.occaecat.hush.wired

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A maker's product render, downloaded once into the app's files ("render-<name>.png"). */
object RemoteRender {
    private fun file(context: Context, name: String) = File(context.filesDir, "render-$name.png")

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

    /** Cronet is the platform's HTTP/2 client (Android 14+); HttpURLConnection only speaks HTTP/1.1. */
    @Volatile private var engine: HttpEngine? = null

    private fun open(context: Context, url: String): HttpURLConnection {
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) runCatching {
            val e = engine ?: HttpEngine.Builder(context.applicationContext).setUserAgent(BROWSER_UA).build().also { engine = it }
            return e.openConnection(URL(url)) as HttpURLConnection
        }
        return URL(url).openConnection() as HttpURLConnection
    }

    /** [trim]: crop transparent margins (renders that sit small in a padded square). */
    suspend fun load(context: Context, name: String, url: String, trim: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(context, name)
        if (!file.exists()) runCatching {
            val conn = open(context, url)
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            // Sony's shop (sony.sk, Akamai) answers 403 unless the request looks like a browser: these headers over HTTP/2.
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/*,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "en,sk;q=0.8")
            try {
                if (conn.responseCode == 200) {
                    val tmp = File(file.path + ".part")
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    tmp.renameTo(file)
                }
            } finally {
                conn.disconnect()
            }
        }
        decode(file, trim)
    }

    /** The render if it was downloaded already (widgets can't wait for the network). */
    fun cached(context: Context, name: String, trim: Boolean = false): Bitmap? = decode(file(context, name), trim)

    private fun decode(file: File, trim: Boolean): Bitmap? {
        if (!file.exists()) return null
        val b = BitmapFactory.decodeFile(file.path) ?: return null
        if (!trim || !b.hasAlpha()) return b
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        var l = b.width; var t = b.height; var r = -1; var btm = -1
        for (y in 0 until b.height) for (x in 0 until b.width) if (px[y * b.width + x] ushr 24 > 8) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > btm) btm = y
        }
        if (r < l || btm < t) return b
        return Bitmap.createBitmap(b, l, t, r - l + 1, btm - t + 1)
    }
}
