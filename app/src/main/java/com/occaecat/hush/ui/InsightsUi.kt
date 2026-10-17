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
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.features.Insights
import com.occaecat.hush.mdr.AmbientMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private fun hours(h: Double) = when {
    h >= 10 -> "${h.roundToInt()} h"
    h >= 1 -> "%.1f h".format(h)
    else -> "${(h * 60).roundToInt()} min"
}

/** The three Insights rows on the Device tab. */
@Composable
fun InsightsSection(state: HeadphonesState, modifier: Modifier, open: (String) -> Unit) {
    val data by Insights.data.collectAsState()
    val connected = state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING
    val level = state.battery?.takeIf { connected }
    val nc = state.ncAsm?.mode == AmbientMode.NOISE_CANCELLING
    val left = level?.let { it / Insights.drain(nc, data.battery).percentPerHour }
    val dose = Insights.weeklyDose(data.days)
    val seen = data.lastSeen
    Column(modifier) {
        SectionHeader("Insights")
        SegmentedGroup {
            ValueRow(0, 3, Icons.Outlined.BatteryChargingFull, "Battery",
                when {
                    level != null && state.charging -> "$level% · charging"
                    level != null && left != null -> "$level% · about ${hours(left)} left"
                    else -> data.battery.lastOrNull()?.let { "Last seen at ${it.level}%" } ?: "No data yet"
                }) { open("battery") }
            ValueRow(1, 3, Icons.Outlined.Hearing, "Hearing",
                if (data.days.isEmpty()) "No listening recorded yet" else "This week ${(dose * 100).roundToInt()}% of the safe limit") { open("hearing") }
            ValueRow(2, 3, Icons.Outlined.LocationOn, "Find my headphones",
                when {
                    connected -> "Connected now"
                    seen != null -> "Last connected " + DateUtils.getRelativeTimeSpanString(seen.t)
                    else -> "Not seen yet"
                }) { open("find") }
        }
    }
}

@Composable
fun InsightsPage(which: String, state: HeadphonesState, onClose: () -> Unit) = when (which) {
    "battery" -> BatteryPage(state, onClose)
    "hearing" -> HearingPage(state, onClose)
    else -> FindPage(state, onClose)
}

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun BatteryPage(state: HeadphonesState, onClose: () -> Unit) {
    val data by Insights.data.collectAsState()
    val connected = state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING
    val nc = state.ncAsm?.mode == AmbientMode.NOISE_CANCELLING
    val drainNc = Insights.drain(true, data.battery)
    val drainOff = Insights.drain(false, data.battery)
    val level = state.battery?.takeIf { connected } ?: data.battery.lastOrNull()?.level
    FullScreenPage("Battery", onClose) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Space.l)) {
                Text(level?.let { "$it%" } ?: "—", style = MaterialTheme.typography.displayMedium)
                val d = if (nc) drainNc else drainOff
                Text(
                    when {
                        state.charging && connected -> "Charging"
                        level == null -> "Connect the headphones to start measuring"
                        else -> "About ${hours(level / d.percentPerHour)} left with noise canceling ${if (nc) "on" else "off"}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (d.measured) "Measured over ${hours(d.hoursMeasured)} of use" else "Sony's rating until Hush has measured 2 hours (${hours(d.hoursMeasured)} so far)",
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

@Composable
private fun HearingPage(state: HeadphonesState, onClose: () -> Unit) {
    val data by Insights.data.collectAsState()
    val dose = Insights.weeklyDose(data.days)
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { d -> d to data.days.firstOrNull { it.date == d } }
    FullScreenPage("Hearing", onClose) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(progress = { dose.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.size(88.dp),
                        color = if (dose >= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text("${(dose * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
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
                    Text("Sound dose of the last 7 days", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SectionHeader("This week")
        Surface(Modifier.fillMaxWidth().height(170.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.Bottom) {
                val max = (week.maxOfOrNull { it.second?.dose ?: 0.0 } ?: 0.0).coerceAtLeast(1.0 / 7)
                week.forEach { (d, day) ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        val f = ((day?.dose ?: 0.0) / max).toFloat().coerceIn(0.02f, 1f)
                        Box(Modifier.fillMaxWidth().fillMaxHeight(f * 0.85f).clip(RoundedCornerShape(8.dp))
                            .background(if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer))
                        Spacer(Modifier.height(Space.xs))
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        val t = data.days.firstOrNull { it.date == today }
        SectionHeader("Today")
        SegmentedGroup {
            InfoRow(0, 2, Icons.Outlined.Hearing, "Listening time", t?.let { hours(it.minutes / 60.0) } ?: "None yet")
            InfoRow(1, 2, Icons.Outlined.Hearing, "Average level",
                t?.takeIf { it.minutes > 0 }?.let { val v = (it.volumeSum.toDouble() / it.minutes).roundToInt(); "Volume $v · ≈ ${Insights.estimatedDb(v, state.volumeSteps).roundToInt()} dB" } ?: "—")
        }
        Text(
            "WHO's guideline is 80 dB for 40 hours a week; every 3 dB louder halves the safe time. Hush estimates the level " +
                "from the headphone volume while music plays, so treat it as a guide, not a measurement.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m),
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun FindPage(state: HeadphonesState, onClose: () -> Unit) {
    val context = LocalContext.current
    val data by Insights.data.collectAsState()
    val seen = data.lastSeen
    val connected = state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING
    var permTick by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val hasLoc = remember(permTick) { Insights.hasLocation(context) }
    val hasBg = remember(permTick) { Insights.hasBackgroundLocation(context) }
    val address by produceState<String?>(null, seen?.lat, seen?.lng) {
        val lat = seen?.lat ?: return@produceState
        val lng = seen.lng ?: return@produceState
        value = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { Geocoder(context).getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0) }.getOrNull()
        }
    }
    FullScreenPage("Find my headphones", onClose) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            color = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Space.l)) {
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(32.dp))
                Spacer(Modifier.height(Space.s))
                Text(
                    when {
                        connected -> "Connected to this phone"
                        seen != null -> "Last connected " + DateUtils.getRelativeTimeSpanString(seen.t)
                        else -> "Hush hasn't seen them disconnect yet"
                    },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                if (!connected && seen != null) {
                    Text(DateUtils.formatDateTime(context, seen.t, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(
                        address ?: seen.lat?.let { "%.5f, %.5f".format(seen.lat, seen.lng) } ?: "No location was available at the time",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    seen.accuracy?.let { Text("Within about ${it.roundToInt()} m", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (!connected && seen?.lat != null) {
            Spacer(Modifier.height(Space.m))
            Button(onClick = {
                val uri = Uri.parse("geo:${seen.lat},${seen.lng}?q=${seen.lat},${seen.lng}(Headphones)")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Outlined.Map, null); Spacer(Modifier.width(Space.s)); Text("Open in Maps")
            }
        }
        SectionHeader("How it works")
        Text(
            "When the headphones disconnect from this phone, Hush notes the time and, if allowed, where the phone was. " +
                "It's the last place they were together — usually where they were left.",
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
