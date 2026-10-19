@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.occaecat.hush.apple.earpods

import com.occaecat.hush.ui.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.apple.usb.EarPodsCheck
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.roundToInt

/** Apple's own pages for the EarPods (USB-C), checked 2026-10-19. */
private object AppleLinks {
    const val GUIDE = "https://cdsassets.apple.com/live/6GJYWVAV/user/earpods-usb-c.pdf"
    const val MANUALS = "https://support.apple.com/en-us/docs/iphone/301005"
    const val COVERAGE = "https://checkcoverage.apple.com/"
    const val VERIFYING = "https://support.apple.com/guide/security/verifying-accessories-sec70a4f377d/web"
    const val STORE = "https://www.apple.com/shop/product/myqy3am/a/earpods-usb-c"
    const val WARRANTY = "https://www.apple.com/legal/warranty/"
    const val SOUND = "https://www.apple.com/sound/"
}

/** What the phone will actually do: both layers, drawn over 20 Hz–20 kHz on a ±12 dB scale. */
@Composable
internal fun ResponseCurve(eq: SoftEq.Settings, modifier: Modifier) {
    val line = if (eq.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val axis = MaterialTheme.colorScheme.outlineVariant
    val points = remember(eq) { SoftEq.response(eq) }
    Canvas(modifier) {
        fun x(hz: Double) = (ln(hz / 20.0) / ln(1000.0)).toFloat() * size.width
        fun y(db: Double) = size.height / 2 - (db.coerceIn(-15.0, 15.0) / 15.0).toFloat() * size.height / 2
        drawLine(axis, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.hz), y(p.db)) else path.lineTo(x(p.hz), y(p.db)) }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
    }
}

// --- Authenticity --------------------------------------------------------------------------------------------------

@Composable
internal fun Authenticity(state: EarPods.State) {
    val report = state.report ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val (icon, title, body) = when (report.verdict) {
        EarPodsCheck.Verdict.GENUINE -> Triple(Icons.Outlined.VerifiedUser, "Looks genuine",
            "Every check matches Apple’s EarPods (USB-C).")
        EarPodsCheck.Verdict.LIKELY -> Triple(Icons.Outlined.GppGood, "Probably genuine",
            "Nothing contradicts it so far. Allow USB access for the full check.")
        EarPodsCheck.Verdict.SUSPICIOUS -> Triple(Icons.Outlined.GppMaybe, "Might be a fake",
            "Some of what these EarPods report doesn’t match Apple’s.")
        EarPodsCheck.Verdict.NOT_EARPODS -> Triple(Icons.Outlined.GppMaybe, "Not EarPods (USB-C)",
            "This device doesn’t identify itself as Apple EarPods.")
    }
    val good = report.verdict == EarPodsCheck.Verdict.GENUINE || report.verdict == EarPodsCheck.Verdict.LIKELY
    Column(ItemWidth) {
        SectionHeader("Authenticity")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(if (good) colors.primaryContainer else colors.errorContainer).padding(Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(icon, size = 48.dp, polygon = if (good) MaterialShapes.Cookie9Sided else MaterialShapes.Burst,
                    container = if (good) colors.primary else colors.error, content = if (good) colors.onPrimary else colors.onError)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized,
                        color = if (good) colors.onPrimaryContainer else colors.onErrorContainer)
                    Text("${report.passed} of ${report.items.size} checks passed",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (good) colors.onPrimaryContainer else colors.onErrorContainer)
                }
            }
            Spacer(Modifier.height(Space.s))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = if (good) colors.onPrimaryContainer else colors.onErrorContainer)
            Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (!state.hasAccess) Button(onClick = EarPods::requestAccess) { Text("Allow USB access") }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide checks" else "Show checks") }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(Space.s))
            SegmentedGroup {
                report.items.forEachIndexed { i, item ->
                    InfoRow(i, report.items.size, when (item.result) {
                        EarPodsCheck.Result.PASS -> Icons.Outlined.CheckCircle
                        EarPodsCheck.Result.FAIL -> Icons.Outlined.Cancel
                        EarPodsCheck.Result.UNKNOWN -> Icons.Outlined.HelpOutline
                    }, item.title, item.detail)
                }
            }
            Text(
                "This compares what the EarPods report over USB with a genuine pair. EarPods have no cryptographic " +
                    "authentication, so a perfect copy could still pass; a failed check is the meaningful result. For " +
                    "certainty, check the serial number with Apple.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
            )
        }
    }
}

// --- Details, remote, docs -----------------------------------------------------------------------------------------

@Composable
internal fun Remote() {
    // From Apple's EarPods (USB-C) user guide; Android maps the remote to the same media keys.
    val rows = listOf(
        Triple(Icons.Outlined.PlayArrow, "Play or pause", "Press the center button"),
        Triple(Icons.Outlined.SkipNext, "Next track", "Press twice · press twice and hold to fast-forward"),
        Triple(Icons.Outlined.SkipPrevious, "Previous track", "Press three times · three times and hold to rewind"),
        Triple(Icons.Outlined.Call, "Calls", "Press to answer or end · hold to decline"),
        Triple(Icons.Outlined.RecordVoiceOver, "Assistant", "Press and hold"),
    )
    Column(ItemWidth) {
        SectionHeader("Remote")
        SegmentedGroup { rows.forEachIndexed { i, (icon, title, value) -> InfoRow(i, rows.size, icon, title, value) } }
    }
}

@Composable
internal fun AppleDocs(state: EarPods.State) {
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    data class Doc(val icon: ImageVector, val title: String, val value: String, val url: String, val before: (() -> Unit)? = null)
    val docs = listOf(
        Doc(Icons.Outlined.Description, "User guide", "PDF from Apple", AppleLinks.GUIDE),
        Doc(Icons.Outlined.Description, "Manuals and specs", "Apple Support", AppleLinks.MANUALS),
        Doc(Icons.Outlined.Shield, "Check coverage", if (state.serial != null) "Copies the serial number" else "Needs the serial number",
            AppleLinks.COVERAGE) {
            state.serial?.let {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Serial number", it))
                Toast.makeText(context, "Serial number copied", Toast.LENGTH_SHORT).show()
            }
        },
        Doc(Icons.Outlined.Policy, "How Apple verifies accessories", "Apple Platform Security", AppleLinks.VERIFYING),
        Doc(Icons.Outlined.Hearing, "Hearing health", "Safe listening levels", AppleLinks.SOUND),
        Doc(Icons.Outlined.VerifiedUser, "Hardware warranty", "Apple Legal", AppleLinks.WARRANTY),
        Doc(Icons.Outlined.ShoppingBag, "Apple Store", "EarPods (USB-C)", AppleLinks.STORE),
    )
    Column(ItemWidth) {
        SectionHeader("Apple")
        SegmentedGroup {
            docs.forEachIndexed { i, d -> ValueRow(i, docs.size, d.icon, d.title, d.value) { d.before?.invoke(); open(d.url) } }
        }
    }
}
