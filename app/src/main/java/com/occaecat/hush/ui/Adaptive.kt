package com.occaecat.hush.ui

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * How the app lays itself out in the current window. Computed once in [HushShell] from the window size and the
 * fold posture, read by the tab pages through [LocalLayout].
 */
data class HushLayout(
    /** Navigation rail instead of the bottom bar. */
    val rail: Boolean = false,
    /** Content columns of the tab pages. */
    val columns: Int = 1,
    /** Short window (landscape phone, half of a fold): the app bar stays small. */
    val compactHeight: Boolean = false,
    /** Phone-short window (small phones): the app bar starts medium instead of large. */
    val shortHeight: Boolean = false,
    /** Very narrow window (small phones, split screen): heroes shrink. */
    val narrow: Boolean = false,
    /** Half-opened foldable lying flat: status above the hinge, controls below. Height of the top half. */
    val tabletopTop: Dp? = null,
    /** Half-opened foldable held like a book: the hinge, in window pixels, that the two columns sit either side of. */
    val bookHinge: IntRange? = null,
)

val LocalLayout = compositionLocalOf { HushLayout() }

@Composable
fun rememberHushLayout(): HushLayout {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val w = config.screenWidthDp
    val h = config.screenHeightDp
    val posture: Posture = currentWindowAdaptiveInfo().windowPosture

    // A half-opened fold. A fully opened one reports a flat hinge and is laid out like a tablet.
    val hinge = posture.hingeList.firstOrNull { !it.isFlat }
    val tabletop = hinge != null && !hinge.isVertical
    val book = hinge != null && hinge.isVertical

    if (tabletop) {
        val top = with(density) { hinge!!.bounds.top.toDp() }
        return HushLayout(rail = false, columns = if (w >= 840) 2 else 1, compactHeight = true, tabletopTop = top)
    }
    if (book) {
        // Two columns, one on each side of the hinge; the rail keeps navigation off the fold.
        val b = hinge!!.bounds
        return HushLayout(rail = true, columns = 2, bookHinge = b.left.toInt()..b.right.toInt())
    }
    val rail = w >= 600
    // Two columns on wide windows, and on landscape phones where one column would leave a short, wide strip.
    val columns = if (w >= 840 || (rail && h < 480)) 2 else 1
    val columnWidth = (w - (if (rail) 96 else 0) - 32 - 16 * (columns - 1)) / columns
    return HushLayout(
        rail = rail,
        columns = columns,
        compactHeight = h < 480,
        shortHeight = h < 700,
        narrow = columnWidth < 340,
    )
}

/** Widest a single content column gets. */
val ColumnMaxWidth = 600.dp

/**
 * Two columns that meet at a hinge: the left one ends [margin] before it, the right one starts [margin] after it.
 * [hingeStart]/[hingeEnd] are relative to the start of the grid's content area (after its start padding).
 */
class HingeCells(private val hingeStart: Int, private val hingeEnd: Int) :
    androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells {
    override fun androidx.compose.ui.unit.Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): IntArray {
        val left = (hingeStart - (spacing - (hingeEnd - hingeStart)) / 2).coerceIn(0, availableSize - spacing)
        return intArrayOf(left, availableSize - left - spacing)
    }

    override fun equals(other: Any?) = other is HingeCells && other.hingeStart == hingeStart && other.hingeEnd == hingeEnd
    override fun hashCode() = 31 * hingeStart + hingeEnd
}
