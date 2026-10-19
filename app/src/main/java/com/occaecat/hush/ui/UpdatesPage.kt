@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.occaecat.hush.update.AppUpdate
import kotlinx.coroutines.launch
import com.occaecat.hush.devices.Firmware

/** State of the GitHub release check for Hush itself. */
class AppUpdateUi(installed: String) {
    var result by mutableStateOf(AppUpdate.Result(installed, null))
    var checked by mutableStateOf(false)
    var checking by mutableStateOf(false)
    var failed by mutableStateOf(false)
}

/** Checks GitHub once per launch (cached 6 h). */
@Composable
fun rememberAppUpdate(): AppUpdateUi {
    val context = LocalContext.current
    val ui = remember { AppUpdateUi(AppUpdate.installed(context)) }
    LaunchedEffect(Unit) {
        AppUpdate.cached(context)?.let { ui.result = it; ui.checked = true }
        ui.check(context, force = false)
    }
    return ui
}

suspend fun AppUpdateUi.check(context: android.content.Context, force: Boolean) {
    checking = true; failed = false
    runCatching { AppUpdate.check(context, force) }
        .onSuccess { result = it; checked = true }.onFailure { failed = true }
    checking = false
}

/** One line for the Updates row: what can be updated, otherwise that everything is current. */
fun updatesSummary(app: AppUpdateUi, firmwarePending: String?, firmwareChecking: Boolean): String {
    val pending = listOfNotNull(
        app.result.release?.version?.takeIf { app.result.available }?.let { "Hush $it" },
        firmwarePending,
    )
    return when {
        pending.isNotEmpty() -> pending.joinToString(" and ").replaceFirstChar { it.uppercase() } + " available"
        app.checking || firmwareChecking -> "Checking…"
        app.failed && !app.checked -> "Couldn't check"
        else -> "Up to date · Hush ${app.result.installed}"
    }
}

/** Hush from GitHub and, when the device has updatable firmware, the device's own section, on one page. */
@Composable
fun UpdatesPage(app: AppUpdateUi, firmware: Firmware?, deviceTitle: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fwChecking = firmware?.checking() == true
    FullScreenPage("Updates", onClose) {
        SectionHeader("Hush", top = Space.s)
        AppSection(app)
        if (firmware != null) {
            SectionHeader(deviceTitle)
            firmware.Section()
        }
        Spacer(Modifier.height(Space.l))
        OutlinedButton(
            enabled = !app.checking && !fwChecking,
            onClick = {
                scope.launch { app.check(context, force = true) }
                if (firmware != null) scope.launch { firmware.recheck(context) }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(if (app.checking || fwChecking) "Checking…" else "Check again") }
        Spacer(Modifier.height(Space.l))
    }
}

@Composable
private fun AppSection(ui: AppUpdateUi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val install by AppUpdate.install.collectAsState()
    val r = ui.result
    val release = r.release
    // Re-read the permission when the user comes back from the "Install unknown apps" screen.
    var allowed by remember { mutableStateOf(AppUpdate.canInstall(context)) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowed = AppUpdate.canInstall(context)
    }
    StatusCard(
        icon = when {
            r.available -> Icons.Outlined.NewReleases
            ui.failed && !ui.checked -> Icons.Outlined.CloudOff
            else -> Icons.Outlined.CheckCircle
        },
        busy = ui.checking && !ui.checked,
        highlight = r.available,
        title = when {
            r.available -> "Version ${release!!.version} is available"
            ui.failed && !ui.checked -> "Couldn't reach GitHub"
            !ui.checked -> "Checking GitHub…"
            release == null -> "No releases yet"
            else -> "Up to date"
        },
        detail = listOfNotNull(
            "Installed ${r.installed}",
            release?.version?.takeIf { !r.available }?.let { "latest $it" },
            release?.sizeBytes?.takeIf { r.available }?.let { "%.1f MB".format(it / 1_048_576f) },
        ).joinToString(" · "),
    ) {
        if (r.available && release != null) {
            Spacer(Modifier.height(Space.m))
            when (val i = install) {
                is AppUpdate.Install.Downloading -> {
                    Text(i.fraction?.let { "Downloading · ${(it * 100).toInt()}%" } ?: "Downloading…",
                        style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(Space.s))
                    val f = i.fraction
                    if (f != null) LinearWavyProgressIndicator(progress = { f }, Modifier.fillMaxWidth())
                    else LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                }
                AppUpdate.Install.Verifying, AppUpdate.Install.Installing -> {
                    Text(if (install == AppUpdate.Install.Verifying) "Checking the download…" else "Installing…",
                        style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(Space.s))
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                }
                else -> {
                    if (i is AppUpdate.Install.Failed) {
                        Text(i.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(Space.s))
                    }
                    if (allowed) {
                        Button(
                            onClick = { AppUpdate.dismissError(); scope.launch { AppUpdate.downloadAndInstall(context, release) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s))
                            Text(if (i is AppUpdate.Install.Failed) "Try again" else "Download and install")
                        }
                    } else {
                        Text("Android asks once before an app may install updates for itself.",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Space.s))
                        Button(onClick = { settings.launch(AppUpdate.unknownSourcesSettings(context)) }, Modifier.fillMaxWidth()) {
                            Text("Allow updates from Hush")
                        }
                    }
                }
            }
        }
    }
    release?.notes?.let { notes ->
        SectionHeader(if (r.available) "What's new in ${release.version}" else "In this version", top = Space.m)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(Space.m)) {
                Text(plainNotes(notes), style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { openUrl(context, release.pageUrl) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Release page"); Spacer(Modifier.width(Space.xs)); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

/** GitHub release notes are Markdown; the page shows them as plain text. */
private fun plainNotes(md: String) = md.lines().joinToString("\n") { line ->
    line.trimEnd()
        .replace(Regex("^#{1,6}\\s*"), "")
        .replace(Regex("^\\s*[-*]\\s+"), "• ")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
}.replace(Regex("\n{3,}"), "\n\n").trim()

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

