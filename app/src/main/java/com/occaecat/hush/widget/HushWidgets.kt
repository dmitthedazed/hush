package com.occaecat.hush.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import com.occaecat.hush.HeadphonesController
import kotlinx.coroutines.flow.map

/**
 * Hush home screen widgets, smallest to largest. Each is responsive inside its range, and each gives the widget
 * picker a generated preview ([providePreview], Android 15+) built from the same content with sample data.
 */
abstract class HushWidget(sizes: Set<DpSize>, private val content: @Composable (WidgetState) -> Unit) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(sizes)
    override val previewSizeMode = SizeMode.Responsive(sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val controller = HeadphonesController.get(context)
        provideContent {
            val state by controller.state.map(WidgetState::from).collectAsState(WidgetState.from(controller.state.value))
            GlanceTheme { content(state) }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { GlanceTheme { content(WidgetState.sample) } }
    }
}

class BatteryWidget : HushWidget(setOf(DpSize(57.dp, 57.dp), DpSize(110.dp, 110.dp)), { BatteryContent(it) })
class StatusWidget : HushWidget(setOf(DpSize(130.dp, 57.dp), DpSize(250.dp, 57.dp)), { StatusContent(it) })
class ControlsWidget : HushWidget(setOf(DpSize(130.dp, 130.dp), DpSize(180.dp, 200.dp)), { ControlsContent(it) })
class DashboardWidget : HushWidget(setOf(DpSize(250.dp, 130.dp), DpSize(250.dp, 185.dp), DpSize(250.dp, 260.dp)), { DashboardContent(it) })

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BatteryWidget() }
class StatusWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StatusWidget() }
class ControlsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ControlsWidget() }
class DashboardWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DashboardWidget() }

object HushWidgets {
    private const val TAG = "HushWidgets"
    private val receivers = listOf(
        BatteryWidgetReceiver::class, StatusWidgetReceiver::class, ControlsWidgetReceiver::class, DashboardWidgetReceiver::class,
    )

    /** Re-renders placed widgets whose Glance session has already ended. */
    suspend fun updateAll(context: Context) {
        runCatching {
            BatteryWidget().updateAll(context)
            StatusWidget().updateAll(context)
            ControlsWidget().updateAll(context)
            DashboardWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "widget update failed", it) }
    }

    /**
     * Publishes the generated picker previews once per app version. The API is rate limited (about two calls an
     * hour), so a widget whose call was limited is retried on a later launch.
     */
    suspend fun publishPreviews(context: Context, version: Long) {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val prefs = context.getSharedPreferences("hush", 0)
        val manager = GlanceAppWidgetManager(context)
        for (receiver in receivers) {
            val key = "widget_preview_" + receiver.simpleName
            if (prefs.getLong(key, -1) == version) continue
            val result = runCatching { manager.setWidgetPreviews(receiver) }.getOrElse { Log.w(TAG, "preview failed", it); -1 }
            Log.i(TAG, "preview ${receiver.simpleName}: $result")
            if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) prefs.edit().putLong(key, version).apply()
        }
    }
}
