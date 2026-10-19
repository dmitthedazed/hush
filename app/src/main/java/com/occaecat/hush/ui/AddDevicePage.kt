@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.occaecat.hush.DeviceModules
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.DeviceModule
import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.Model
import com.occaecat.hush.devices.Transport

/**
 * Adding a device: what the modules found (paired headsets, plugged-in accessories) first, then by hand, brand →
 * model → how to get it ready. With [onClose] null it is the first screen (nothing registered yet).
 */
@Composable
fun AddDeviceScreen(onClose: (() -> Unit)?, onAdd: (Candidate) -> Unit) {
    val context = LocalContext.current
    var brand by rememberSaveable { mutableStateOf<String?>(null) }
    var modelId by rememberSaveable { mutableStateOf<String?>(null) }
    val module = brand?.let(DeviceModules::module)
    val model = module?.models?.firstOrNull { it.id == modelId }
    LifecycleResumeEffect(Unit) {
        DeviceModules.scan(context)
        onPauseOrDispose {}
    }
    val back: () -> Unit = { if (modelId != null) modelId = null else if (brand != null) brand = null else onClose?.invoke() }
    BackHandler(enabled = brand != null) { back() }
    val title = model?.name ?: module?.brand ?: "Add a device"
    val content: @Composable ColumnScope.() -> Unit = {
        when {
            model != null && module != null -> ModelPage(module, model, onAdd)
            module != null -> BrandPage(module) { modelId = it }
            else -> StartPage(onAdd) { brand = it }
        }
    }
    if (onClose != null) FullScreenPage(title, onClose = back, content = content)
    else Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState()).padding(horizontal = Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.l, bottom = Space.m)) {
            if (brand != null) IconButton(onClick = back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = Space.s))
        }
        content()
    }
}

@Composable
private fun ColumnScope.StartPage(onAdd: (Candidate) -> Unit, onBrand: (String) -> Unit) {
    val context = LocalContext.current
    val found by DeviceModules.found.collectAsState()
    Text(
        "Hush finds headphones that are paired with this phone or plugged in. Pick one to add it; added devices stay " +
            "in the device switcher even while they're off.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.s),
    )
    PermissionRow(DeviceModules.all.flatMap { it.models }.mapNotNull { it.permission }.distinct())
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader("Found", Modifier.weight(1f))
        IconButton(onClick = { DeviceModules.scan(context) }, modifier = Modifier.padding(top = Space.m)) {
            Icon(Icons.Outlined.Refresh, "Look again")
        }
    }
    if (found.isEmpty()) {
        Text(
            "Nothing new yet. Pair headphones in Bluetooth settings or plug in wired ones, and they show up here.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s),
        )
        TextButton(onClick = { openBluetoothSettings(context) }) { Text("Bluetooth settings") }
    } else SegmentedGroup {
        found.forEachIndexed { i, c -> CandidateRow(i, found.size, c, added = false) { onAdd(c) } }
    }
    SectionHeader("Add by brand")
    SegmentedGroup {
        val brands = DeviceModules.all.filter { it.models.isNotEmpty() }
        brands.forEachIndexed { i, m ->
            ValueRow(i, brands.size, Icons.Outlined.Headphones, m.brand,
                m.models.joinToString(", ") { it.name }) { onBrand(m.id) }
        }
    }
    Spacer(Modifier.height(Space.l))
}

@Composable
private fun ColumnScope.BrandPage(module: DeviceModule, onModel: (String) -> Unit) {
    SegmentedGroup(Modifier.padding(top = Space.s)) {
        module.models.forEachIndexed { i, m ->
            ValueRow(i, module.models.size, if (m.transport == Transport.USB) Icons.Outlined.Usb else Icons.Outlined.Bluetooth,
                m.name, m.note) { onModel(m.id) }
        }
    }
}

