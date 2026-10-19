package com.occaecat.hush.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column

/** An app-bar button listing every registered device (connected or not) and a way to add another. */
@Composable
fun DeviceSwitcher() {
    val host = LocalDeviceHost.current ?: return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Devices, "Switch device") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            host.all.forEach { d ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(d.name)
                            Text(
                                if (d.active) "Connected" else "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (d.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(if (d.transport == Transport.USB) Icons.Outlined.Usb else Icons.Outlined.Bluetooth, null) },
                    trailingIcon = if (d.key == host.current?.key) ({ Icon(Icons.Outlined.Check, null) }) else null,
                    onClick = { open = false; host.open(d) },
                )
            }
            if (host.all.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add device") },
                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                onClick = { open = false; host.add() },
            )
        }
    }
}
