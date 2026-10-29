package com.occaecat.hush.tv.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.occaecat.hush.HeadphoneBrand
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.tv.R

/** Full-screen two-pane layout for the screens before a session: render on the left, the task on the right. */
@Composable
private fun EntryLayout(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Tv.SideMargin, vertical = Tv.TopMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.xm4_angle), null,
                Modifier.fillMaxHeight(0.8f).width(300.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(Tv.Gutter * 2))
            Column(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    val focus = remember { FocusRequester() }
    EntryLayout {
        Text("Hush", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Tv.Small))
        Text(
            "Control your headphones from the TV: noise cancelling, ambient sound, equalizer and device settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tv.Gutter))
        Text(
            "Hush needs the Nearby devices permission to talk to headphones paired with this TV.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tv.Gutter))
        Button(onClick = onGrant, modifier = Modifier.focusRequester(focus)) {
            Icon(Icons.Outlined.Bluetooth, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Allow")
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun DevicePickerScreen(state: HeadphonesState, onRefresh: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    // Coming back from Bluetooth settings: show what was just paired.
    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose {}
    }
    EntryLayout {
        Text("Choose headphones", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pair them with the TV first (Settings → Remotes & accessories), then pick them here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tv.Gutter))
        LazyColumn(
            Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Tv.Small),
        ) {
            items(state.paired, key = { it.address }) { d ->
                val first = d == state.paired.first()
                ListItem(
                    selected = false,
                    onClick = { onPick(d.address) },
                    modifier = if (first) Modifier.focusRequester(focus) else Modifier,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    leadingContent = {
                        Icon(
                            if (d.brand != HeadphoneBrand.UNKNOWN) Icons.Outlined.Headphones else Icons.Outlined.Speaker, null,
                            Modifier.size(ListItemDefaults.IconSize),
                        )
                    },
                    headlineContent = { Text(d.name) },
                    supportingContent = {
                        Text(if (d.address == state.address) "Last used" else if (d.brand == HeadphoneBrand.JABRA) "Jabra" else if (d.looksSony) "Sony" else d.address)
                    },
                )
            }
        }
        if (state.paired.isEmpty()) {
            Text(
                "No paired Bluetooth devices yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Tv.Gutter))
        Row(horizontalArrangement = Arrangement.spacedBy(Tv.Gap)) {
            Button(
                onClick = { openPairing(context) },
                modifier = if (state.paired.isEmpty()) Modifier.focusRequester(focus) else Modifier,
            ) {
                Icon(Icons.Outlined.Bluetooth, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Pair headphones")
            }
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Refresh")
            }
        }
    }
    LaunchedEffect(state.paired.isEmpty()) { runCatching { focus.requestFocus() } }
}

/**
 * Google TV has no ACTION_BLUETOOTH_SETTINGS activity; pairing lives in TV Settings' "Remotes & accessories",
 * reached through CONNECT_INPUT. Fall back to the phone-style action (other Android TV builds), then Settings.
 */
private fun openPairing(context: android.content.Context) {
    val candidates = listOf(
        Intent("com.google.android.intent.action.CONNECT_INPUT"),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
        ?.let { runCatching { context.startActivity(it) } }
}
