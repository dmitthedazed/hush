package com.occaecat.hush.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.jabra.JabraButtonContext

@Composable
fun JabraButtonsPage(state: HeadphonesState, vm: HeadphonesController, onClose: () -> Unit) {
    val dialogs = rememberDialogState()
    var resetting by remember { mutableStateOf<JabraButtonContext?>(null) }
    val buttons = state.jabraButtons
    val connected = state.isJabra && state.phase == Phase.CONNECTED
    val editable = connected && buttons != null && !buttons.loading && buttons.error == null
    LaunchedEffect(state.address, state.phase) { if (connected) vm.refreshJabraButtons() }
    LaunchedEffect(editable, state.address) {
        dialogs.choice = null
        resetting = null
    }
    FullScreenPage("Button controls", onClose) {
        Text("Choose what a single, double or triple press does on each earbud.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.m))
        val status = when {
            !connected -> "Connect the headphones to change their buttons. Controls are unavailable during a firmware update."
            buttons == null || buttons.loading -> "Reading and confirming button assignments…"
            buttons.error != null -> buttons.error
            buttons.slots.isEmpty() -> "These headphones did not report configurable buttons."
            else -> null
        }
        status?.let {
            Text(it, color = if (buttons?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.m))
        }
        SegmentedGroup {
            ValueRow(0, 1, Icons.Outlined.Refresh, "Refresh assignments", null,
                enabled = connected && buttons?.loading != true, onClick = vm::refreshJabraButtons)
        }
        buttons?.supportedContexts?.sortedBy { it.id }?.forEach { context ->
            val slots = buttons.slots.filter { it.slot.context == context }
            SectionHeader(context.label)
            SegmentedGroup {
                slots.forEachIndexed { index, assignment ->
                    ValueRow(index, slots.size + 1, Icons.Outlined.TouchApp, assignment.slot.label,
                        listOfNotNull(assignment.actionLabel, assignment.lockReason).joinToString(" · "),
                        enabled = editable && assignment.lockReason == null && assignment.allowedActions.isNotEmpty()) {
                        dialogs.choice = Choice(assignment.slot.label,
                            assignment.allowedActions.map { it.id to it.label }, assignment.action) { action ->
                            if (vm.state.value.address == state.address && vm.state.value.phase == Phase.CONNECTED)
                                vm.setJabraButton(assignment.slot, action)
                        }
                    }
                }
                ValueRow(slots.size, slots.size + 1, Icons.Outlined.Restore, "Reset ${context.label.lowercase()}",
                    "Restore the headphone defaults for this context", enabled = editable && buttons.canReset(context)) {
                    resetting = context
                }
            }
        }
        Spacer(Modifier.height(Space.l))
    }
    DialogHost(dialogs)
    resetting?.let { context ->
        ConfirmDialog("Restore the default left and right button assignments for ${context.label.lowercase()}?",
            onConfirm = {
                if (vm.state.value.address == state.address && vm.state.value.phase == Phase.CONNECTED)
                    vm.resetJabraButtons(context)
            }, onDismiss = { resetting = null },
            title = "Reset button controls?", confirm = "Reset")
    }
}
