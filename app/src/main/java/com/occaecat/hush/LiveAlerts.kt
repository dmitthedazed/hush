package com.occaecat.hush

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/**
 * Short ColorOS Fluid Cloud capsules for headset events: connected, disconnected and low battery.
 *
 * Each is an Android 16 live update (promoted ongoing notification) that removes itself after a few seconds.
 * The ongoing controls stay in [HushService]'s plain silent notification. Promotion needs the app's Live alerts
 * switch (appop POST_PROMOTED_NOTIFICATIONS) and a channel above LOW.
 */
object LiveAlerts {
    private const val CHANNEL = "events"
    private const val ID = 2
    const val LOW_BATTERY = 10

    fun connected(context: Context, name: String, battery: Int?) =
        post(context, name, battery?.let { "Connected · $it%" } ?: "Connected", battery?.let { "$it%" } ?: "On", 5_000)

    fun disconnected(context: Context, name: String) =
        post(context, name, "Disconnected", "Off", 4_000)

    fun lowBattery(context: Context, name: String, battery: Int) =
        post(context, name, "Low battery · $battery%", "$battery%", 10_000)

    private fun post(context: Context, title: String, text: String, chip: String, durationMs: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Headphone events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val panel = PendingIntent.getActivity(
            context, 20, Intent(context, DeviceCardPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_headphones)
            .setLargeIcon(Icon.createWithResource(context, R.drawable.card_xm4))
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