@Composable
private fun ColumnScope.ModelPage(module: DeviceModule, model: Model, onAdd: (Candidate) -> Unit) {
    val context = LocalContext.current
    val all by module.candidates.collectAsState()
    val registered by DeviceRegistry.items.collectAsState()
    val units = all.filter { it.modelId == model.id }
    model.image?.let {
        Image(painterResource(it), model.name, Modifier.fillMaxWidth().height(180.dp).padding(vertical = Space.m), contentScale = ContentScale.Fit)
    }
    Text(model.howTo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Space.s, vertical = Space.s))
    PermissionRow(listOfNotNull(model.permission))
    if (units.isNotEmpty() || !model.addWithoutFinding) {
        SectionHeader(if (model.transport == Transport.USB) "Plugged in" else "Paired with this phone")
        if (units.isEmpty()) Text(
            "None yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s),
        )
        else SegmentedGroup {
            units.forEachIndexed { i, c -> CandidateRow(i, units.size, c, added = registered.any { it.key == c.key }) { onAdd(c) } }
        }
    }
    Spacer(Modifier.height(Space.m))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        if (model.transport == Transport.BLUETOOTH) OutlinedButton(onClick = { openBluetoothSettings(context) }) {
            Icon(Icons.Outlined.BluetoothSearching, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Bluetooth settings")
        }
        if (model.addWithoutFinding) {
            val c = Candidate(module.id, model.id, model.name, model.transport)
            val added = registered.any { it.key == c.key }
            Button(onClick = { onAdd(c) }, enabled = !added) { Text(if (added) "Added" else "Add ${model.name}") }
        }
    }
    Spacer(Modifier.height(Space.l))
}

@Composable
private fun CandidateRow(index: Int, count: Int, c: Candidate, added: Boolean, onAdd: () -> Unit) {
    val module = DeviceModules.module(c.moduleId)
    val modelName = module.models.firstOrNull { it.id == c.modelId }?.name
    SegmentedListItem(
        onClick = onAdd,
        enabled = !added,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { LeadIcon(if (c.transport == Transport.USB) Icons.Outlined.Usb else Icons.Outlined.Bluetooth, !added) },
        supportingContent = { Text(listOfNotNull(module.brand, modelName?.takeIf { it != c.name }).joinToString(" · ")) },
        trailingContent = { if (added) Text("Added") else FilledTonalButton(onClick = onAdd) { Text("Add") } },
        content = { Text(c.name) },
    )
}

/** Asks for what the modules need to find devices (Bluetooth), while it's missing. */
@Composable
private fun PermissionRow(permissions: List<String>) {
    val context = LocalContext.current
    var tick by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        DeviceModules.scan(context)
    }
    val missing = androidx.compose.runtime.remember(tick, permissions) {
        permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }
    if (missing.isEmpty()) return
    SegmentedGroup(Modifier.padding(top = Space.m)) {
        ValueRow(0, 1, Icons.Outlined.Bluetooth, "Allow nearby devices", "Needed to see headphones paired with this phone") {
            launcher.launch(missing.toTypedArray())
        }
    }
}

private fun openBluetoothSettings(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
}

/** A device a module just found that isn't registered: offer to add it, once. */
@Composable
fun FoundDevicePrompt(c: Candidate, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val module = DeviceModules.module(c.moduleId)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.l).padding(bottom = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShapeBadge(if (c.transport == Transport.USB) Icons.Outlined.Usb else Icons.Outlined.Bluetooth, size = 64.dp)
            Spacer(Modifier.height(Space.m))
            Text("New device found", style = MaterialTheme.typography.headlineSmallEmphasized)
            Spacer(Modifier.height(Space.xs))
            Text(
                "${c.name} (${module.brand}) is ${if (c.transport == Transport.USB) "plugged in" else "paired with this phone"}. " +
                    "Add it to Hush?",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(onClick = onDismiss) { Text("Not now") }
                Button(onClick = onAdd) { Text("Add") }
            }
        }
    }
}
