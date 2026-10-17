package com.occaecat.hush.features

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.R
import com.occaecat.hush.update.FirmwareCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Weekly background check of Sony's update server for the last connected headset. The headset's ids (category,
 * service id, model, firmware, serial) are remembered while it is connected; a JobScheduler job (any network,
 * persisted across reboots) runs [FirmwareCheck] once a week and notifies once per new version.
 */
object FirmwareWatch {
    private const val JOB_ID = 4711
    private const val CHANNEL = "firmware"
    private const val PREFS = "fw_watch"

    fun start(context: Context) {
        val app = context.applicationContext
        schedule(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            HeadphonesController.get(app).state
                .map { listOf(it.updateInfo[2], it.updateInfo[3], it.modelName, it.firmware, it.serial) }
                .distinctUntilChanged()
                .collect { (category, service, model, fw, serial) ->
                    if (category == null || service == null || model == null || fw == null) return@collect
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("category", category).putString("service", service).putString("model", model)
                        .putString("fw", fw).putString("serial", serial).apply()
                }
        }
    }

    private fun schedule(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB_ID) != null) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(c, FirmwareJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(7 * 24 * 60 * 60 * 1000L, 24 * 60 * 60 * 1000L)
                .setPersisted(true)
                .build(),
        )
    }

    internal suspend fun run(c: Context) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val category = p.getString("category", null) ?: return
        val service = p.getString("service", null) ?: return
        val model = p.getString("model", null) ?: return
        val fw = p.getString("fw", null) ?: return
        val r = FirmwareCheck.check(c, category, service, model, fw, p.getString("serial", null), force = true)
        if (!r.available || r.latest == null || p.getString("notified", null) == r.latest) return
        p.edit().putString("notified", r.latest).apply()
        notify(c, model, r)
    }

    private fun notify(c: Context, model: String, r: FirmwareCheck.Result) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Firmware updates", NotificationManager.IMPORTANCE_DEFAULT))
        val pkg = "com.sony.songpal.mdr"
        val sc = c.packageManager.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        val update = PendingIntent.getActivity(c, 20, sc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(c, 21,
            Intent(c, com.occaecat.hush.MainActivity::class.java).putExtra("open", "firmware").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = r.changes ?: "Installed ${r.current}"
        nm.notify(JOB_ID, android.app.Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_headphones)
            .setContentTitle("$model: firmware ${r.latest} is available")
            .setContentText(text.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('-', ' ') ?: text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(android.app.Notification.Action.Builder(null, "Update in Sound Connect", update).build())
            .setAutoCancel(true)
            .build())
    }
}

class FirmwareJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val ok = runCatching { FirmwareWatch.run(applicationContext) }.isSuccess
            jobFinished(params, !ok)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
