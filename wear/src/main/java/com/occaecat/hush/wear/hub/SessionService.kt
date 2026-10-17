package com.occaecat.hush.wear.hub

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.occaecat.hush.link.Snapshot
import com.occaecat.hush.wear.R
import com.occaecat.hush.wear.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds the process (and so the standalone RFCOMM session) while the watch itself drives the headset. Shown as
 * an Ongoing Activity: the chip on the watch face and the entry in the launcher's recents jump back into Hush.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Headphone session", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        val local = HushHub.get(this).local
        startForeground(ID, build(Snapshot.from(local.state.value)), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        scope.launch {
            local.state.map(Snapshot::from).distinctUntilChanged().collect { nm.notify(ID, build(it)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun build(s: Snapshot): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = WearLabels.status(s, Route.WATCH, phoneReachable = false) +
            if (s.connected && s.mode != null) " · " + WearLabels.modeShort(s.mode) else ""
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_xm4)
            .setContentTitle(WearLabels.name(s))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(
                WearLabels.modeIcon(s.mode),
                "Switch noise mode",
                ActionReceiver.pending(this, ActionReceiver.ACTION_CYCLE),
            )
        OngoingActivity.Builder(this, ID, builder)
            .setStaticIcon(R.drawable.ic_xm4)
            .setTouchIntent(open)
            .setStatus(Status.forPart(Status.TextPart(text)))
            .setTitle(WearLabels.name(s))
            .build()
            .apply(this)
        return builder.build()
    }

    companion object {
        private const val TAG = "SessionService"
        private const val CHANNEL = "session"
        private const val ID = 7

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, SessionService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "foreground service not allowed now", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "foreground service needs Bluetooth permission", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
