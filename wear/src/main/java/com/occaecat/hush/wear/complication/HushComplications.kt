package com.occaecat.hush.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.occaecat.hush.Phase
import com.occaecat.hush.link.Snapshot
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.wear.R
import com.occaecat.hush.wear.hub.ActionReceiver
import com.occaecat.hush.wear.hub.HushHub
import com.occaecat.hush.wear.hub.WearLabels
import com.occaecat.hush.wear.ui.MainActivity

private fun text(s: String) = PlainComplicationText.Builder(s).build()

private fun mono(context: Context, res: Int) = MonochromaticImage.Builder(Icon.createWithResource(context, res)).build()

private fun openApp(context: Context) = PendingIntent.getActivity(
    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
)

private suspend fun current(context: Context): Snapshot {
    val hub = HushHub.get(context)
    if (hub.state.value.route == com.occaecat.hush.wear.hub.Route.PHONE) hub.refreshPhone()
    return hub.state.value.snapshot
}

/** Headset battery: a ring (ranged value), "85%" (short/long text) or a headphones glyph that dims when away. */
class BatteryComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, current(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Snapshot(phase = Phase.CONNECTED, name = "WH-1000XM4", battery = 80, mode = AmbientMode.NOISE_CANCELLING))

    private fun build(type: ComplicationType, s: Snapshot): ComplicationData? {
        val level = s.battery.takeIf { s.connected }
        val label = level?.let { "$it%" } ?: "--"
        val description = text(level?.let { "Headphones battery $it percent" } ?: "Headphones not connected")
        val icon = mono(this, if (level != null) R.drawable.ic_xm4 else R.drawable.ic_w_disconnected)
        val tap = openApp(this)
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder((level ?: 0).toFloat(), 0f, 100f, description)
                    .setText(text(label))
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(label), description)
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text(if (level != null) "$label · ${WearLabels.mode(s.mode)}" else "Not connected"), description,
                )
                    .setTitle(text(WearLabels.name(s)))
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(icon, description).setTapAction(tap).build()
            else -> NoDataComplicationData()
        }
    }
}

/** Noise mode as a one-tap toggle on the watch face: tap cycles NC → Ambient → Off, like the earcup button. */
class NoiseComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, current(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Snapshot(phase = Phase.CONNECTED, mode = AmbientMode.NOISE_CANCELLING))

    private fun build(type: ComplicationType, s: Snapshot): ComplicationData? {
        val mode = s.mode.takeIf { s.connected }
        val res = WearLabels.modeIcon(mode)
        val description = text(if (mode != null) WearLabels.mode(mode) + ", tap to switch" else "Headphones not connected")
        // Disconnected: the tap opens Hush instead of cycling into nothing.
        val tap = if (mode != null) ActionReceiver.pending(this, ActionReceiver.ACTION_CYCLE) else openApp(this)
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(WearLabels.modeShort(mode)), description)
                    .setMonochromaticImage(mono(this, res))
                    .setTapAction(tap)
                    .build()
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(mono(this, res), description).setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(Icon.createWithResource(this, res), SmallImageType.ICON).build(), description,
                ).setTapAction(tap).build()
            else -> NoDataComplicationData()
        }
    }
}
