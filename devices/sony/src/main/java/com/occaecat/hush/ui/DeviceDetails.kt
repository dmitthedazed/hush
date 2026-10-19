@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.devices.Detail
import com.occaecat.hush.devices.DetailGroup

/**
 * Everything the headset says about itself: device info, the firmware-update parameters Sound Connect sends to
 * Sony's update server (UPDT_GET_PARAM), radio and protocol. Identifiers are hidden until tapped.
 */
fun sonyDetails(state: HeadphonesState): List<DetailGroup> {
    val u = state.updateInfo
    return listOfNotNull(
        group("Headphones", buildList {
            add(Info(Icons.Outlined.Headphones, "Model", state.modelName))
            add(Info(Icons.Outlined.Category, "Series", state.modelSeries?.let { SERIES[it] ?: hex(it) }))
            add(Info(Icons.Outlined.Palette, "Colour", state.modelColor?.let { COLORS[it] ?: hex(it) }))
            add(Info(Icons.Outlined.Info, "Firmware", state.firmware))
            add(Info(Icons.Outlined.Tag, "Serial number", state.serial, hidden = true))
            add(Info(Icons.Outlined.Bluetooth, "Bluetooth address", state.address, hidden = true))
        }),
        group("Firmware updates", buildList {
            add(Info(Icons.Outlined.Public, "Region", u[4]?.let { regionLabel(it) }))
            add(Info(Icons.Outlined.Widgets, "Product category", u[2]))
            add(Info(Icons.Outlined.Badge, "Service ID", u[3]))
            add(Info(Icons.Outlined.Language, "Language", u[5]?.ifEmpty { "Not set" }))
            add(Info(Icons.Outlined.SystemUpdate, "Update method", u[9]?.toIntOrNull()?.let(::updateMethod)))
            add(Info(Icons.Outlined.BatteryAlert, "Battery needed to update", u[8]?.let { "$it% or more" }))
            add(Info(Icons.Outlined.BatteryAlert, "Update stops below", u[10]?.let { "$it%" }))
        }),
        group("Radio & protocol", buildList {
            add(Info(Icons.Outlined.SettingsInputAntenna, "BLE transmit power", u[7]?.let { "$it dBm" }))
            add(Info(Icons.Outlined.Memory, "Protocol version", state.protocolVersion?.let { "%d.%d (0x%04X)".format(it shr 12, (it shr 8) and 0xF, it) }))
            add(Info(Icons.Outlined.Widgets, "Supported functions",
                state.functions.takeIf { it.isNotEmpty() }?.let { f -> "${f.size}: " + f.sorted().joinToString(" ") { "%02X".format(it) } }))
            add(Info(Icons.Outlined.Fingerprint, "Device binding ID", u[11], hidden = true))
        }),
    )
}

private fun group(title: String, rows: List<Info>) =
    rows.filter { !it.value.isNullOrEmpty() }.takeIf { it.isNotEmpty() }?.let { shown ->
        DetailGroup(title, shown.map { Detail(it.title, it.value!!, it.icon, copy = !it.hidden, hidden = it.hidden) })
    }

private data class Info(val icon: ImageVector, val title: String, val value: String?, val hidden: Boolean = false)

private fun hex(v: Int) = "0x%02X".format(v)

/** Sony ModelSeries. */
private val SERIES = mapOf(
    0x00 to "None", 0x10 to "Extra Bass", 0x11 to "ULT Power Sound", 0x20 to "h.ear", 0x30 to "Premium (1000X)",
    0x40 to "Sports", 0x50 to "Casual", 0x60 to "LinkBuds", 0x70 to "Neckband", 0x80 to "LinkPod", 0x90 to "Gaming",
)

/** Sony ModelColor (the common ones). */
private val COLORS = mapOf(
    0x00 to "Default", 0x01 to "Black", 0x02 to "White", 0x03 to "Silver", 0x04 to "Red", 0x05 to "Blue",
    0x06 to "Pink", 0x07 to "Yellow", 0x08 to "Green", 0x09 to "Gray", 0x0A to "Gold", 0x0B to "Cream",
    0x0C to "Orange", 0x0D to "Brown", 0x0E to "Violet",
)

/** UpdateMethod: high nibble is the chip family, low bits resumable / background / true wireless. */
private fun updateMethod(v: Int): String {
    val chip = when (v and 0xF0) { 0x20 -> "MediaTek"; 0x10 -> "CSR (Qualcomm)"; 0x00 -> "Tandem"; else -> hex(v) }
    val flags = listOfNotNull("background".takeIf { v and 0x04 != 0 }, "resumable".takeIf { v and 0x01 != 0 }, "earbuds".takeIf { v and 0x02 != 0 })
    return (listOf(chip) + flags).joinToString(" · ")
}

/** Sony destination codes seen on headphones; anything else is shown as-is. */
private fun regionLabel(code: String) = when (code.uppercase()) {
    "CE7" -> "Europe (CE7)"
    "E" -> "Europe (E)"
    "U", "U2", "UC" -> "Americas ($code)"
    "JE", "J" -> "Japan ($code)"
    "CN" -> "China (CN)"
    "AU", "AU1" -> "Australia ($code)"
    else -> code
}
