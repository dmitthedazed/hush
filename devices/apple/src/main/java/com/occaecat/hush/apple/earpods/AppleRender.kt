package com.occaecat.hush.apple.earpods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Apple's own product render (the one on the EarPods (USB-C) support page), cached in the app's files. */
object AppleRender {
    private const val EARPODS = "https://cdsassets.apple.com/content/services/pub/image?productid=301005&size=1000x1000"

    suspend fun earPods(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "render-earpods-usb-c.png")
        if (!file.exists()) runCatching {
            val conn = URL(EARPODS).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
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
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    /** The render if it was downloaded already (widgets can't wait for the network). */
    fun cached(context: Context): Bitmap? =
        File(context.filesDir, "render-earpods-usb-c.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
}
