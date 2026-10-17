package com.occaecat.hush.xposed

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.occaecat.hush.DeviceCard
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * Native rows on the headset's page in ColorOS Bluetooth settings (com.oplus.wirelesssettings).
 *
 * That page already shows the Fast Pair items Google serves as a slice (METADATA_ENHANCED_SETTINGS_UI_URI) through
 * `com.android.settings.bluetooth.BlockingPrefWithSliceController`, which only turns slice rows into links. When
 * the slice belongs to our headset, real COUI switches are added in front of those rows: Noise cancelling and
 * Ambient sound (both off = Off), plus a row that opens the Hush panel. State comes from and goes to Hush's
 * provider ([DeviceCard.METHOD_STATE], [DeviceCard.METHOD_MODE]).
 */
internal class BluetoothSettingsHook(
    private val module: XposedModule,
    private val loader: ClassLoader,
    private val trace: (String) -> Unit,
) {
    private val controllerClass = loader.loadClass("com.android.settings.bluetooth.BlockingPrefWithSliceController")
    private val preferenceClass = loader.loadClass("androidx.preference.Preference")
    private val switchClass = loader.loadClass("com.coui.appcompat.preference.COUISwitchPreference")
    private val plainClass = loader.loadClass("com.coui.appcompat.preference.COUIPreference")
    private val setKey = preferenceClass.getMethod("setKey", String::class.java)
    private val setTitle = preferenceClass.getMethod("setTitle", CharSequence::class.java)
    private val setSummary = preferenceClass.getMethod("setSummary", CharSequence::class.java)
    private val setOrder = preferenceClass.getMethod("setOrder", Int::class.javaPrimitiveType)
    private val setIntent = preferenceClass.getMethod("setIntent", Intent::class.java)
    private val setChangeListener = preferenceClass.methods.single { it.name == "setOnPreferenceChangeListener" }
    private val setChecked = switchClass.getMethod("setChecked", Boolean::class.javaPrimitiveType)
    private val setEnabled = preferenceClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType)

    /** Switches of the device page last shown, refreshed when Hush publishes a change. */
    private var shown: Rows? = null

    private class Rows(val context: Context, val nc: Any, val ambient: Any, val panel: Any)

    fun install() {
        val parse = controllerClass.getDeclaredMethod("parseSliceToPreferenceList", loader.loadClass("androidx.slice.Slice"))
        val uriField = controllerClass.getDeclaredField("mUri").apply { isAccessible = true }
        val categoryField = controllerClass.getDeclaredField("mPreferenceCategory").apply { isAccessible = true }
        val contextField = loader.loadClass("com.android.settingslib.core.AbstractPreferenceController")
            .getDeclaredField("mContext").apply { isAccessible = true }
        val addPreference = categoryField.type.getMethod("addPreference", preferenceClass)

        after(parse) { result ->
            @Suppress("UNCHECKED_CAST")
            val list = result as? MutableList<Any?> ?: return@after result
            val controller = currentController ?: return@after result
            val uri = uriField.get(controller) as? Uri ?: return@after result
            val context = contextField.get(controller) as Context
            val state = state(context) ?: return@after result
            val address = state.getString("address") ?: return@after result
            if (!uri.toString().contains(Uri.encode(address), ignoreCase = true) && !uri.toString().contains(address, true)) {
                return@after result
            }
            val category = categoryField.get(controller) ?: return@after result
            val rows = rowsFor(context, category, addPreference)
            render(rows, state)
            ArrayList<Any?>(list).apply { addAll(0, listOf(rows.nc, rows.ambient, rows.panel)) }
        }
        trace("bluetooth settings hooked")
    }

    /** The controller whose parseSliceToPreferenceList is running (it is a private instance method). */
    private var currentController: Any? = null

    private fun after(method: Method, block: (Any?) -> Any?) {
        module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
            currentController = chain.thisObject
            val result = chain.proceed()
            try {
                block(result)
            } catch (t: Throwable) {
                trace("bluetooth settings hook failed: $t")
                result
            } finally {
                currentController = null
            }
        }
    }

    private fun rowsFor(context: Context, category: Any, add: Method): Rows {
        shown?.takeIf { it.context === context }?.let { return it }
        val nc = switchClass.getConstructor(Context::class.java).newInstance(context)
        val ambient = switchClass.getConstructor(Context::class.java).newInstance(context)
        val panel = plainClass.getConstructor(Context::class.java).newInstance(context)
        setKey(nc, "hush_nc"); setTitle(nc, "Noise cancelling"); setOrder(nc, -30)
        setKey(ambient, "hush_ambient"); setTitle(ambient, "Ambient sound"); setOrder(ambient, -20)
        setKey(panel, "hush_panel"); setTitle(panel, "Headphone controls"); setOrder(panel, -10)
        setSummary(panel, "Noise modes, ambient level, EQ")
        setIntent(panel, Intent("com.oplus.mydevices.ACTION_DEVICE_DETAILED_PANEL").setPackage(PACKAGE))
        setChangeListener(nc, listener { on -> mode(context, if (on) "NOISE_CANCELLING" else "OFF") })
        setChangeListener(ambient, listener { on -> mode(context, if (on) "AMBIENT" else "OFF") })
        for (p in listOf(nc, ambient, panel)) add(category, p)
        val rows = Rows(context, nc, ambient, panel)
        shown = rows
        context.contentResolver.registerContentObserver(DeviceCard.URI, false, object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = shown?.takeIf { it === rows } ?: return context.contentResolver.unregisterContentObserver(this)
                state(context)?.let { render(current, it) }
            }
        })
        return rows
    }

    private fun render(rows: Rows, state: Bundle) {
        val connected = state.getBoolean("connected")
        val mode = state.getString("mode")
        setChecked(rows.nc, connected && mode == "NOISE_CANCELLING")
        setChecked(rows.ambient, connected && mode == "AMBIENT")
        setEnabled(rows.nc, connected)
        setEnabled(rows.ambient, connected)
        setSummary(rows.nc, if (connected) "Block outside noise" else "Open Hush to connect")
        setSummary(rows.ambient, if (connected) "Let surroundings in" else "")
    }

    private fun listener(onChange: (Boolean) -> Unit): Any {
        val type = setChangeListener.parameterTypes[0]
        return Proxy.newProxyInstance(loader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "toString" -> "HushSwitchListener"
                "hashCode" -> 0
                "equals" -> false
                else -> {
                    (args?.getOrNull(1) as? Boolean)?.let(onChange)
                    true
                }
            }
        }
    }

    private fun state(context: Context): Bundle? = runCatching {
        context.contentResolver.call(DeviceCard.URI, DeviceCard.METHOD_STATE, null, null)
    }.onFailure { trace("state unavailable: $it") }.getOrNull()

    private fun mode(context: Context, mode: String) {
        runCatching {
            context.contentResolver.call(DeviceCard.URI, DeviceCard.METHOD_MODE, null, Bundle().apply { putString("extra", mode) })
        }.onFailure { trace("mode change failed: $it") }
    }

    private companion object {
        const val PACKAGE = "com.occaecat.hush"
    }
}
