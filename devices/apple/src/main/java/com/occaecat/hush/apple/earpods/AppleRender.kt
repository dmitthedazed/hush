package com.occaecat.hush.apple.earpods

import android.content.Context
import android.graphics.Bitmap
import com.occaecat.hush.wired.RemoteRender

/** Apple's own product render (the one on the EarPods (USB-C) support page), cached in the app's files. */
object AppleRender {
    private const val EARPODS = "https://cdsassets.apple.com/content/services/pub/image?productid=301005&size=1000x1000"

    suspend fun earPods(context: Context): Bitmap? = RemoteRender.load(context, "earpods-usb-c", EARPODS)

    fun cached(context: Context): Bitmap? = RemoteRender.cached(context, "earpods-usb-c")
}
