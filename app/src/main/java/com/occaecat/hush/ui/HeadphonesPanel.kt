package com.occaecat.hush.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.R

/**
 * The quick panel ColorOS opens from My Devices and the control center, the way Melody does for OPPO earbuds:
 * a sheet over whatever was on screen with the headset, its battery and the noise modes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeadphonesPanel(
    state: HeadphonesState,
    vm: HeadphonesController,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.m).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProductDisk(renderFor(state.modelColor), size = 88.dp, dimmed = state.phase != Phase.CONNECTED)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(state.displayName, style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(
                        when (state.phase) {
                            Phase.CONNECTED -> listOfNotNull("Connected", state.codec?.label?.takeIf { it != "—" }).joinToString(" · ")
                            else -> subtitleFor(state) ?: "Not connected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.battery?.takeIf { state.phase == Phase.CONNECTED }?.let {
                    Text("$it%", style = MaterialTheme.typography.displaySmallEmphasized, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(Space.m))
            when {
                state.phase == Phase.CONNECTED && state.ncAsm != null -> {
                    val nc = state.ncAsm!!
                    NoiseControlCard(nc.mode, nc.ambientLevel, nc.voiceFocus, vm, Modifier.fillMaxWidth())
                }
                state.phase == Phase.CONNECTING || state.phase == Phase.RECONNECTING ->
                    LoadingIndicator(Modifier.padding(Space.l))
                else -> FilledTonalButton(onClick = { vm.connectSaved() }) { Text("Connect") }
            }
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenApp) { Text("All settings") }
            }
            Spacer(Modifier.height(Space.s))
        }
    }
}
