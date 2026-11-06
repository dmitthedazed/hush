@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.Manifest
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.ui.graphics.Color
import com.occaecat.hush.DeviceModules
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.features.Insights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

private fun hours(h: Double) = when {
    h >= 10 -> "${h.roundToInt()} h"
    h >= 1 -> "%.1f h".format(h)
    else -> "${(h * 60).roundToInt()} min"
}

/** Every registered device Insights knows, one per Insights key: the current one first. */
private data class Tracked(val key: String, val profile: Insights.Profile, val name: String, val connected: Boolean, val controller: DeviceController)

@Composable
private fun trackedDevices(currentKey: String?): List<Tracked> {
    val devices by DeviceModules.devices.collectAsState()
    val list = devices.mapNotNull { d ->
        key(d.key) {
            val c = DeviceModules.controller(d)
            val s by c.state.collectAsState()
            c.insights?.let { Tracked(it.key, it, d.name, s.connected, c) }
        }
    }
    return list.groupBy { it.key }.values.map { same -> same.firstOrNull { it.connected } ?: same.first() }
        .sortedByDescending { it.key == currentKey }
}

/** The Insights rows on the Device tab: this device's battery, the listener's hearing, and where every device is. */
@Composable
fun InsightsSection(profile: Insights.Profile, live: Insights.Sample, modifier: Modifier, open: (String) -> Unit) {
    val all by Insights.all.collectAsState()
    val data = all[profile.key] ?: Insights.Snapshot()
    val connected = live.connected
    val level = live.battery?.takeIf { connected }
    val nc = live.nc
    val left = level?.let { it / Insights.drain(profile, nc, data.battery).percentPerHour }
    val dose = Insights.weeklyDose(all)
    val mine = Insights.weeklyDose(data.days)
    val tracked = trackedDevices(profile.key)
    val seen = data.lastSeen
    Column(modifier) {
        SectionHeader("Insights", top = Space.s)
        SegmentedGroup {
            val b = if (profile.hasBattery) 1 else 0
            val n = 2 + b
            if (profile.hasBattery) ValueRow(0, n, Icons.Outlined.BatteryChargingFull, "Battery",
                when {
                    level != null && live.charging -> "$level% · charging"
                    level != null && left != null -> "$level% · about ${hours(left)} left"
                    else -> data.battery.lastOrNull()?.let { "Last seen at ${it.level}%" } ?: "No data yet"
                }, modifier = Modifier.sharedPage("insight:battery")) { open("battery") }
            ValueRow(b, n, Icons.Outlined.Hearing, "Hearing",
                when {
                    all.values.all { it.days.isEmpty() } -> "No listening recorded yet"
                    tracked.size > 1 && mine > 0 && mine < dose -> "This week ${pct(dose)} of the safe limit · ${pct(mine)} on these"
                    else -> "This week ${pct(dose)} of the safe limit"
                }, modifier = Modifier.sharedPage("insight:hearing")) { open("hearing") }
            ValueRow(b + 1, n, Icons.Outlined.LocationOn, if (tracked.size > 1) "Find my devices" else "Find my ${profile.noun}",
                when {
                    tracked.size > 1 -> "${tracked.count { it.connected }} of ${tracked.size} connected"
                    connected -> "Connected now"
                    seen != null -> "Last connected " + ago(seen.t)
                    else -> "Not seen yet"
                }, modifier = Modifier.sharedPage("insight:find")) { open("find") }
        }
    }
}

private fun ago(t: Long): CharSequence =
    if (System.currentTimeMillis() - t < 60_000) "just now" else DateUtils.getRelativeTimeSpanString(t)

private fun pct(dose: Double) = "${(dose * 100).roundToInt()}%"

