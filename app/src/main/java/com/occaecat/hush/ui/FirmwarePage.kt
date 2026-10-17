@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.update.FirmwareCheck
import kotlinx.coroutines.launch

/** State of Sony's firmware check for the connected headset; null fields until the headset reported its ids. */
class FirmwareUi {
    var result by mutableStateOf<FirmwareCheck.Result?>(null)
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

/** Checks once per connection (Sony's file is cached 12 h); [FirmwareUi] drives the About row and the page. */
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

fun firmwareSummary(ui: FirmwareUi, state: HeadphonesState): String? {
    val r = ui.result
    return when {
        r == null && ui.checking -> "Checking…"
        r == null && ui.failed -> "Couldn't check"
        r == null -> state.firmware?.let { "Version $it" }
        r.available -> "Version ${r.latest} available"
        else -> "Up to date · ${r.current}"
    }
}

@Composable
fun FirmwarePage(state: HeadphonesState, ui: FirmwareUi, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val r = ui.result
    FullScreenPage("Firmware update", onClose, bottomBar = if (r?.available == true) {
        {
            Button(onClick = { openSoundConnect(context) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Outlined.SystemUpdate, null); Spacer(Modifier.width(Space.s)); Text("Update in Sound Connect")
            }
        }
    } else null) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            color = if (r?.available == true) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
                if (ui.checking && r == null) LoadingIndicator(Modifier.size(56.dp))
                else ShapeBadge(if (r?.available == true) Icons.Outlined.NewReleases else Icons.Outlined.CheckCircle, size = 56.dp,
                    polygon = MaterialShapes.Cookie9Sided)
                Spacer(Modifier.width(Space.m))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        when {
                            r == null && ui.failed -> "Couldn't reach Sony"
                            r == null -> "Checking with Sony…"
                            r.available -> "Version ${r.latest} is available"
                            else -> "Up to date"
                        },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        listOfNotNull(
                            "Installed ${state.firmware ?: r?.current ?: "—"}",
                            r?.latest?.takeIf { !r.available }?.let { "latest $it" },
                            r?.sizeBytes?.takeIf { r.available }?.let { "%.1f MB".format(it / 1_048_576f) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    r?.published?.let { Text("Sony's list updated ${it.take(10)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        r?.changes?.let {
            SectionHeader(if (r.available) "What's new" else "In this version")
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Space.m))
            }
        }
        if (r?.available == true) Text(
            "Hush checks Sony's update server the way Sound Connect does, but installing firmware stays with Sound Connect: " +
                "it takes about 40 minutes over Bluetooth and must not be interrupted.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m),
        )
        val notice = r?.notice
        if (notice != null) {
            var open by remember { mutableStateOf(false) }
            SectionHeader("Notes from Sony")
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
        Spacer(Modifier.height(Space.m))
        var attempt by remember { mutableIntStateOf(0) }
        OutlinedButton(
            enabled = !ui.checking && state.fwInputs() != null,
            onClick = {
                val (category, service, model, fw) = state.fwInputs() ?: return@OutlinedButton
                attempt++
                scope.launch {
                    ui.checking = true; ui.failed = false
                    runCatching { FirmwareCheck.check(context, category, service, model, fw, state.serial, force = true) }
                        .onSuccess { ui.result = it }.onFailure { ui.failed = true }
                    ui.checking = false
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(if (ui.checking) "Checking…" else "Check again") }
        Spacer(Modifier.height(Space.l))
    }
}

private fun openSoundConnect(context: android.content.Context) {
    val pkg = "com.sony.songpal.mdr"
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
