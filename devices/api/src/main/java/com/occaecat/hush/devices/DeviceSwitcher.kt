package com.occaecat.hush.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** An app-bar button listing every device Hush knows; nothing when there's only one. */
@Composable
fun DeviceSwitcher() {
    val host = LocalDeviceHost.current ?: return
    if (host.all.size < 2) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Devices, "Switch device") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            host.all.forEach { d ->
                DropdownMenuItem(
                    text = { Text(d.name) },
                    leadingIcon = { Icon(if (d.transport == Transport.USB) Icons.Outlined.Usb else Icons.Outlined.Bluetooth, null) },
                    trailingIcon = if (d.key == host.current?.key) ({ Icon(Icons.Outlined.Check, null) }) else null,
                    onClick = { open = false; host.open(d) },
                )
            }
        }
    }
}
