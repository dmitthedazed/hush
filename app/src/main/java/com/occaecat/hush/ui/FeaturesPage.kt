package com.occaecat.hush.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoiseAware
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.Feature
import com.occaecat.hush.devices.Source
import com.occaecat.hush.devices.capabilities
import com.occaecat.hush.devices.note

/**
 * What Hush does with a device and where each part runs: built into the headphones, the phone and the headphones
 * together, or the phone alone. Read from the capability model, so a new brand gets this page by reporting hardware.
 */
@Composable
fun FeaturesPage(c: DeviceController, name: String, onClose: () -> Unit) {
    val caps = c.capabilities
    FullScreenPage("Features", onClose) {
        Text(
            "No account and no tracking: what isn't built into your $name, this phone does itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
        )
        Group("On your headphones", caps.sources.filterValues { it == Source.DEVICE }.keys) { "Built in" }
        Group("Phone and headphones together", caps.sources.filterValues { it == Source.HYBRID }.keys) { it.note(Source.HYBRID) }
        Group("On this phone", caps.sources.filterValues { it == Source.PHONE }.keys) { it.note(Source.PHONE) }
        Group("Not on these headphones", Feature.entries.filter { it !in caps }, dimmed = true) { it.missing }
    }
}

@Composable
private fun Group(title: String, features: Collection<Feature>, dimmed: Boolean = false, line: (Feature) -> String?) {
    if (features.isEmpty()) return
    val list = Feature.entries.filter { it in features }
    SectionHeader(title)
    SegmentedGroup {
        list.forEachIndexed { i, f -> InfoRow(i, list.size, f.icon, f.title, line(f).orEmpty(), dimmed = dimmed) }
    }
}

private val Feature.icon: ImageVector get() = when (this) {
    Feature.NOISE -> Icons.Outlined.NoiseAware
    Feature.EQUALIZER -> Icons.Outlined.Equalizer
    Feature.AUTO_EQ -> Icons.Outlined.AutoGraph
    Feature.BATTERY -> Icons.Outlined.BatteryFull
    Feature.VOLUME -> Icons.AutoMirrored.Outlined.VolumeUp
    Feature.SLEEP_TIMER -> Icons.Outlined.Bedtime
    Feature.FIRMWARE -> Icons.Outlined.SystemUpdate
    Feature.MICROPHONE -> Icons.Outlined.Mic
    Feature.SCENES -> Icons.Outlined.AutoAwesome
    Feature.INSIGHTS -> Icons.Outlined.Insights
    Feature.WIDGETS -> Icons.Outlined.Widgets
}

/** Why a feature is absent: the phone can't stand in for every piece of hardware. */
private val Feature.missing: String get() = when (this) {
    Feature.NOISE -> "Needs microphones in the headphones"
    Feature.BATTERY -> "Not reported by these headphones"
    Feature.FIRMWARE -> "No updates from the maker"
    else -> "Not available"
}
