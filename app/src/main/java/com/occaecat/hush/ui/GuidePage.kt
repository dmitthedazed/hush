@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.guide.InstructionGuide

/** Sound Connect's instruction guide for the connected model, laid out natively (see [InstructionGuide]). */
@Composable
fun GuidePage(state: HeadphonesState, onClose: () -> Unit) {
    if (!state.capabilities.vendorGuide) { HelpPage(state, {}, {}, onClose); return }
    val model = state.modelName ?: state.deviceName ?: "WH-1000XM4"
    var attempt by remember { mutableIntStateOf(0) }
    var topics by remember { mutableStateOf<List<InstructionGuide.Topic>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(model, attempt) {
        failed = false
        val t = runCatching { InstructionGuide.load(model, state.guideCategories, state.firmware) }.getOrNull()
        topics = t
        failed = t.isNullOrEmpty()
    }

    FullScreenPage("How to use", onClose) {
        when {
            failed -> Column(Modifier.fillMaxWidth().padding(top = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load the guide for $model", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Space.xs))
                Text("Sony serves it online. Check the connection and try again.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Space.m))
                FilledTonalButton(onClick = { topics = null; attempt++ }) { Text("Try again") }
            }
            topics == null -> Box(Modifier.fillMaxWidth().padding(top = Space.xl), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(56.dp))
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(Space.m), modifier = Modifier.padding(bottom = Space.l)) {
                topics!!.forEach { TopicCard(it) }
                Text("From Sony's Help Guide for $model.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.s))
            }
        }
    }
}

@Composable
internal fun TopicCard(t: InstructionGuide.Topic) {
    val dark = isSystemInDarkTheme()
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(Space.m)) {
            (if (dark) t.gifDark ?: t.gif else t.gif)?.let { url ->
                Gif(url, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large))
                Spacer(Modifier.height(Space.m))
            }
            Text(t.title, style = MaterialTheme.typography.titleMediumEmphasized)
            t.description?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (t.gestures.isNotEmpty()) {
                Spacer(Modifier.height(Space.m))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    t.gestures.forEach { g ->
                        Row(verticalAlignment = Alignment.Top) {
                            LeadIcon(gestureIcon(g.icon))
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                                Text(g.gesture, style = MaterialTheme.typography.titleSmall)
                                g.actions.forEach {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The pages' black gesture icons don't survive a dark theme; Material equivalents by file name. */
private fun gestureIcon(file: String): ImageVector = when {
    "double" in file -> Icons.Outlined.TouchApp
    "forward" in file -> Icons.AutoMirrored.Outlined.ArrowForward
    "backward" in file -> Icons.AutoMirrored.Outlined.ArrowBack
    "up" in file -> Icons.Outlined.ArrowUpward
    "down" in file -> Icons.Outlined.ArrowDownward
    "long" in file -> Icons.Outlined.PanTool
    else -> Icons.Outlined.TouchApp
}

/** An animated GIF (AnimatedImageDrawable), sized to its own aspect ratio once decoded. */
@Composable
private fun Gif(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, url) {
        val f = InstructionGuide.gifFile(context, url) ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(f)) }.getOrNull()
        }
    }
    val d = drawable
    if (d == null) {
        Box(modifier.aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(40.dp))
        }
        return
    }
    val ratio = if (d.intrinsicHeight > 0) d.intrinsicWidth.toFloat() / d.intrinsicHeight else 16f / 9f
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true } },
        update = { iv -> if (iv.drawable !== d) { iv.setImageDrawable(d); (d as? AnimatedImageDrawable)?.start() } },
        modifier = modifier.aspectRatio(ratio),
    )
}
