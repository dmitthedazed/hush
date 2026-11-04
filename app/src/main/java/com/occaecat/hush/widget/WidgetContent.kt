package com.occaecat.hush.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.occaecat.hush.R

// ---- shared pieces ----

/** Opens the device's quick panel when it has one, else the app. */
class OpenAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(context: android.content.Context, glanceId: androidx.glance.GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val device = com.occaecat.hush.DeviceModules.current.value
        val intent = device?.let { com.occaecat.hush.DeviceModules.controller(it).panelIntent(context) }
            ?: com.occaecat.hush.devices.appIntent(context)
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private val openPanel: Action get() = androidx.glance.appwidget.action.actionRunCallback<OpenAction>()

/** Rounded widget surface that follows the system (Material You) colors. */
@Composable
private fun Surface(modifier: GlanceModifier = GlanceModifier, onClick: Action? = null, content: @Composable () -> Unit) {
    var m = modifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
        .background(GlanceTheme.colors.widgetBackground)
    if (onClick != null) m = m.clickable(onClick)
    Box(m) { content() }
}

@Composable
private fun Battery(s: WidgetState, sizeSp: Int) {
    Text(
        s.headline,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = sizeSp.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

@Composable
private fun Caption(text: String, color: androidx.glance.unit.ColorProvider = GlanceTheme.colors.onSurfaceVariant) {
    Text(text, style = TextStyle(color = color, fontSize = 12.sp), maxLines = 1)
}

@Composable
private fun Title(text: String, sizeSp: Int = 15) {
    Text(
        text, maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = sizeSp.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun Render(size: androidx.compose.ui.unit.Dp, s: WidgetState) {
    val render = s.render
    if (render != null) Image(ImageProvider(render), contentDescription = s.name, modifier = GlanceModifier.size(size), contentScale = ContentScale.Fit)
    else Image(
        ImageProvider(R.drawable.ic_w_headphones), contentDescription = s.name, modifier = GlanceModifier.size(size * 0.6f),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
    )
}

/** Three mode buttons; the active one is filled. Disabled look when there is no session. */
@Composable
private fun ModeButtons(s: WidgetState, buttonSize: androidx.compose.ui.unit.Dp, gap: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        s.modes.forEachIndexed { i, mode ->
            if (i > 0) Spacer(GlanceModifier.width(gap))
            val active = s.connected && mode.active
            CircleIconButton(
                imageProvider = ImageProvider(mode.icon),
                contentDescription = mode.label,
                onClick = setMode(mode.id),
                modifier = GlanceModifier.size(buttonSize),
                backgroundColor = if (active) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer,
                contentColor = if (active) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer,
            )
        }
    }
}

/** Mode buttons with a label under each, for the large widget. */
@Composable
private fun LabeledModeButtons(s: WidgetState, fill: GlanceModifier? = null) {
    // With [fill] (a weight from the parent column) the buttons take all the height left over.
    val large = fill != null
    Row((fill ?: GlanceModifier).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        s.modes.forEachIndexed { i, mode ->
            if (i > 0) Spacer(GlanceModifier.width(8.dp))
            val active = s.connected && mode.active
            Column(
                GlanceModifier.defaultWeight().let { if (large) it.fillMaxHeight() else it.height(64.dp) }.cornerRadius(20.dp)
                    .background(if (active) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
                    .clickable(setMode(mode.id)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (active) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
                Image(ImageProvider(mode.icon), null, GlanceModifier.size(if (large) 28.dp else 22.dp), colorFilter = ColorFilter.tint(color))
                if (large) Spacer(GlanceModifier.height(4.dp))
                Text(mode.short, style = TextStyle(color = color, fontSize = if (large) 14.sp else 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
        }
    }
}

// ---- 1×1 → 2×2: battery ----

@Composable
fun BatteryContent(s: WidgetState) {
    val size = LocalSize.current
    val big = size.width >= 100.dp && size.height >= 100.dp
    Surface(onClick = cycleMode()) {
        Column(
            GlanceModifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (s.connected) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
            Image(
                ImageProvider(if (!s.connected) R.drawable.ic_w_disconnected else s.active?.icon ?: R.drawable.ic_w_headphones), null,
                GlanceModifier.size(if (big) 28.dp else 20.dp), colorFilter = ColorFilter.tint(tint),
            )
            Spacer(GlanceModifier.height(if (big) 6.dp else 2.dp))
            Battery(s, if (big) 30 else 18)
            if (big) Caption(if (s.connected) s.active?.label ?: s.status else s.status)
        }
    }
}

// ---- 2×1 → 4×1: status strip ----

@Composable
fun StatusContent(s: WidgetState) {
    val wide = LocalSize.current.width >= 250.dp
    Surface(onClick = openPanel) {
        Row(
            GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Render(if (wide) 44.dp else 36.dp, s)
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                // Narrow: the model number is the useful part of the name, and modes get their short labels.
                Title(if (wide) s.name else s.name.removePrefix("WH-"), if (wide) 15 else 14)
                Caption(
                    if (s.connected) listOfNotNull(s.headline, s.active?.let { if (wide) it.label else it.short }).joinToString(" · ")
                    else s.status
                )
            }
            if (wide && s.modes.isNotEmpty()) ModeButtons(s, 36.dp, 6.dp)
        }
    }
}

// ---- 2×2 → 3×3: controls ----

@Composable
fun ControlsContent(s: WidgetState) {
    val size = LocalSize.current
    val large = size.height >= 200.dp && size.width >= 180.dp
    // Three buttons and two gaps must fit the width inside the 12dp padding.
    val gap = if (large) 10.dp else 6.dp
    val n = s.modes.size.coerceAtLeast(1)
    val button = minOf(if (large) 52.dp else 44.dp, (size.width - 24.dp - gap * (n - 1)) / n)
    Surface {
        Column(
            GlanceModifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(GlanceModifier.fillMaxWidth().clickable(openPanel), verticalAlignment = Alignment.CenterVertically) {
                Render(if (large) 76.dp else 52.dp, s)
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Battery(s, if (large) 30 else 22)
                    Caption(if (!s.connected) s.status else s.active?.let { if (large) it.label else it.short } ?: s.status)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            ModeButtons(s, button, gap)
        }
    }
}

// ---- 4×2 → 5×4: dashboard ----

@Composable
fun DashboardContent(s: WidgetState) {
    val size = LocalSize.current
    // 4×2 compact; 4×3 and taller add the DSEE / Speak-to-Chat chips and a larger header.
    val roomy = size.height >= 180.dp
    Surface {
        Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            Row(GlanceModifier.fillMaxWidth().clickable(openPanel), verticalAlignment = Alignment.CenterVertically) {
                Render(if (roomy) 88.dp else 60.dp, s)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Title(s.name, if (roomy) 19 else 17)
                    Text(
                        s.status, maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = if (roomy) 14.sp else 13.sp),
                    )
                    val activeLabel = s.active?.label
                    if (roomy && s.connected && activeLabel != null) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            activeLabel, maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
                Spacer(GlanceModifier.width(8.dp))
                if (s.battery != null || !s.connected) BatteryRing(s, if (roomy) 64.dp else 50.dp)
            }
            if (roomy) {
                // Chips sit right on top of the mode buttons, which take whatever height is left.
                // Without modes the switches move down to where the mode buttons would be.
                if (s.modes.isEmpty()) Spacer(GlanceModifier.defaultWeight())
                if (s.toggles.isNotEmpty()) {
                    Spacer(GlanceModifier.height(12.dp))
                    Row(GlanceModifier.fillMaxWidth().let { if (s.modes.isEmpty()) it.height(56.dp) else it }) {
                        s.toggles.take(2).forEachIndexed { i, t ->
                            if (i > 0) Spacer(GlanceModifier.width(8.dp))
                            ToggleChip(t.label, s.connected && t.checked, s.connected && t.enabled, toggle(t.id), GlanceModifier.defaultWeight())
                        }
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                if (s.modes.isNotEmpty()) LabeledModeButtons(s, GlanceModifier.defaultWeight())
            } else {
                Spacer(GlanceModifier.defaultWeight())
                if (s.modes.isNotEmpty()) LabeledModeButtons(s)
                else s.toggles.firstOrNull()?.let { t ->
                    ToggleChip(t.label, s.connected && t.checked, s.connected && t.enabled, toggle(t.id), GlanceModifier.fillMaxWidth())
                }
            }
        }
    }
}

/** A compact on/off pill: filled with the primary container when on, outlined-looking surface when off. */
@Composable
private fun ToggleChip(label: String, checked: Boolean, enabled: Boolean, action: Action, modifier: GlanceModifier) {
    val bg = if (checked) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant
    val fg = when {
        !enabled -> GlanceTheme.colors.onSurfaceVariant
        checked -> GlanceTheme.colors.onPrimaryContainer
        else -> GlanceTheme.colors.onSurface
    }
    var m = modifier.height(36.dp).cornerRadius(18.dp).background(bg).padding(horizontal = 12.dp)
    if (enabled) m = m.clickable(action)
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        if (checked) {
            Text("✓ ", style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Text(label, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/**
 * Battery as a ring with the percentage inside. Glance only has an indeterminate circular indicator, so the ring
 * is drawn into a bitmap in the theme's colors.
 */
@Composable
private fun BatteryRing(s: WidgetState, size: androidx.compose.ui.unit.Dp) {
    val context = androidx.glance.LocalContext.current
    val level = s.battery?.takeIf { s.connected }
    val track = GlanceTheme.colors.secondaryContainer.getColor(context).toArgb()
    val progress = GlanceTheme.colors.primary.getColor(context).toArgb()
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(ringBitmap(level ?: 0, track, progress)), contentDescription = level?.let { "Battery $it%" },
            modifier = GlanceModifier.fillMaxSize(),
        )
        Text(
            level?.let { "$it%" } ?: "—", maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = (size.value / 4.6f).sp, fontWeight = FontWeight.Medium),
        )
    }
}

private fun ringBitmap(level: Int, track: Int, progress: Int): android.graphics.Bitmap {
    val px = 192
    val stroke = px * 0.08f
    val bitmap = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val r = android.graphics.RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
    canvas.drawArc(r, 0f, 360f, false, paint.apply { color = track })
    if (level > 0) canvas.drawArc(r, -90f, 360f * level.coerceIn(0, 100) / 100f, false, paint.apply { color = progress })
    return bitmap
}

private fun androidx.compose.ui.graphics.Color.toArgb() = android.graphics.Color.argb(alpha, red, green, blue)


