package com.occaecat.hush.features

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.diag.HeadsetProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The headset self test ([HeadsetProbe]) for models nobody tried with Hush, and mailing its results to the developer.
 * Runs in its own scope: leaving the page must not stop it halfway, with a setting still switched away.
 */
object HeadsetCheck {
    const val TO = "dmitriyuzat@gmail.com"

    data class Progress(val done: Int, val total: Int, val what: String)

    data class Ui(val running: Boolean = false, val progress: Progress? = null, val report: HeadsetProbe.Report? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun run(context: Context) {
        if (job?.isActive == true) return
        val vm = HeadphonesController.get(context)
        val about = about(context)
        _ui.value = Ui(running = true)
        job = scope.launch {
            val report = HeadsetProbe(vm).run(about) { done, total, what -> _ui.value = _ui.value.copy(progress = Progress(done, total, what)) }
            _ui.value = Ui(report = report)
        }
    }

    /** What Hush saw so far, without the checks: for a headset it could not start a session with. */
    fun collect(context: Context): HeadsetProbe.Report = HeadsetProbe(HeadphonesController.get(context)).collect(about(context))

    private fun about(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return "Hush ${version ?: "?"}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /** Opens the mail app with the report, the capture and the replies attached. */
    suspend fun mail(context: Context, report: HeadsetProbe.Report, note: String) {
        val uris = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "reports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            listOf("report" to report.text, "capture" to report.capture, "replies" to report.replies).map { (kind, text) ->
                val f = File(dir, "${report.fileStem}-$kind.txt").apply { writeText(text) }
                FileProvider.getUriForFile(context, "${context.packageName}.reports", f)
            }
        }
        val checks = if (report.checks.isEmpty()) "The check was not run." else
            "${report.passed} passed, ${report.failed} failed, ${report.skipped} skipped:\n" +
                report.checks.joinToString("\n") { "${it.verdict.name.padEnd(8)} ${it.name}" }
        val body = buildString {
            if (note.isNotBlank()) append(note.trim()).append("\n\n")
            append(report.text.lineSequence().take(3).joinToString("\n")).append("\n\n")
            append(checks).append("\n\nThe full report, every frame of the session and the replies are attached. ")
            append("Bluetooth addresses, device names, the serial number and track names are replaced.")
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(TO))
            .putExtra(Intent.EXTRA_SUBJECT, "Hush headset check: ${report.model ?: "unknown headset"} ${report.firmware.orEmpty()}".trim())
            .putExtra(Intent.EXTRA_TEXT, body)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        // Only mail apps: a chat app would drop the attachments or the address.
        send.selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(send) }.onFailure {
            // No mail app answers the selector: offer every app that takes the files.
            send.selector = null
            context.startActivity(Intent.createChooser(send, "Send the report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
