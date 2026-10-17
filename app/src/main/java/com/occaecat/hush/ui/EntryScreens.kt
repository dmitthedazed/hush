@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.PairedDevice
import com.occaecat.hush.R

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(Space.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val compact = LocalLayout.current.compactHeight
            ShapeBadge(Icons.Outlined.Bluetooth, size = if (compact) 96.dp else 160.dp, polygon = MaterialShapes.Cookie12Sided, spinning = true)
            Spacer(Modifier.height(if (compact) Space.m else Space.xl))
            Text(
                "Talk to your headphones",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.s))
            Text(
                "Hush needs the Nearby devices permission to open a control channel to your Sony headset.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(Space.xl))
            Button(
                onClick = onGrant,
                modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text("Allow", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }
}

@Composable
fun DevicePickerScreen(state: HeadphonesState, onRefresh: () -> Unit, onPick: (String) -> Unit) {
    TabScaffold(
        title = "Hush",
        subtitle = "Choose your headphones",
        actions = { IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh") } },
    ) {
        wideItem {
            Text(
                "Pair your headphones in Android Bluetooth settings first, then pick them here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = ItemWidth.padding(horizontal = Space.s),
            )
        }
        val sony = state.paired.filter { it.looksSony }
        val other = state.paired.filterNot { it.looksSony }
        if (state.paired.isEmpty()) wideItem {
            Column(ItemWidth.padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShapeBadge(Icons.Outlined.Bluetooth, size = 96.dp, polygon = MaterialShapes.Ghostish,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Space.m))
                Text("No paired devices", style = MaterialTheme.typography.titleLargeEmphasized)
            }
        }
        if (sony.isNotEmpty()) item { DeviceGroup("Sony headphones", sony, state.address, onPick) }
        if (other.isNotEmpty()) item { DeviceGroup("Other devices", other, state.address, onPick) }
    }
}

@Composable
private fun DeviceGroup(title: String, devices: List<PairedDevice>, lastUsed: String?, onPick: (String) -> Unit) {
    Column(ItemWidth) {
        SectionHeader(title)
        SegmentedGroup {
            devices.forEachIndexed { i, d ->
                SegmentedListItem(
                    onClick = { onPick(d.address) },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = devices.size),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = {
                        if (d.name.contains("1000XM4")) ProductDisk(R.drawable.xm4_side, size = 48.dp)
                        else if (d.looksSony) ShapeBadge(Icons.Outlined.Headphones, size = 48.dp, polygon = MaterialShapes.Cookie6Sided)
                        else ShapeBadge(Icons.Outlined.Bluetooth, size = 48.dp, polygon = MaterialShapes.Circle,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer)
                    },
                    supportingContent = { Text(if (d.address == lastUsed) "Last used" else d.address) },
                    trailingContent = if (d.address == lastUsed) {
                        { Icon(Icons.Outlined.Check, "Last used", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    content = { Text(d.name) },
                )
            }
        }
    }
}
