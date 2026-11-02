@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.diag.HeadsetProbe
import com.occaecat.hush.features.HeadsetCheck
import kotlinx.coroutines.launch

/** Runs the headset self test and mails the results; for models nobody has tried with Hush. */
@Composable
fun HeadsetCheckPage(state: HeadphonesState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by HeadsetCheck.ui.collectAsState()
    var note by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val connected = state.phase == Phase.CONNECTED
    val canMail = ui.report != null || !HeadphonesController.get(context).sessionLog.isEmpty
    FullScreenPage("Headset check", onClose) {
        Text(
            "Hush hasn't been tried with every model. The check reads everything the headphones report, switches each " +
                "setting they offer to another value and back, and checks the headphones confirm it. It takes about a " +
                "minute and leaves your settings as they were; you may hear a few prompts. Buttons, sound quality and " +
                "multipoint are left alone because changing them reconnects the headphones.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.s),
        )
        Spacer(Modifier.height(Space.m))
        if (ui.running) {
            val p = ui.progress
            LinearWavyProgressIndicator(progress = { p?.let { it.done.toFloat() / maxOf(it.total, 1) } ?: 0f }, modifier = Modifier.fillMaxWidth())
            Text(p?.let { "${it.done} of ${it.total}: ${it.what}" } ?: "Starting…", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m))
        } else {
            Button(onClick = { HeadsetCheck.run(context) }, enabled = connected && !state.demo, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(Space.s)); Text(if (ui.report == null) "Run the check" else "Run again")
            }
            if (!connected) Text(
                "Connect the headphones first.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s, vertical = Space.s),
            )
        }

        ui.report?.takeIf { it.checks.isNotEmpty() }?.let { r ->
            SectionHeader("${r.passed} passed · ${r.failed} failed${if (r.skipped > 0) " · ${r.skipped} skipped" else ""}")
            SegmentedGroup {
                r.checks.forEachIndexed { i, c ->
                    val (icon, label) = when (c.verdict) {
                        HeadsetProbe.Verdict.PASS -> Icons.Outlined.CheckCircle to "Works"
                        HeadsetProbe.Verdict.PARTIAL -> Icons.Outlined.WarningAmber to "Only one way"
                        HeadsetProbe.Verdict.FAIL -> Icons.Outlined.ErrorOutline to "Not confirmed"
                        HeadsetProbe.Verdict.SKIPPED -> Icons.Outlined.RemoveCircleOutline to "Skipped"
                    }
                    ValueRow(i, r.checks.size, icon, c.name, if (expanded == c.name) c.notes.joinToString("\n") { it.trim() } else label) {
                        expanded = if (expanded == c.name) null else c.name
                    }
                }
            }
        }

        if (!ui.running && canMail) {
            SectionHeader("Send to the developer")
            OutlinedTextField(note, { note = it }, label = { Text("Anything you noticed (optional)") }, minLines = 2,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.m))
            OutlinedButton(onClick = {
                val report = ui.report ?: HeadsetCheck.collect(context)
                scope.launch { HeadsetCheck.mail(context, report, note) }
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Outlined.Mail, null, Modifier.size(24.dp)); Spacer(Modifier.width(Space.s)); Text("Email the results")
            }
            Text(
                "Goes to ${HeadsetCheck.TO} from your mail app, so you see it before it's sent. Bluetooth addresses, " +
                    "device names, the serial number and track names are replaced first.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m),
            )
        }
    }
}
