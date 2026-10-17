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
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.update.AppUpdate
import com.occaecat.hush.update.FirmwareCheck
import kotlinx.coroutines.launch

/** State of Sony's firmware check for the connected headset; null fields until the headset reported its ids. */
class FirmwareUi {
    var result by mutableStateOf<FirmwareCheck.Result?>(null)
    var checking by mutableStateOf(false)
    var failed by mutableStateOf(false)
}

/** State of the GitHub release check for Hush itself. */
class AppUpdateUi(installed: String) {
    var result by mutableStateOf(AppUpdate.Result(installed, null))
    var checked by mutableStateOf(false)
    var checking by mutableStateOf(false)
    var failed by mutableStateOf(false)
}

private fun HeadphonesState.fwInputs(): List<String>? {
    val category = updateInfo[2] ?: return null
    val service = updateInfo[3] ?: return null
    val model = modelName ?: return null
    val fw = firmware ?: return null
    return listOf(category, service, model, fw)
}

/** Checks once per connection (Sony's file is cached 12 h); [FirmwareUi] drives the Updates row and page. */
@Composable
fun rememberFirmwareCheck(state: HeadphonesState): FirmwareUi {
    val context = LocalContext.current
    val ui = remember { FirmwareUi() }
    val inputs = state.fwInputs()
    LaunchedEffect(inputs) {
        val (category, service, model, fw) = inputs ?: return@LaunchedEffect
        ui.result = FirmwareCheck.cached(context, service)?.takeIf { it.current == fw }
        ui.checking = true; ui.failed = false
        runCatching { FirmwareCheck.check(context, category, service, model, fw, state.serial) }
            .onSuccess { ui.result = it }.onFailure { ui.failed = ui.result == null }
        ui.checking = false
    }
    return ui
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

private suspend fun AppUpdateUi.check(context: android.content.Context, force: Boolean) {
    checking = true; failed = false
    runCatching { AppUpdate.check(context, force) }
        .onSuccess { result = it; checked = true }.onFailure { failed = true }
    checking = false
}

private suspend fun FirmwareUi.check(context: android.content.Context, state: HeadphonesState) {
    val (category, service, model, fw) = state.fwInputs() ?: return
    checking = true; failed = false
    runCatching { FirmwareCheck.check(context, category, service, model, fw, state.serial, force = true) }
        .onSuccess { result = it }.onFailure { failed = true }
    checking = false
}

/** One line for the Updates row: what can be updated, otherwise that everything is current. */
fun updatesSummary(app: AppUpdateUi, fw: FirmwareUi): String {
    val pending = listOfNotNull(
        app.result.release?.version?.takeIf { app.result.available }?.let { "Hush $it" },
        fw.result?.takeIf { it.available }?.latest?.let { "firmware $it" },
    )
    return when {
        pending.isNotEmpty() -> pending.joinToString(" and ").replaceFirstChar { it.uppercase() } + " available"
        app.checking || fw.checking -> "Checking…"
        app.failed && !app.checked -> "Couldn't check"
        else -> "Up to date · Hush ${app.result.installed}"
    }
}

/** Hush from GitHub and the headphones' firmware from Sony, on one page. */
@Composable
fun UpdatesPage(state: HeadphonesState, app: AppUpdateUi, fw: FirmwareUi, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FullScreenPage("Updates", onClose) {
        SectionHeader("Hush", top = Space.s)
        AppSection(app)
        SectionHeader("Headphones")
        FirmwareSection(state, fw)
        Spacer(Modifier.height(Space.l))
        OutlinedButton(
            enabled = !app.checking && !fw.checking,
            onClick = {
                scope.launch { app.check(context, force = true) }
                scope.launch { fw.check(context, state) }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(if (app.checking || fw.checking) "Checking…" else "Check again") }
        Spacer(Modifier.height(Space.l))
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    busy: Boolean,
    highlight: Boolean,
    title: String,
    detail: String?,
    content: @Composable () -> Unit = {},
) {
    Surface(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) LoadingIndicator(Modifier.size(48.dp))
                else ShapeBadge(icon, size = 48.dp, polygon = MaterialShapes.Cookie9Sided)
                Spacer(Modifier.width(Space.m))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            content()
        }
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
                    if (i.fraction != null) LinearWavyProgressIndicator(progress = { i.fraction }, Modifier.fillMaxWidth())
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

@Composable
private fun FirmwareSection(state: HeadphonesState, ui: FirmwareUi) {
    val context = LocalContext.current
    val r = ui.result
    val known = state.fwInputs() != null
    StatusCard(
        icon = when {
            !known && r == null -> Icons.Outlined.Headphones
            r?.available == true -> Icons.Outlined.NewReleases
            r == null && ui.failed -> Icons.Outlined.CloudOff
            else -> Icons.Outlined.CheckCircle
        },
        busy = ui.checking && r == null,
        highlight = r?.available == true,
        title = when {
            r == null && !known -> "Connect the headphones to check"
            r == null && ui.failed -> "Couldn't reach Sony"
            r == null -> "Checking with Sony…"
            r.available -> "Firmware ${r.latest} is available"
            else -> "Firmware is up to date"
        },
        detail = listOfNotNull(
            (state.firmware ?: r?.current)?.let { "Installed $it" },
            r?.latest?.takeIf { !r.available }?.let { "latest $it" },
            r?.sizeBytes?.takeIf { r.available }?.let { "%.1f MB".format(it / 1_048_576f) },
        ).joinToString(" · ").ifEmpty { null },
    ) {
        if (r?.available == true) {
            Spacer(Modifier.height(Space.m))
            Text(
                "Installing firmware stays with Sound Connect: it takes about 40 minutes over Bluetooth and must not be interrupted.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Space.s))
            FilledTonalButton(onClick = { openSoundConnect(context) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Update in Sound Connect")
            }
        }
    }
    r?.changes?.let {
        SectionHeader(if (r.available) "What's new in ${r.latest}" else "In this version", top = Space.m)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.m))
        }
    }
    val notice = r?.notice
    if (notice != null) {
        var open by remember { mutableStateOf(false) }
        SectionHeader("Notes from Sony", top = Space.m)
        Surface(
            onClick = { open = !open }, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                if (open) notice else notice.substringBefore("YOU SHOULD").substringBefore("\n\n\n").take(600).trim() + "…",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Space.m),
            )
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

private fun openSoundConnect(context: android.content.Context) {
    val pkg = "com.sony.songpal.mdr"
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
