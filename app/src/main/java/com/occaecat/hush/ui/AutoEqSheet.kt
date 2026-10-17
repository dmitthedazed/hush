@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.autoeq.AutoEq
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.roundToInt

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/**
 * AutoEq picker: search the AutoEq profile index (pre-filled with the connected model), pick a measurement,
 * preview how close the headset's six bands get to that correction, and apply it to the custom preset.
 */
@Composable
fun AutoEqSheet(
    modelName: String?,
    bandCount: Int,
    /** The preset slot Apply writes to (the active Manual / Custom slot, else Manual). */
    targetPreset: Int,
    vm: HeadphonesController,
    onApplied: (AutoEq.Entry) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var index by remember { mutableStateOf<Load<List<AutoEq.Entry>>>(Load.Loading) }
    LaunchedEffect(reload) {
        index = Load.Loading
        index = try {
            Load.Ready(AutoEq.index(context))
        } catch (e: Exception) {
            Load.Failed(e.message ?: "Network error")
        }
    }
    var query by rememberSaveable { mutableStateOf(modelName.orEmpty()) }
    var picked by remember { mutableStateOf<AutoEq.Entry?>(null) }
    var fit by remember { mutableStateOf<Load<AutoEq.Fit>>(Load.Loading) }

    fun pick(entry: AutoEq.Entry) {
        picked = entry
        fit = Load.Loading
        scope.launch {
            fit = try {
                AutoEq.fit(AutoEq.curve(entry), bandCount)?.let { Load.Ready(it) }
                    ?: Load.Failed("This headset's equalizer can't be matched to AutoEq curves")
            } catch (e: Exception) {
                Load.Failed(e.message ?: "Network error")
            }
        }
    }

    BackHandler(enabled = picked != null) { picked = null }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = picked,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            modifier = Modifier.navigationBarsPadding(),
            label = "autoeq",
        ) { entry ->
            if (entry == null) {
                SearchPage(query, { query = it }, index, onRetry = { reload++ }, onPick = ::pick)
            } else {
                PreviewPage(
                    entry, fit, Labels.presetNames[targetPreset] ?: "Manual",
                    onBack = { picked = null },
                    onRetry = { pick(entry) },
                    onApply = { levels ->
                        vm.applyEqBands(levels)
                        AutoEq.rememberApplied(context, entry, targetPreset)
                        onApplied(entry)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String, leading: (@Composable () -> Unit)? = null) {
    Row(Modifier.padding(start = if (leading != null) Space.s else Space.l, end = Space.l), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchPage(
    query: String,
    onQuery: (String) -> Unit,
    index: Load<List<AutoEq.Entry>>,
    onRetry: () -> Unit,
    onPick: (AutoEq.Entry) -> Unit,
) {
    Column {
        SheetTitle("AutoEq", "Corrections from headphone measurements, fitted to your equalizer")
        Spacer(Modifier.height(Space.m))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m),
            placeholder = { Text("Search headphones") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear") } }
            } else null,
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.height(Space.m))
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 520.dp), contentAlignment = Alignment.TopCenter) {
            when (index) {
                Load.Loading -> LoadingIndicator(Modifier.padding(top = Space.xl).size(56.dp))
                is Load.Failed -> Problem("Couldn't load the AutoEq index", index.message, onRetry)
                is Load.Ready -> {
                    val results = remember(index.value, query) { AutoEq.search(index.value, query).take(60) }
                    if (results.isEmpty()) {
                        Text(
                            if (query.isBlank()) "Type a headphone model" else "No AutoEq profiles for “$query”",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xl),
                        )
                    } else {
                        LazyColumn(
                            Modifier.padding(horizontal = Space.m),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            itemsIndexed(results, key = { _, e -> e.path }) { i, e ->
                                SegmentedListItem(
                                    onClick = { onPick(e) },
                                    shapes = ListItemDefaults.segmentedShapes(index = i, count = results.size),
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    leadingContent = { LeadIcon(Icons.Outlined.Headphones) },
                                    supportingContent = { Text(e.detail) },
                                    content = { Text(e.name) },
                                )
                            }
                            item { Spacer(Modifier.height(Space.m)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPage(
    entry: AutoEq.Entry,
    fit: Load<AutoEq.Fit>,
    targetName: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onApply: (List<Int>) -> Unit,
) {
    Column {
        SheetTitle(entry.name, entry.detail) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }
        Spacer(Modifier.height(Space.m))
        when (fit) {
            Load.Loading -> Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(56.dp))
            }
            is Load.Failed -> Box(Modifier.fillMaxWidth().height(320.dp)) {
                Problem("Couldn't load this profile", fit.message, onRetry)
            }
            is Load.Ready -> {
                val f = fit.value
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = Space.m),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.padding(Space.m)) {
                        CurveChart(f, Modifier.fillMaxWidth().height(180.dp))
                        Spacer(Modifier.height(Space.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            Legend("AutoEq target", MaterialTheme.colorScheme.outline, dashed = true)
                            Legend("Your headphones", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(Space.m))
                // The six levels that will be written.
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    f.levels.forEachIndexed { i, lv ->
                        val db = lv - 10
                        Surface(
                            Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(Modifier.padding(vertical = Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (db > 0) "+$db" else "$db",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (db == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    if (i == 0) "Bass" else Labels.sixBands[i],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Text(
                    "Within ${"%.1f".format(f.rmsError)} dB of the target on average. The headset has six fixed bands, " +
                        "so narrow peaks and dips can't be corrected. Saved to $targetName.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.m).padding(bottom = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    var saved by remember(entry) { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = {
                            com.occaecat.hush.sound.SoundStore.saveEq(entry.name, f.levels, "AutoEq · ${entry.detail}")
                            saved = true
                        },
                        enabled = !saved,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text(if (saved) "In library" else "Save to library") }
                    Button(onClick = { onApply(f.levels) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 2.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height * 1.5f,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null,
            )
        }
        Spacer(Modifier.width(Space.xs))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Log-frequency plot of the AutoEq correction (dashed) and what the six bands will produce (solid). */
@Composable
private fun CurveChart(fit: AutoEq.Fit, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val target = MaterialTheme.colorScheme.outline
    val result = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val range = (fit.target + fit.result).maxOf { kotlin.math.abs(it.db) }.coerceIn(6.0, 15.0) + 1
    Canvas(modifier) {
        val lo = log10(20.0)
        val hi = log10(16_000.0)
        val labelH = 16.dp.toPx()
        val h = size.height - labelH
        fun x(hz: Double) = ((log10(hz) - lo) / (hi - lo) * size.width).toFloat()
        fun y(db: Double) = (h / 2 - db / range * h / 2).toFloat()
        drawLine(grid, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
        listOf(100.0 to "100", 1000.0 to "1k", 10_000.0 to "10k").forEach { (hz, label) ->
            drawLine(grid, Offset(x(hz), 0f), Offset(x(hz), h), strokeWidth = 1.dp.toPx())
            val m = measurer.measure(label, labelStyle)
            drawText(m, topLeft = Offset(x(hz) - m.size.width / 2f, h + 2.dp.toPx()))
        }
        fun path(points: List<AutoEq.Point>) = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.hz), y(p.db)) else lineTo(x(p.hz), y(p.db)) }
        }
        drawPath(
            path(fit.target), target,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
        drawPath(path(fit.result), result, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun Problem(title: String, message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s))
        Button(onClick = onRetry) { Text("Try again") }
    }
}
