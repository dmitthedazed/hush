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
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.R
import com.occaecat.hush.update.AppUpdate
import com.occaecat.hush.update.FirmwareCheck
import com.occaecat.hush.update.JabraFirmware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Weekly manufacturer firmware check for the last verified headset identity. Sony update identifiers and
 * Jabra product IDs stay separate; no downloads or installations run in this background job.
 * The same job checks Hush's own GitHub releases ([AppUpdate]).
 */
object FirmwareWatch {
    private const val JOB_ID = 4711
    private const val CHANNEL = "firmware"
    private const val APP_CHANNEL = "app_updates"
    private const val PREFS = "fw_watch"

    private data class Target(val address: String?, val brand: String, val productId: Int?, val category: String?,
                              val service: String?, val model: String?, val firmware: String?, val serial: String?)
    private fun target(state: HeadphonesState) = Target(state.address, state.brand.name, state.jabraProductId,
        state.updateInfo[2], state.updateInfo[3], state.modelName, state.firmware, state.serial)

    fun start(context: Context) {
        val app = context.applicationContext
        schedule(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            HeadphonesController.get(app).state
                .map(::target)
                .distinctUntilChanged()
                .collect { target ->
                    val preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val changed = preferences.getString("address", null) != target.address || preferences.getString("brand", "SONY") != target.brand
                    if (changed || target.address == null) {
                        preferences.edit().remove("category").remove("service").remove("model").remove("fw")
                            .remove("serial").remove("productId").remove("notified")
                            .putString("address", target.address).putString("brand", target.brand).apply()
                        app.getSystemService(NotificationManager::class.java).cancel(JOB_ID)
                    }
                    if (target.address == null || target.model == null || target.firmware == null) return@collect
                    if (target.brand == "JABRA") {
                        val pid = target.productId?.takeIf(JabraFirmware::supports) ?: return@collect
                        preferences.edit().putInt("productId", pid).putString("model", target.model)
                            .putString("fw", target.firmware).putString("brand", target.brand).apply()
                    } else if (target.brand == "SONY" && target.category != null && target.service != null) {
                        preferences.edit().putString("category", target.category).putString("service", target.service)
                            .putString("model", target.model).putString("fw", target.firmware).putString("serial", target.serial)
                            .putString("brand", target.brand).apply()
                    }
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
        val app = runCatching { checkApp(c) }
        runCatching { checkFirmware(c) }.getOrThrow()
        app.getOrThrow()
    }

    private suspend fun checkApp(c: Context) {
        val r = AppUpdate.check(c, force = true)
        val v = r.release?.version?.takeIf { r.available } ?: return
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("notified_app", null) == v) return
        p.edit().putString("notified_app", v).apply()
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(APP_CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val text = r.release.notes?.lineSequence()?.map { it.trim().trimStart('#', '-', '*', ' ') }?.firstOrNull { it.isNotBlank() }
            ?: "Installed ${r.installed}"
        nm.notify(JOB_ID + 1, android.app.Notification.Builder(c, APP_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_headphones)
            .setContentTitle("Hush $v is available")
            .setContentText(text)
            .setContentIntent(openUpdates(c))
            .setAutoCancel(true)
            .build())
    }

    private fun openUpdates(c: Context) = PendingIntent.getActivity(c, 21,
        Intent(c, com.occaecat.hush.MainActivity::class.java).putExtra("open", "updates").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private suspend fun checkFirmware(c: Context) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("brand", "SONY") == "JABRA") {
            val pid = p.getInt("productId", 0).takeIf(JabraFirmware::supports) ?: return
            val installed = p.getString("fw", null) ?: return
            val address = p.getString("address", null) ?: return
            val manifest = JabraFirmware.check(c, pid, force = true)
            val identity = "jabra:$pid:${manifest.version}"
            if (!manifest.available(installed) || p.getString("notified", null) == identity ||
                p.getString("address", null) != address || p.getInt("productId", 0) != pid || p.getString("fw", null) != installed) return
            p.edit().putString("notified", identity).apply()
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Firmware updates", NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(JOB_ID, android.app.Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_headphones)
                .setContentTitle("${manifest.productName}: firmware ${manifest.version} is available")
                .setContentText("Installed $installed. Open Hush to review the update.")
                .setContentIntent(openUpdates(c)).setAutoCancel(true).build())
            return
        }
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
        val open = openUpdates(c)
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
