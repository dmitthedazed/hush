package com.occaecat.hush

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.util.Log
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.jabra.firmware.JabraFirmwareUpdateState
import com.occaecat.hush.jabra.firmware.FirmwareUpdateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the process (and so the MDR session in [HeadphonesController]) alive while the headset is in use, with
 * a quiet notification that shows battery and switches the noise mode.
 */
class HushService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { HeadphonesController.get(this) }
    private var firmwareWakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        // A plain silent notification for the controls; Fluid Cloud capsules are separate events (LiveAlerts).
        nm.deleteNotificationChannel("connection")
        nm.deleteNotificationChannel("headphones")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Headphone controls", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        startForeground(ID, build(CardSnapshot(controller.state.value), controller.state.value.firmwareUpdate), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        running = true
        // Adaptive Sound Control and app profiles live as long as the headset session does.
        com.occaecat.hush.sound.SoundAutomation.start(this)
        scope.launch {
            controller.state.map { CardSnapshot(it) to it.firmwareUpdate }.distinctUntilChanged().collect { (card, update) ->
                val transferring = update?.status == FirmwareUpdateStatus.TRANSFERRING
                if (transferring && firmwareWakeLock?.isHeld != true) {
                    firmwareWakeLock = getSystemService(android.os.PowerManager::class.java)
                        .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "$packageName:firmware")
                        .apply { acquire(50 * 60_000L) }
                } else if (!transferring) releaseFirmwareWakeLock()
                nm.notify(ID, build(card, update))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_MODE)?.let { name ->
            runCatching { AmbientMode.valueOf(name) }.getOrNull()?.let(controller::setAmbientMode)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        releaseFirmwareWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun releaseFirmwareWakeLock() {
        firmwareWakeLock?.let { if (it.isHeld) it.release() }
        firmwareWakeLock = null
    }

    private fun build(s: CardSnapshot, update: JabraFirmwareUpdateState? = null): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { if (update != null) putExtra("open", "firmware") },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val status = when {
            update?.status == FirmwareUpdateStatus.AWAITING_CASE -> "Put both earbuds in the case and close it. Wait for the case LED to stop flashing."
            update?.status == FirmwareUpdateStatus.VERIFYING -> "Checking installed firmware"
            update?.status == FirmwareUpdateStatus.TRANSFERRING -> "Updating firmware: ${update.progress?.percent ?: 0}%"
            update?.status == FirmwareUpdateStatus.INTERRUPTED || update?.status == FirmwareUpdateStatus.FAILED -> update.error ?: "Firmware update needs attention"
            s.connecting -> "Connecting…"
            !s.connected -> "Disconnected"
            else -> listOfNotNull(
                s.battery?.let { "$it%" + if (s.charging) " · charging" else "" },
                s.mode?.let { if (s.brand == HeadphoneBrand.JABRA && it == AmbientMode.AMBIENT) "HearThrough" else modeLabel(it) },
                s.components.takeIf { it.isNotEmpty() }?.joinToString(" \u00b7 ") { "${it.label} ${it.level?.let { n -> "$n%" } ?: "Unknown"}" },
            ).joinToString(" · ")
        }
        val panel = PendingIntent.getActivity(
            this, 10, Intent(this, DeviceCardPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_headphones)
            .setLargeIcon(Icon.createWithResource(this, if (s.brand == HeadphoneBrand.JABRA) R.drawable.earbuds else R.drawable.card_xm4))
            .setContentTitle(s.name ?: "Headphones")
            .setContentText(status)
            .setStyle(Notification.BigTextStyle().bigText(status))
            .setContentIntent(if (s.connected && (update == null || update.status == FirmwareUpdateStatus.SUCCEEDED)) panel else open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
        if (update?.status == FirmwareUpdateStatus.TRANSFERRING) builder.setProgress(100, update.progress?.percent ?: 0, false)
        if (s.connected && update?.active != true) {
            for (mode in s.noiseModes) {
                val pi = PendingIntent.getService(
                    this, mode.ordinal + 1,
                    Intent(this, HushService::class.java).putExtra(EXTRA_MODE, mode.name),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                // Short labels: the Fluid Cloud card gives each button about a third of its width.
                val label = when (mode) {
                    AmbientMode.NOISE_CANCELLING -> "NC"
                    AmbientMode.AMBIENT -> if (s.brand == HeadphoneBrand.JABRA) "HearThrough" else "Ambient"
                    AmbientMode.OFF -> "Off"
                }
                builder.addAction(Notification.Action.Builder(null, label, pi).build())
            }
        }
        return builder.build()
    }

    companion object {
        @Volatile var running: Boolean = false
            private set
        private const val TAG = "HushService"
        private const val CHANNEL = "controls"
        private const val ID = 1
        private const val EXTRA_MODE = "mode"

        fun modeLabel(mode: AmbientMode) = when (mode) {
            AmbientMode.NOISE_CANCELLING -> "Noise cancelling"
            AmbientMode.AMBIENT -> "Ambient"
            AmbientMode.OFF -> "Off"
        }

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, HushService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                // Started from the background without an exemption; the session still runs while the process lives.
                Log.w(TAG, "foreground service not allowed now", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "service start failed", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HushService::class.java))
        }
    }
}
