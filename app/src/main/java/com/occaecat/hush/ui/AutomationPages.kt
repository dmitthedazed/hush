@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.scenes.Activity
import com.occaecat.hush.scenes.AppRule
import com.occaecat.hush.scenes.Place
import com.occaecat.hush.scenes.SceneAction
import com.occaecat.hush.scenes.SceneStore
import com.occaecat.hush.sound.SoundAutomation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

/** The Sound page's Automation group, for any device with noise control or an equalizer. */
@Composable
fun AutomationSection(key: String, c: DeviceController, modifier: Modifier, open: (String) -> Unit) {
    val config by SceneStore.config.collectAsState()
    val inputs by SoundAutomation.inputs.collectAsState()
    val d = SceneStore.device(config, key, c)
    Column(modifier) {
        SectionHeader("Automation", top = Space.s)
        SegmentedGroup {
            ValueRow(0, 2, Icons.AutoMirrored.Outlined.DirectionsWalk, "Adaptive Sound Control",
                if (!d.ascEnabled) "Off" else inputs.activity?.let { "On · ${it.label}" } ?: "On",
                modifier = Modifier.sharedPage("automation:asc")) { open("asc") }
            ValueRow(1, 2, Icons.Outlined.Apps, "App profiles",
                when {
                    !d.appsEnabled -> "Off"
                    d.apps.isEmpty() -> "On · no apps yet"
                    else -> "On · ${d.apps.size} app" + if (d.apps.size > 1) "s" else ""
                }, modifier = Modifier.sharedPage("automation:apps")) { open("apps") }
        }
    }
}

@Composable
private fun NoticeCard(icon: ImageVector, title: String, text: String, button: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(Space.s))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(Space.xs))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(Space.s))
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it.take(40) }, singleLine = true, label = { Text("Name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/** A switch card at the top of an automation page. */
@Composable
private fun HeaderSwitch(title: String, text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Space.m))
            Switch(on, onChange)
        }
    }
}

@Composable
private fun RuleRow(i: Int, n: Int, live: Boolean, lead: @Composable () -> Unit, title: String, value: String, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = i, count = n),
        colors = ListItemDefaults.colors(
            containerColor = if (live) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = lead,
        supportingContent = { Text(value) },
        content = { Text(title) },
    )
}

// ---------------------------------------------------------------------------------------------------------------
// Adaptive Sound Control
// ---------------------------------------------------------------------------------------------------------------

private val activityIcons = mapOf(
    Activity.STILL to Icons.Outlined.Weekend,
    Activity.WALKING to Icons.AutoMirrored.Filled.DirectionsWalk,
    Activity.RUNNING to Icons.AutoMirrored.Filled.DirectionsRun,
    Activity.VEHICLE to Icons.Outlined.DirectionsCar,
)

