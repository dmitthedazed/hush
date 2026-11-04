package com.occaecat.hush

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.DeviceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Short alerts for the current device, whatever its brand: connected, disconnected and low battery, as Android 16
 * live updates (ColorOS shows them as Fluid Cloud capsules) that remove themselves after a few seconds.
 *
 * Promotion needs the app's Live alerts switch (appop POST_PROMOTED_NOTIFICATIONS) and a channel above LOW.
 */
object DeviceEvents {
    private const val CHANNEL = "events"
    private const val ID = 2
    private const val LOW_BATTERY = 10

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(app: Application, scope: CoroutineScope) = scope.launch {
        // A new current device starts from scratch: no alert for the switch itself.
        DeviceModules.current.collectLatest { device ->
            if (device == null) return@collectLatest
            val c = DeviceModules.controller(device)
            launch { connection(app, c) }
            launch { battery(app, c) }
        }
    }

    private suspend fun connection(app: Context, c: DeviceController) {
        var previous = c.state.value.connection
        c.state.map { it.connection }.distinctUntilChanged().collect { now ->
            // A session coming up, not a planned reconnect (those pass through RECONNECTING and stay quiet).
            if (now == Connection.CONNECTED && previous == Connection.CONNECTING) announce(app, c)
            if (now == Connection.DISCONNECTED && (previous == Connection.CONNECTED || previous == Connection.RECONNECTING)) {
                post(app, c, c.state.value.name, "Disconnected", "Off", 4_000)
            }
            previous = now
        }
    }

    /** Once per drop to the threshold; re-armed after charging above it. */
    private suspend fun battery(app: Context, c: DeviceController) {
        var warned = false
        c.state.map { it.battery to it.charging }.distinctUntilChanged().collect { (level, charging) ->
            if (level == null) return@collect
            if (level > LOW_BATTERY || charging) warned = false
            else if (!warned) {
                warned = true
                post(app, c, c.state.value.name, "Low battery · $level%", "$level%", 10_000)
            }
        }
    }

    /** Waits briefly for the first battery read so the alert can show it. */
    private suspend fun announce(app: Context, c: DeviceController) {
        withTimeoutOrNull(2_000) { c.state.first { it.battery != null } }
        val s = c.state.value
        if (!s.connected) return
        val battery = s.battery
        post(app, c, s.name, battery?.let { "Connected · $it%" } ?: "Connected", battery?.let { "$it%" } ?: "On", 5_000)
        // ColorOS quick device connect card, shown by the Xposed module in com.heytap.accessory.
        app.sendBroadcast(
            Intent("com.occaecat.hush.action.SHOW_CONNECT_CARD")
                .setPackage("com.heytap.accessory")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("title", s.name)
                .putExtra("status", battery?.let { "Battery $it%" } ?: "Connected"),
        )
    }

    private fun post(context: Context, c: DeviceController, title: String, text: String, chip: String, durationMs: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Headphone events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val open = c.panelIntent(context) ?: Intent(context, MainActivity::class.java)
        val panel = PendingIntent.getActivity(
            context, 20, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(com.occaecat.hush.ui.R.drawable.ic_stat_headphones)
            .apply { c.renderBitmap(context)?.let { setLargeIcon(Icon.createWithBitmap(it)) } }
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(panel)
            .setOngoing(true)
            .setShowWhen(false)
            .setTimeoutAfter(durationMs)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        // Set through extras so it also works on API 36.0, where the platform setters are missing.
        notification.extras.putBoolean("android.requestPromotedOngoing", true)
        notification.extras.putCharSequence("android.shortCriticalText", chip)
        nm.notify(ID, notification)
    }
}
