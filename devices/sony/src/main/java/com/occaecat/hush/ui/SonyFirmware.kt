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

private fun HeadphonesState.fwInputs(): List<String>? {
    val category = updateInfo[2] ?: return null
    val service = updateInfo[3] ?: return null
    val model = modelName ?: return null
    val fw = firmware ?: return null
    return listOf(category, service, model, fw)
}

private suspend fun FirmwareUi.check(context: android.content.Context, state: HeadphonesState) {
    val (category, service, model, fw) = state.fwInputs() ?: return
    checking = true; failed = false
    runCatching { FirmwareCheck.check(context, category, service, model, fw, state.serial, force = true) }
        .onSuccess { result = it }.onFailure { failed = true }
    checking = false
}

/** Sony's firmware check for the connected headset, as the shared Updates page's device section. */
class SonyFirmware(private val controller: com.occaecat.hush.HeadphonesController) : com.occaecat.hush.devices.Firmware {
    private val ui = FirmwareUi()

    @Composable
    override fun pending(): String? {
        val state by controller.state.collectAsState()
        val context = LocalContext.current
        val inputs = state.fwInputs()
        LaunchedEffect(inputs) {
            val (category, service, model, fw) = inputs ?: return@LaunchedEffect
            ui.result = FirmwareCheck.cached(context, service)?.takeIf { it.current == fw }
            ui.checking = true; ui.failed = false
            runCatching { FirmwareCheck.check(context, category, service, model, fw, state.serial) }
                .onSuccess { ui.result = it }.onFailure { ui.failed = ui.result == null }
            ui.checking = false
        }
        return ui.result?.takeIf { it.available }?.latest?.let { "firmware $it" }
    }

    @Composable
    override fun checking() = ui.checking

    @Composable
    override fun Section() {
        val state by controller.state.collectAsState()
        FirmwareSection(state, ui)
    }

    override suspend fun recheck(context: android.content.Context) = ui.check(context, controller.state.value)
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

private fun openSoundConnect(context: android.content.Context) {
    val pkg = "com.sony.songpal.mdr"
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
