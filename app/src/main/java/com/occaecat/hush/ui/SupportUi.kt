@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.features.Backup
import com.occaecat.hush.features.Help
import com.occaecat.hush.guide.InstructionGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

// ---------------------------------------------------------------------------------------------------------------
// Help
// ---------------------------------------------------------------------------------------------------------------

private val ArticleIcons = mapOf(
    Help.Article.TROUBLESHOOTING to Icons.AutoMirrored.Outlined.HelpOutline,
    Help.Article.CONNECTION to Icons.Outlined.Bluetooth,
    Help.Article.MULTIPOINT to Icons.Outlined.Devices,
    Help.Article.FIRMWARE to Icons.Outlined.SystemUpdate,
    Help.Article.APP_GUIDE to Icons.AutoMirrored.Outlined.MenuBook,
)

@Composable
fun HelpPage(state: HeadphonesState, onGuide: () -> Unit, onTour: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = state.modelName ?: "WH-1000XM4"
    var opening by remember { mutableStateOf<Help.Article?>(null) }
    FullScreenPage("Help", onClose) {
        Surface(onClick = { Help.openHelpGuide(context, model) }, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(renderFor(state.modelColor)), null, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(Space.m))
                Column {
                    Text("$model Help Guide", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text("Sony's full manual, in your language", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SectionHeader("In Hush")
        SegmentedGroup {
            ValueRow(0, 2, Icons.Outlined.Slideshow, "Take the tour", "Gestures and buttons, step by step", onClick = onTour)
            ValueRow(1, 2, Icons.AutoMirrored.Outlined.MenuBook, "How to use", "All of it on one page", onClick = onGuide)
        }
        SectionHeader("Sony support")
        SegmentedGroup {
            Help.Article.entries.forEachIndexed { i, a ->
                ValueRow(i, Help.Article.entries.size, ArticleIcons[a] ?: Icons.AutoMirrored.Outlined.HelpOutline, a.title, a.subtitle,
                    trailing = if (opening == a) ({ LoadingIndicator(Modifier.size(24.dp)) }) else null) {
                    if (opening != null) return@ValueRow
                    opening = a
                    scope.launch { Help.open(context, model, state.firmware, a); opening = null }
                }
            }
        }
        Text("Articles open in the browser: Sony's site only serves them there.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m))
    }
}

/** Opens one help article from anywhere (the "controls are elsewhere" card, the not-connected placeholder). */
fun openHelpArticle(context: Context, state: HeadphonesState, article: Help.Article, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch { Help.open(context, state.modelName ?: "WH-1000XM4", state.firmware, article) }
}

// ---------------------------------------------------------------------------------------------------------------
// Backup
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun BackupPage(state: HeadphonesState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Backup.export(context, state, history).toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val o = withContext(Dispatchers.IO) { runCatching { Backup.read(context, uri) }.getOrNull() }
            if (o == null) Toast.makeText(context, "That isn't a Hush backup", Toast.LENGTH_SHORT).show() else pending = o
        }
    }
    FullScreenPage("Backup & restore", onClose) {
        Text(
            "One file with your EQ library, scenes, Adaptive Sound Control, places and app profiles, the touch-and-hold " +
                "action, and the headphones' own settings. Keep it in Drive or move it to a new phone; it's plain JSON, " +
                "so the laptop helper can read it too.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.s),
        )
        SectionHeader("Back up")
        SegmentedGroup {
            SwitchRow(0, 1, Icons.Outlined.Backup, "Include history", "Battery, listening and last location", history) { history = it }
        }
        Spacer(Modifier.height(Space.m))
        Button(onClick = { save.launch("hush-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(Space.s)); Text("Save backup…")
        }
        SectionHeader("Restore")
        OutlinedButton(onClick = { load.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Outlined.Upload, null); Spacer(Modifier.width(Space.s)); Text("Restore from file…")
        }
    }
    pending?.let { o -> RestoreDialog(o, state) { pending = null } }
}

@Composable
private fun RestoreDialog(o: JSONObject, state: HeadphonesState, onDone: () -> Unit) {
    val context = LocalContext.current
    val s = remember(o) { Backup.summary(o) }
    val connected = state.phase == Phase.CONNECTED
    var headset by remember { mutableStateOf(connected && s.hasHeadset) }
    var history by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Outlined.Restore, null) },
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(listOfNotNull(
                    s.model, DateUtils.getRelativeTimeSpanString(s.created).toString(),
                    "${s.eqCount} saved EQs", "${s.sceneCount} scenes",
                ).joinToString(" · "))
                Text("Your current Hush settings will be replaced.", style = MaterialTheme.typography.bodySmall)
                if (s.hasHeadset) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(headset, { headset = it }, enabled = connected)
                    Text(if (connected) "Also apply headphone settings" else "Connect to apply headphone settings")
                }
                if (s.hasInsights) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(history, { history = it })
                    Text("Also restore history")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Backup.restore(context, o, headset, history)
                Toast.makeText(context, "Restored", Toast.LENGTH_SHORT).show()
                onDone()
            }) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------------------------
// First-run tour
// ---------------------------------------------------------------------------------------------------------------

/** Shown once, the first time a headset with an instruction guide is connected; also from Help. */
@Composable
fun TourHost(state: HeadphonesState) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hush", Context.MODE_PRIVATE) }
    var done by remember { mutableStateOf(prefs.getBoolean("tour_done", false)) }
    if (done || state.phase != Phase.CONNECTED || state.guideCategories.isNullOrEmpty()) return
    TourPage(state) {
        prefs.edit().putBoolean("tour_done", true).apply()
        done = true
    }
}

@Composable
fun TourPage(state: HeadphonesState, onClose: () -> Unit) {
    val model = state.modelName ?: state.deviceName ?: "WH-1000XM4"
    val topics by produceState<List<InstructionGuide.Topic>?>(null, model) {
        value = runCatching { InstructionGuide.load(model, state.guideCategories, state.firmware) }.getOrNull().orEmpty()
    }
    BackHandler(onBack = onClose)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val t = topics
            if (t == null) {
                Box(contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
                return@Surface
            }
            val pages = 1 + t.size
            val pager = rememberPagerState { pages }
            val scope = rememberCoroutineScope()
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("Skip") }
                }
                HorizontalPager(pager, Modifier.weight(1f)) { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.m)) {
                        if (page == 0) {
                            Spacer(Modifier.height(Space.xl))
                            Image(painterResource(renderFor(state.modelColor)), null, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Fit)
                            Spacer(Modifier.height(Space.l))
                            Text("Get to know your $model", style = MaterialTheme.typography.headlineMediumEmphasized)
                            Spacer(Modifier.height(Space.s))
                            Text(
                                if (t.isEmpty()) "Sony's tutorial isn't available right now. You can open it later from Device → Help."
                                else "${t.size} quick tips from Sony's tutorial: the touch panel, Quick Attention and the CUSTOM button.",
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            TopicCard(t[page - 1])
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(pages) { i ->
                            Box(Modifier.size(if (i == pager.currentPage) 10.dp else 6.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                    val last = pager.currentPage == pages - 1
                    Button(onClick = { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier.height(48.dp)) { Text(if (last) "Done" else "Next") }
                }
            }
        }
    }
}