@Composable
fun InsightsPage(which: String, profile: Insights.Profile, live: Insights.Sample, onClose: () -> Unit) = when (which) {
    "battery" -> BatteryPage(profile, live, onClose)
    "hearing" -> HearingPage(profile.key, onClose)
    else -> FindPage(profile.key, onClose)
}

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun BatteryPage(profile: Insights.Profile, live: Insights.Sample, onClose: () -> Unit) {
    val all by Insights.all.collectAsState()
    val data = all[profile.key] ?: Insights.Snapshot()
    val connected = live.connected
    val nc = live.nc
    val drainNc = Insights.drain(profile, true, data.battery)
    val drainOff = Insights.drain(profile, false, data.battery)
    val level = live.battery?.takeIf { connected } ?: data.battery.lastOrNull()?.level
    FullScreenPage("Battery", onClose) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Space.l)) {
                Text(level?.let { "$it%" } ?: "—", style = MaterialTheme.typography.displayMedium)
                val d = if (nc) drainNc else drainOff
                Text(
                    when {
                        live.charging && connected -> "Charging"
                        level == null -> "Connect the ${profile.noun} to start measuring"
                        else -> "About ${hours(level / d.percentPerHour)} left with noise canceling ${if (nc) "on" else "off"}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (d.measured) "Measured over ${hours(d.hoursMeasured)} of use" else "${profile.ratedBy} until Hush has measured 2 hours (${hours(d.hoursMeasured)} so far)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SectionHeader("Last 48 hours")
        BatteryChart(data.battery, Modifier.fillMaxWidth().height(160.dp))
        SectionHeader("Drain")
        SegmentedGroup {
            InfoRow(0, 2, Icons.Outlined.BatteryChargingFull, "Noise canceling on",
                "%.1f%% an hour · a full charge ≈ %s".format(drainNc.percentPerHour, hours(100 / drainNc.percentPerHour)) + if (drainNc.measured) "" else " (rated)")
            InfoRow(1, 2, Icons.Outlined.BatteryChargingFull, "Ambient or off",
                "%.1f%% an hour · a full charge ≈ %s".format(drainOff.percentPerHour, hours(100 / drainOff.percentPerHour)) + if (drainOff.measured) "" else " (rated)")
        }
        Text(
            "The battery is the headphones' own, so listening on a laptop or another phone drains it too. Hush measures " +
                "only while this phone stays connected: with “Connect to 2 devices” it keeps seeing the drain while the " +
                "laptop plays; time spent with another device alone is skipped instead of being counted as idle.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m),
        )
    }
}

/** Level over the last 48 h; a dashed line bridges the gaps when the phone wasn't connected. */
@Composable
private fun BatteryChart(samples: List<Insights.BatterySample>, modifier: Modifier) {
    val now = System.currentTimeMillis()
    val from = now - 48 * 3_600_000L
    val points = samples.filter { it.t >= from }
    val line = MaterialTheme.colorScheme.primary
    val gap = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        if (points.size < 2) {
            Box(contentAlignment = Alignment.Center) {
                Text("The chart fills in as you use the headphones", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.m))
            }
            return@Surface
        }
        Canvas(Modifier.padding(Space.m)) {
            fun x(t: Long) = ((t - from).toFloat() / (now - from)) * size.width
            fun y(l: Int) = size.height * (1 - l / 100f)
            listOf(0, 50, 100).forEach { drawLine(grid, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx()) }
            points.zipWithNext().forEach { (a, b) ->
                val same = a.session == b.session
                drawLine(
                    if (same) line else gap, Offset(x(a.t), y(a.level)), Offset(x(b.t), y(b.level)),
                    strokeWidth = if (same) 3.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (same) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------

/** Series colours for the devices on the Hearing page, in [trackedDevices] order. */
@Composable
private fun seriesColors() = MaterialTheme.colorScheme.let { listOf(it.primary, it.tertiary, it.secondary, it.error, it.outline) }

/**
 * The listener's ears, whatever they listened on: one dose for the week, merged from every device (each minute is
 * counted on one device only), with each device's share. Removed devices still count; their history is kept.
 */
@Composable
private fun HearingPage(currentKey: String, onClose: () -> Unit) {
    val all by Insights.all.collectAsState()
    val tracked = trackedDevices(currentKey)
    val keys = (tracked.map { it.key } + all.filterValues { it.days.isNotEmpty() }.keys).distinct()
    val names = keys.associateWith { k -> tracked.firstOrNull { it.key == k }?.name ?: "Removed device" }
    val palette = seriesColors()
    val color = keys.withIndex().associate { (i, k) -> k to palette[i.coerceAtMost(palette.lastIndex)] }
    val dose = Insights.weeklyDose(all)
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    fun day(k: String, d: LocalDate) = all[k]?.days?.firstOrNull { it.date == d }
    FullScreenPage("Hearing", onClose) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(progress = { dose.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.size(88.dp),
                        color = if (dose >= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(pct(dose), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(Space.l))
                Column {
                    Text(
                        when {
                            dose < 0.5 -> "Well within the safe limit"
                            dose < 1 -> "Getting close to the limit"
                            else -> "Over the weekly limit"
                        },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(if (keys.size > 1) "Sound dose of the last 7 days, all devices" else "Sound dose of the last 7 days",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SectionHeader("This week")
        Surface(Modifier.fillMaxWidth().height(170.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.Bottom) {
                val max = week.maxOf { d -> keys.sumOf { day(it, d)?.dose ?: 0.0 } }.coerceAtLeast(1.0 / 7)
                week.forEach { d ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        val parts = keys.mapNotNull { k -> day(k, d)?.dose?.takeIf { it > 0 }?.let { k to it } }
                        val total = parts.sumOf { it.second }
                        val f = (total / max).toFloat().coerceIn(0.02f, 1f) * 0.85f
                        // One bar a day, stacked by device; an empty day is a stub in the neutral colour.
                        Column(Modifier.fillMaxWidth().fillMaxHeight(f).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)) {
                            parts.asReversed().forEach { (k, v) ->
                                Box(Modifier.fillMaxWidth().weight(v.toFloat()).background(color.getValue(k).copy(alpha = if (d == today) 1f else 0.7f)))
                            }
                        }
                        Spacer(Modifier.height(Space.xs))
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, LocalLocale.current.platformLocale),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (keys.isNotEmpty()) {
            SectionHeader(if (keys.size > 1) "By device" else "Listening")
            SegmentedGroup {
                keys.forEachIndexed { i, k ->
                    val days = all[k]?.days.orEmpty().filter { !it.date.isBefore(week.first()) }
                    val minutes = days.sumOf { it.minutes }
                    val profile = Insights.profile(k) ?: tracked.firstOrNull { it.key == k }?.profile
                    val avg = days.sumOf { it.volumeSum }.takeIf { minutes > 0 }?.let { (it.toDouble() / minutes).roundToInt() }
                    val steps = tracked.firstOrNull { it.key == k }?.controller?.state?.value?.volumeSteps?.coerceAtLeast(2) ?: 31
                    DeviceShareRow(i, keys.size, color.getValue(k), names.getValue(k),
                        if (minutes == 0) "Nothing this week"
                        else listOfNotNull(
                            hours(minutes / 60.0),
                            "${pct(Insights.weeklyDose(days))} of the limit",
                            if (avg != null && profile != null) "≈ ${Insights.estimatedDb(profile, avg, steps).roundToInt()} dB" else null,
                        ).joinToString(" · "))
                }
            }
        }
        val todayMinutes = keys.sumOf { day(it, today)?.minutes ?: 0 }
        SectionHeader("Today")
        SegmentedGroup {
            InfoRow(0, 1, Icons.Outlined.Hearing, "Listening time", if (todayMinutes > 0) hours(todayMinutes / 60.0) else "None yet")
        }
        Text(
            "WHO's guideline is 80 dB for 40 hours a week; every 3 dB louder halves the safe time. Hush estimates the level " +
                "from each device's volume while music plays and adds the devices up, since it's the same ears; a minute " +
                "goes to the device that was playing, never to two. Treat it as a guide, not a measurement.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m),
        )
    }
}

@Composable
private fun DeviceShareRow(index: Int, count: Int, color: Color, title: String, value: String) {
    SegmentedListItem(
        onClick = {}, enabled = false,
        shapes = segShapes(index, count),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
            disabledSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        leadingContent = { Box(Modifier.size(12.dp).clip(CircleShape).background(color)) },
        supportingContent = { Text(value) },
    ) { Text(title) }
}

// ---------------------------------------------------------------------------------------------------------------

/** Where every device was last together with this phone, on one page. */
@Composable
private fun FindPage(currentKey: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val all by Insights.all.collectAsState()
    val tracked = trackedDevices(currentKey)
    var permTick by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val hasLoc = remember(permTick) { Insights.hasLocation(context) }
    val hasBg = remember(permTick) { Insights.hasBackgroundLocation(context) }
    val single = tracked.singleOrNull()
    FullScreenPage(single?.let { "Find my ${it.profile.noun}" } ?: "Find my devices", onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            tracked.forEach { t -> FindCard(t, all[t.key]?.lastSeen) }
        }
        SectionHeader("How it works")
        Text(
            "When a device disconnects from this phone (or is unplugged), Hush notes the time and, if allowed, where the " +
                "phone was. It's the last place they were together — usually where it was left.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s),
        )
        if (!hasLoc || !hasBg) {
            Spacer(Modifier.height(Space.m))
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(Space.m)) {
                    Text(if (!hasLoc) "Location is off for Hush" else "Allow location all the time",
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (!hasLoc) "Without it Hush can only remember when, not where."
                        else "Disconnects usually happen with the screen off; Android only shares the location then with “Allow all the time”.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Space.s))
                    Button(onClick = {
                        if (!hasLoc) ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text(if (!hasLoc) "Allow location" else "Open settings") }
                }
            }
        }
    }
}

@Composable
private fun FindCard(t: Tracked, seen: Insights.LastSeen?) {
    val context = LocalContext.current
    val connected = t.connected
    val address by produceState<String?>(null, seen?.lat, seen?.lng) {
        val lat = seen?.lat ?: return@produceState
        val lng = seen.lng ?: return@produceState
        value = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { Geocoder(context).getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0) }.getOrNull()
        }
    }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                t.controller.Render(Modifier.size(56.dp), connected)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        when {
                            connected -> "Connected to this phone"
                            seen != null -> "Last connected " + ago(seen.t)
                            else -> "Hush hasn't seen it disconnect yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (!connected && seen != null) {
                Spacer(Modifier.height(Space.s))
                Text(DateUtils.formatDateTime(context, seen.t, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY),
                    style = MaterialTheme.typography.bodySmall)
                Text(
                    address ?: seen.lat?.let { "%.5f, %.5f".format(seen.lat, seen.lng) } ?: "No location was available at the time",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                seen.accuracy?.let { Text("Within about ${it.roundToInt()} m", style = MaterialTheme.typography.bodySmall) }
                if (seen.lat != null) {
                    Spacer(Modifier.height(Space.s))
                    FilledTonalButton(onClick = {
                        val label = Uri.encode(t.name)
                        val uri = Uri.parse("geo:${seen.lat},${seen.lng}?q=${seen.lat},${seen.lng}($label)")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Open in Maps") }
                }
            }
        }
    }
}
