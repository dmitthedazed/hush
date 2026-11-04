package com.occaecat.hush.wired

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
 * The equalizer effect lives in Hush's process, so while a phone-side equalizer runs (wired headphones with the EQ on), this
 * keeps the process alive with a quiet notification.
 */
class PhoneEqService : Service() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Phone equalizer", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        // connectedDevice needs BLUETOOTH_CONNECT granted; without it the EQ still runs, only without the keep-alive.
        try {
            startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: SecurityException) {
            Log.w("PhoneEqService", "not started", e)
            stopSelf()
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 20, com.occaecat.hush.devices.appIntent(this), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.occaecat.hush.ui.R.drawable.ic_stat_headphones)
            .setContentTitle(PhoneEq.running ?: "Hush")
            .setContentText("Equalizer on")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Already running for another device: the title follows the one that owns the effect now.
        getSystemService(NotificationManager::class.java).notify(ID, notification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "phone_eq"
        private const val ID = 7

        /** Runs the service exactly while [PhoneEq] runs; start failures (background limits) only cost persistence. */
        fun sync(context: Context) {
            val intent = Intent(context, PhoneEqService::class.java)
            if (PhoneEq.running == null) {
                context.stopService(intent)
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.w("PhoneEqService", "not started", e)
            }
        }
    }
}
