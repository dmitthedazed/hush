package com.occaecat.hush.apple.earpods

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * The equalizer effect lives in Hush's process, so while the EarPods are plugged in with the EQ on, this keeps the
 * process alive with a quiet notification.
 */
class EarPodsService : Service() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "EarPods equalizer", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        val open = PendingIntent.getActivity(
            this, 20, com.occaecat.hush.devices.appIntent(this), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.occaecat.hush.ui.R.drawable.ic_stat_headphones)
            .setContentTitle("EarPods")
            .setContentText("Equalizer on")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "earpods"
        private const val ID = 7

        /** Runs the service exactly while [needed]; start failures (background limits) only cost persistence. */
        fun sync(context: Context, needed: Boolean) {
            val intent = Intent(context, EarPodsService::class.java)
            if (!needed) {
                context.stopService(intent)
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.w("EarPodsService", "not started", e)
            }
        }
    }
}
