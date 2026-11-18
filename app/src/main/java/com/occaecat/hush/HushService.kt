package com.occaecat.hush

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceState
import com.occaecat.hush.devices.QuickMode
import com.occaecat.hush.devices.appIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the Bluetooth control session in the foreground and shows its battery and noise controls. */
class HushService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.deleteNotificationChannel("connection")
        notifications.deleteNotificationChannel("headphones")
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Headphone controls", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val current = session.value
        val controller = current?.controller
        val initial = card(controller?.state?.value, controller?.quick?.modes?.value.orEmpty())
        try {
            startForeground(ID, build(current, initial), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth foreground service permission unavailable", e)
            stopSelf()
            return
        }
        scope.launch {
            session.collectLatest { selected ->
                if (selected == null) return@collectLatest
                val c = selected.controller
                combine(c.state, c.quick?.modes ?: flowOf(emptyList()), ::card)
                    .distinctUntilChanged()
                    .collect { notifications.notify(ID, build(selected, it)) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val selected = session.value
        val mode = intent?.getStringExtra(EXTRA_MODE)
        if (selected != null && mode != null && selected.key == intent.getStringExtra(EXTRA_DEVICE) &&
            selected.controller.state.value.connection == Connection.CONNECTED) {
            selected.controller.quick?.setMode(mode)
        }
        if (selected == null) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun build(selected: Session?, card: Card): Notification {
        val open = selected?.controller?.panelIntent(this)?.takeIf { card.modes.isNotEmpty() } ?: appIntent(this)
        val panel = PendingIntent.getActivity(this, 10, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.occaecat.hush.ui.R.drawable.ic_stat_headphones)
            .setLargeIcon(com.occaecat.hush.ui.notificationIcon(this))
            .setContentTitle(card.name)
            .setContentText(card.status)
            .setStyle(Notification.BigTextStyle().bigText(card.status))
            .setContentIntent(panel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
        if (selected != null) {
            for (mode in card.modes) {
                val command = Intent(this, HushService::class.java)
                    .setData(Uri.fromParts("hush-mode", selected.key, mode.id))
                    .putExtra(EXTRA_DEVICE, selected.key)
                    .putExtra(EXTRA_MODE, mode.id)
                val action = PendingIntent.getService(this, 0, command, PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(Notification.Action.Builder(null, mode.short, action).build())
            }
        }
        return builder.build()
    }

    private data class Session(val key: String, val controller: DeviceController)
    private data class Card(val name: String, val status: String, val modes: List<QuickMode>)

    private fun card(state: DeviceState?, modes: List<QuickMode>): Card {
        if (state?.connection != Connection.CONNECTED) {
            return Card(state?.name ?: "Headphones", "Connecting…", emptyList())
        }
        val status = listOfNotNull(
            state.battery?.let { "$it%" + if (state.charging) " · charging" else "" },
            modes.firstOrNull { it.active }?.label,
        ).joinToString(" · ").ifEmpty { "Connected" }
        return Card(state.name, status, modes)
    }

    companion object {
        private const val TAG = "HushService"
        private const val CHANNEL = "controls"
        private const val ID = 1
        private const val EXTRA_DEVICE = "device"
        private const val EXTRA_MODE = "mode"
        private lateinit var session: StateFlow<Session?>

        @OptIn(ExperimentalCoroutinesApi::class)
        fun watch(app: Context, scope: CoroutineScope) {
            session = DeviceModules.bluetoothSessions.flatMapLatest { devices ->
                val sessions = devices.map { Session(it.key, DeviceModules.controller(it)) }
                if (sessions.isEmpty()) flowOf(null) else combine(sessions.map { s ->
                    s.controller.state.map { it.connection }.distinctUntilChanged()
                }) { states ->
                    val index = states.indexOf(Connection.CONNECTED).takeIf { it >= 0 }
                        ?: states.indexOfFirst { it != Connection.DISCONNECTED }
                    sessions.getOrNull(index)
                }
            }.stateIn(scope, SharingStarted.Eagerly, null)
            scope.launch {
                session.collect {
                    if (it != null) startIfActive(app)
                    else app.stopService(Intent(app, HushService::class.java))
                }
            }
        }

        fun startIfActive(context: Context) {
            if (session.value == null) return
            try {
                context.startForegroundService(Intent(context, HushService::class.java))
            } catch (e: IllegalStateException) {
                // Android may deny background starts. The session can still run, and opening Hush retries.
                Log.w(TAG, "Could not start headphone controls", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not start headphone controls", e)
            }
        }
    }
}
