package com.occaecat.hush.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.occaecat.hush.devices.BatteryPart

/**
 * Brand-neutral pictures of one earbud and the charging case, for the battery gauge of true wireless headphones that
 * have no render of their own. Outlines in the tint of the icon that draws them; null for a single main battery.
 */
val BatteryPart.glyph: ImageVector? get() = when (this) {
    BatteryPart.LEFT -> BatteryGlyphs.left
    BatteryPart.RIGHT -> BatteryGlyphs.right
    BatteryPart.EARBUD_1, BatteryPart.EARBUD_2 -> BatteryGlyphs.earbud
    BatteryPart.CASE -> BatteryGlyphs.case
    BatteryPart.MAIN -> null
}

object BatteryGlyphs {
    val earbud: ImageVector by lazy { glyph("Earbud") { bud() } }
    val right: ImageVector by lazy { glyph("EarbudRight") { bud() } }

    /** The right earbud mirrored across the middle. */
    val left: ImageVector by lazy { glyph("EarbudLeft") { bud(mirror = true) } }

    val case: ImageVector by lazy {
        glyph("EarbudCase") {
            // Body, lid seam and the latch dot.
            moveTo(6.5f, 7f); horizontalLineTo(17.5f)
            arcTo(3f, 3f, 0f, false, true, 20.5f, 10f); verticalLineTo(16.5f)
            arcTo(3f, 3f, 0f, false, true, 17.5f, 19.5f); horizontalLineTo(6.5f)
            arcTo(3f, 3f, 0f, false, true, 3.5f, 16.5f); verticalLineTo(10f)
            arcTo(3f, 3f, 0f, false, true, 6.5f, 7f); close()
            moveTo(3.5f, 11.5f); horizontalLineTo(20.5f)
            moveTo(12f, 15f); verticalLineToRelative(0.01f)
        }
    }

    private fun glyph(name: String, draw: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw,
        ).build()

    // A round head with a stem down its right side (x mirrored for the left bud).
    private fun PathBuilder.bud(mirror: Boolean = false) {
        fun x(v: Float) = if (mirror) 24f - v else v
        val sweep = !mirror
        moveTo(x(11f), 19.25f); lineTo(x(11f), 14.29f)
        arcTo(5.5f, 5.5f, 0f, true, sweep, x(14.5f), 11.29f)
        lineTo(x(14.5f), 19.25f)
        arcTo(1.75f, 1.75f, 0f, false, sweep, x(11f), 19.25f); close()
    }
}
