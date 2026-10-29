@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.HushService
import com.occaecat.hush.Phase
import com.occaecat.hush.jabra.firmware.FirmwareUpdateStatus
import com.occaecat.hush.jabra.firmware.JabraFirmwarePackage
import com.occaecat.hush.jabra.firmware.JabraFirmwareReadiness
import com.occaecat.hush.update.JabraFirmware
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class JabraUpdateUi {
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var readiness by mutableStateOf<JabraFirmwareReadiness?>(null)
    var prepared by mutableStateOf<JabraFirmwarePackage?>(null)
    var review by mutableStateOf(false)
    var job: Job? = null
}

@Composable
internal fun JabraFirmwareSection(state: HeadphonesState, firmware: FirmwareUi) {
    val context = LocalContext.current
    val controller = remember { HeadphonesController.get(context) }
    val scope = rememberCoroutineScope()
    val ui = remember(state.address, state.jabraProductId, state.firmware) { JabraUpdateUi() }
    DisposableEffect(ui) { onDispose { ui.job?.cancel() } }
    val manifest = firmware.jabraResult
    val comparison = manifest?.let { release -> state.firmware?.let { runCatching { JabraFirmware.compare(release.version, it) } } }
    val available = (comparison?.getOrNull() ?: 0) > 0
    val operation = state.firmwareUpdate?.takeIf { it.address == state.address }
    val active = operation?.status in setOf(FirmwareUpdateStatus.TRANSFERRING, FirmwareUpdateStatus.AWAITING_CASE, FirmwareUpdateStatus.VERIFYING)

    fun stillSelected(): Boolean {
        val current = controller.state.value
        return current.address == state.address && current.jabraProductId == state.jabraProductId &&
            current.firmware == state.firmware && current.phase == Phase.CONNECTED
    }

    fun prepare() {
        val release = manifest ?: return
        ui.job?.cancel()
        ui.job = scope.launch {
            ui.busy = true; ui.error = null; ui.prepared = null; ui.readiness = null
            ui.message = "Checking the headphones…"
            try {
                val readiness = controller.readJabraFirmwareReadiness()
                if (!stillSelected()) throw CancellationException("Headphones changed")
                ui.readiness = readiness
                val language = readiness.languageId ?: error("The headphones did not report their firmware language")
                require(readiness.ready) { readiness.blockers.joinToString("\n") }
                require(readiness.productId == release.productId && release.available(readiness.currentVersion)) { "A newer update is no longer available for these headphones" }
                val prepared = JabraFirmware.prepare(context, release, language) { progress ->
                    ui.message = "Downloading and checking files · ${progress.completedFiles}/${progress.totalFiles}"
                }
                if (!stillSelected()) throw CancellationException("Headphones changed")
                ui.prepared = prepared
                ui.review = true
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { ui.error = failure.message ?: "Could not prepare the update"
            } finally { ui.busy = false; ui.message = null }
        }
    }

    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = if (available) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(when {
                active -> "Updating the headphones"
                manifest == null && firmware.failed -> "Couldn't check Jabra firmware"
                firmware.checking -> "Checking with Jabra…"
                manifest == null -> "Connect the headphones to check"
                available -> "Firmware ${manifest?.version} is available"
                state.firmware == null -> "Installed firmware version unavailable"
                comparison?.isFailure == true -> "Installed firmware version unrecognized"
                (comparison?.getOrNull() ?: 0) < 0 -> "Installed firmware is newer than the published release"
                else -> "Firmware is up to date"
            }, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(listOfNotNull(state.firmware?.let { "Installed $it" }, manifest?.released?.let { "Published $it" }).joinToString(" · "))
            if (firmware.failed) firmware.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            operation?.let { update ->
                val description = when (update.status) {
                    FirmwareUpdateStatus.TRANSFERRING -> update.progress?.message ?: "Transferring and verifying firmware…"
                    FirmwareUpdateStatus.AWAITING_CASE -> "Place both earbuds in their charging case and close the lid. Keep the case charged and wait for its update indicator to finish before taking the earbuds out."
                    FirmwareUpdateStatus.VERIFYING -> "Reconnect the headphones so Hush can verify the installed version."
                    FirmwareUpdateStatus.SUCCEEDED -> "Firmware ${update.targetVersion} is installed and verified."
                    FirmwareUpdateStatus.FAILED -> update.error ?: "The update could not be completed."
                    FirmwareUpdateStatus.INTERRUPTED -> update.error ?: "The previous update was interrupted. Reconnect the headphones and check their installed firmware before trying again."
                }
                Text(description)
                if (update.status !in setOf(FirmwareUpdateStatus.FAILED, FirmwareUpdateStatus.INTERRUPTED)) {
                    update.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                if (update.status == FirmwareUpdateStatus.TRANSFERRING) {
                    val fraction = (update.progress?.percent ?: 0).coerceIn(0, 100) / 100f
                    LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
                if (update.canCancel) OutlinedButton(onClick = controller::cancelJabraFirmwareUpdate) { Text("Cancel update") }
                if (update.status in setOf(FirmwareUpdateStatus.AWAITING_CASE, FirmwareUpdateStatus.INTERRUPTED, FirmwareUpdateStatus.FAILED)) {
                    Text("After the case update light has stopped flashing, take out the earbuds and check the installed version.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = controller::verifyJabraFirmwareUpdate) { Text("Check installed version") }
                }
                if (update.status in setOf(FirmwareUpdateStatus.SUCCEEDED, FirmwareUpdateStatus.FAILED, FirmwareUpdateStatus.INTERRUPTED)) {
                    TextButton(onClick = controller::dismissJabraFirmwareUpdate) { Text("Dismiss") }
                }
            }
            if (!active && available) {
                Text("Experimental installation in Hush. Keep both earbuds and the case charged; close other headphone apps. Installation finishes in the charging case.",
                    style = MaterialTheme.typography.bodySmall)
                if (ui.busy) {
                    Text(ui.message ?: "Preparing update…")
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { ui.job?.cancel() }) { Text("Cancel download") }
                } else Button(onClick = ::prepare, enabled = state.phase == Phase.CONNECTED && manifest?.productId == state.jabraProductId,
                    modifier = Modifier.fillMaxWidth()) { Text("Download and review update") }
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            manifest?.releaseNotesUrl?.let { url ->
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) { Text("Jabra release notes") }
            }
            if (!active) OutlinedButton(onClick = {
                val launch = context.packageManager.getLaunchIntentForPackage("com.jabra.moments")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.jabra.moments"))
                runCatching { context.startActivity(launch) }
            }) { Text("Open Jabra Sound+") }
        }
    }

    val prepared = ui.prepared
    if (ui.review && prepared != null) AlertDialog(
        onDismissRequest = { ui.review = false },
        title = { Text("Install firmware ${prepared.version}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("${state.modelName ?: state.displayName}\n${state.firmware} → ${prepared.version}")
                Text("${manifest?.languages?.get(prepared.languageId) ?: "Current language"} · %.1f MB".format(prepared.totalBytes / 1_048_576.0))
                Text("The download from Jabra has been verified for these headphones.", style = MaterialTheme.typography.bodySmall)
                Text("This updater is experimental. Keep the phone nearby and charged. After transfer, put both earbuds in the charged case and wait for installation to finish. Do not interrupt installation.")
            }
        },
        confirmButton = { Button(onClick = {
            ui.review = false
            val selected = stillSelected()
            if (selected && !HushService.running) HushService.start(context)
            if (selected && !HushService.running) {
                ui.error = "Keep Hush open and reconnect before installing."
            } else if (!selected || !controller.startJabraFirmwareUpdate(prepared)) {
                ui.error = "The headphones are no longer ready. Download and review the update again."
                ui.prepared = null
            }
        }, enabled = stillSelected()) { Text("Install update") } },
        dismissButton = { TextButton(onClick = { ui.review = false }) { Text("Not now") } },
    )
}
