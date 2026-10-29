package com.occaecat.hush.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase

@Composable
fun JabraButtonsScreen(state: HeadphonesState, vm: HeadphonesController, onClose: () -> Unit) {
    val dialogs = rememberDialogState()
    val buttons = state.jabraButtons
    val connected = state.isJabra && state.phase == Phase.CONNECTED
    val editable = connected && buttons != null && !buttons.loading && buttons.error == null
    BackHandler(onBack = onClose)
    LaunchedEffect(state.address, state.phase) { if (connected) vm.refreshJabraButtons() }
    LaunchedEffect(editable, state.address) {
        dialogs.choice = null
        dialogs.confirm = null
    }
    SectionScaffold("Button controls") {
        item { ValueRow(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Device", null, onClick = onClose) }
        val status = when {
            !connected -> "Connect the headphones to change their buttons. Controls are unavailable during a firmware update."
            buttons == null || buttons.loading -> "Reading and confirming button assignments…"
            buttons.error != null -> buttons.error
            buttons.slots.isEmpty() -> "These headphones did not report configurable buttons."
            else -> null
        }
        if (status != null) item {
            Text(status, modifier = Modifier.padding(Tv.Small),
                color = if (buttons?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ValueRow(Icons.Outlined.Refresh, "Refresh assignments", null,
                enabled = connected && buttons?.loading != true, onClick = vm::refreshJabraButtons)
        }
        buttons?.supportedContexts?.sortedBy { it.id }?.forEach { context ->
            item { SectionHeader(context.label) }
            buttons.slots.filter { it.slot.context == context }.forEach { assignment -> item {
                ValueRow(Icons.Outlined.TouchApp, assignment.slot.label, assignment.actionLabel,
                    supporting = assignment.lockReason,
                    enabled = editable && assignment.lockReason == null && assignment.allowedActions.isNotEmpty()) {
                    dialogs.choice = Choice(assignment.slot.label, assignment.allowedActions.map { it.id to it.label },
                        assignment.action) {
                        if (vm.state.value.address == state.address && vm.state.value.phase == Phase.CONNECTED)
                            vm.setJabraButton(assignment.slot, it)
                    }
                }
            } }
            item {
                ValueRow(Icons.Outlined.Restore, "Reset ${context.label.lowercase()}", null,
                    supporting = "Restore the headphone defaults for this context", enabled = editable && buttons.canReset(context)) {
                    dialogs.confirm = Confirm("Restore the default left and right button assignments for ${context.label.lowercase()}?",
                        "Reset") {
                        if (vm.state.value.address == state.address && vm.state.value.phase == Phase.CONNECTED)
                            vm.resetJabraButtons(context)
                    }
                }
            }
        }
    }
    DialogHost(dialogs)
}