/** Per device: what each activity and each place does to it. Places themselves are shared by every device. */
@Composable
fun AdaptiveSoundPage(key: String, name: String, c: DeviceController, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by SceneStore.config.collectAsState()
    val inputs by SoundAutomation.inputs.collectAsState()
    val d = SceneStore.device(config, key, c)
    var refresh by remember { mutableIntStateOf(0) }
    val activityOk = rememberOnResume { SoundAutomation.hasActivityPermission(context) }.also { refresh }
    val locationOk = rememberOnResume { SoundAutomation.hasLocationPermission(context) }
    val backgroundOk = rememberOnResume { SoundAutomation.hasBackgroundLocation(context) }
    val askActivity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; SoundAutomation.refresh() }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++; SoundAutomation.refresh() }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; SoundAutomation.refresh() }
    var editing by remember { mutableStateOf<Activity?>(null) }
    var editingPlace by remember { mutableStateOf<Place?>(null) }
    var adding by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var locateError by remember { mutableStateOf<String?>(null) }

    FullScreenPage("Adaptive Sound Control", onClose) {
        HeaderSwitch("Adaptive Sound Control",
            "Switches $name's sound when you start walking, running or riding, and at your places.", d.ascEnabled) { on ->
            SceneStore.updateDevice(key, c) { it.copy(ascEnabled = on) }
            if (on && !activityOk) askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (d.ascEnabled) {
            val now = listOfNotNull(
                inputs.activity?.label,
                config.places.filter { it.id in inputs.inside }.joinToString { it.name }.ifEmpty { null },
            ).joinToString(" · ")
            Text(
                if (now.isEmpty()) "Waiting for the first activity update…" else "Now: $now → ${describe(SoundAutomation.resolve(d, inputs, config.places).first, c)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Space.s, top = Space.s),
            )
        }
        if (d.ascEnabled && !activityOk) {
            Spacer(Modifier.height(Space.m))
            NoticeCard(Icons.AutoMirrored.Filled.DirectionsWalk, "Physical activity access",
                "Needed to tell staying, walking, running and riding apart.", "Allow") {
                askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        SectionHeader("Activities")
        SegmentedGroup {
            Activity.entries.forEachIndexed { i, a ->
                RuleRow(i, Activity.entries.size, inputs.activity == a && d.ascEnabled, { LeadIcon(activityIcons.getValue(a)) },
                    a.label, describe(d.activities[a] ?: SceneAction(), c)) { editing = a }
            }
        }

        SectionHeader("My Places")
        if (config.places.isNotEmpty() && (!locationOk || !backgroundOk)) {
            NoticeCard(Icons.Outlined.Place, "Location all the time",
                "Places only switch the sound if Hush may use your location in the background. Android asks for it in Settings.",
                "Allow") {
                if (!locationOk) askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            Spacer(Modifier.height(Space.s))
        }
        if (config.places.isNotEmpty()) SegmentedGroup {
            config.places.forEachIndexed { i, p ->
                RuleRow(i, config.places.size, p.id in inputs.inside && d.ascEnabled, { LeadIcon(Icons.Outlined.Place) },
                    p.name, describe(d.places[p.id] ?: SceneAction(), c) + " · ${p.radius.roundToInt()} m") { editingPlace = p }
            }
        }
        Spacer(Modifier.height(Space.s))
        OutlinedButton(
            onClick = {
                if (!locationOk) askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else adding = true
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (locating) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(Space.s))
            Text("Add current location")
        }
        locateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Space.s)) }
        Text(
            "Places are shared by all your devices; what each one does there is set per device. Place settings win over " +
                "activity settings; an app profile wins over both.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Space.s),
        )
        Spacer(Modifier.height(Space.l))
    }

    editing?.let { a ->
        SceneSheet(a.label, c, d.activities[a] ?: SceneAction()) { result ->
            if (result != null) SceneStore.updateDevice(key, c) { it.copy(activities = it.activities + (a to result)) }
            editing = null
        }
    }
    editingPlace?.let { p ->
        SceneSheet(p.name, c, d.places[p.id] ?: SceneAction(), onDelete = {
            // A place belongs to every device: removing it here removes it everywhere.
            SceneStore.update { cfg ->
                cfg.copy(places = cfg.places.filterNot { it.id == p.id },
                    devices = cfg.devices.mapValues { (_, v) -> v.copy(places = v.places - p.id) })
            }
            editingPlace = null
        }) { result ->
            if (result != null) SceneStore.updateDevice(key, c) { it.copy(places = it.places + (p.id to result)) }
            editingPlace = null
        }
    }
    if (adding) NameDialog("Name this place", "Home", "Add") { placeName ->
        adding = false
        if (placeName == null) return@NameDialog
        locating = true
        locateError = null
        scope.launch {
            val loc = SoundAutomation.currentLocation(context)
            locating = false
            if (loc == null) {
                locateError = "Couldn't get your location. Turn on location and try again."
            } else {
                val place = Place(UUID.randomUUID().toString(), placeName.trim(), loc.latitude, loc.longitude, 150f)
                SceneStore.update { it.copy(places = it.places + place) }
                editingPlace = place
                if (!backgroundOk) askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// App profiles
// ---------------------------------------------------------------------------------------------------------------

private data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

private suspend fun launchableApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { ri ->
            AppInfo(ri.activityInfo.packageName, ri.loadLabel(pm).toString(),
                runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull())
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun AppIcon(context: Context, pkg: String) {
    val icon = remember(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (icon != null) Image(icon, null, Modifier.size(40.dp)) else LeadIcon(Icons.Outlined.Apps)
}

@Composable
fun AppProfilesPage(key: String, name: String, c: DeviceController, onClose: () -> Unit) {
    val context = LocalContext.current
    val config by SceneStore.config.collectAsState()
    val inputs by SoundAutomation.inputs.collectAsState()
    val d = SceneStore.device(config, key, c)
    val access = rememberOnResume { SoundAutomation.hasNotificationAccess(context) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppRule?>(null) }

    FullScreenPage("App profiles", onClose) {
        HeaderSwitch("App profiles", "While an app plays, $name uses its own sound. When it stops, the EQ goes back.", d.appsEnabled) { on ->
            SceneStore.updateDevice(key, c) { it.copy(appsEnabled = on) }
        }
        if (!access) {
            Spacer(Modifier.height(Space.m))
            NoticeCard(Icons.Outlined.Apps, "Notification access",
                "Android only shows which app is playing to apps with notification access. Hush reads media sessions only.",
                "Open settings") {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } else if (d.appsEnabled) {
            val playing = inputs.playing
            Text(
                playing?.let { p -> "Playing: " + (d.apps.firstOrNull { it.pkg == p }?.label ?: p) } ?: "Nothing is playing",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Space.s, top = Space.s),
            )
        }
        SectionHeader("Apps")
        if (d.apps.isEmpty()) {
            Text(
                "Add an app, for example a music app with a bright EQ and noise canceling, or a podcast app with a speech EQ.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s),
            )
        } else SegmentedGroup {
            d.apps.forEachIndexed { i, r ->
                RuleRow(i, d.apps.size, inputs.playing == r.pkg && d.appsEnabled, { AppIcon(context, r.pkg) },
                    r.label, describe(r.action, c)) { editing = r }
            }
        }
        Spacer(Modifier.height(Space.s))
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(Space.s)); Text("Add app")
        }
        Spacer(Modifier.height(Space.l))
    }

    if (picking) AppPickerSheet(exclude = d.apps.map { it.pkg }.toSet(), onDismiss = { picking = false }) { app ->
        picking = false
        val rule = AppRule(app.pkg, app.label, SceneAction())
        SceneStore.updateDevice(key, c) { it.copy(apps = it.apps + rule) }
        editing = rule
    }
    editing?.let { r ->
        SceneSheet(r.label, c, r.action, onDelete = {
            SceneStore.updateDevice(key, c) { dd -> dd.copy(apps = dd.apps.filterNot { it.pkg == r.pkg }) }
            editing = null
        }) { result ->
            if (result != null) SceneStore.updateDevice(key, c) { dd -> dd.copy(apps = dd.apps.map { if (it.pkg == r.pkg) it.copy(action = result) else it }) }
            editing = null
        }
    }
}

@Composable
private fun AppPickerSheet(exclude: Set<String>, onDismiss: () -> Unit, onPick: (AppInfo) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = launchableApps(context) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, shape = CircleShape,
                placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m),
            )
            Spacer(Modifier.height(Space.m))
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else {
                val shown = list.filter { it.pkg !in exclude && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }
                LazyColumn(Modifier.heightIn(max = 560.dp).padding(horizontal = Space.m), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    itemsIndexed(shown, key = { _, a -> a.pkg }) { i, a ->
                        SegmentedListItem(
                            onClick = { onPick(a) },
                            shapes = ListItemDefaults.segmentedShapes(index = i, count = shown.size),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            leadingContent = { a.icon?.let { Image(it, null, Modifier.size(40.dp)) } ?: LeadIcon(Icons.Outlined.Apps) },
                            content = { Text(a.label) },
                        )
                    }
                    item { Spacer(Modifier.height(Space.m)) }
                }
            }
        }
    }
}
