package com.occaecat.hush.xposed

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.occaecat.hush.DeviceCard
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList

/**
 * libxposed API 102 module, scoped to ColorOS My Devices (com.heytap.mydevices).
 *
 * My Devices already reads third-party device cards from content providers, but only for packages in its built-in
 * allowlist (assets/support/device_app_config.json). The hooks add Hush to that list, then forward changes of
 * [DeviceCard.URI] to the per-package observer My Devices registers, the way a real device app would through
 * the My Devices SDK. Names are resolved by shape where R8 shortened them; checked on 17.4.10 (ColorOS 16.1).
 *
 * - `DeviceAppConfigManager`: the support cache is its only no-arg method returning CopyOnWriteArrayList (`j`);
 *   `c(String)` / `d(String)` are the "is supported" / "supports detail panel" checks.
 * - `AppAgentManager`: `c(String, Wr)` registers the observer, `d(String)` removes it.
 * - The observer interface (`aa.Wr`): `a(id, Boolean, pkg)` removed, `b(id)` added, `c(id)` updated.
 */
class HushModule : XposedModule() {

    private var processName = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageReady(param: PackageReadyParam) {
        trace("package ready ${param.packageName} in $processName")
        if (param.packageName == ACCESSORY && (processName == ACCESSORY || processName == "$ACCESSORY:ui")) {
            runCatching {
                val hook = AccessoryHook(this, param.classLoader, ::trace)
                if (processName == ACCESSORY) hook.installTrigger() else hook.install()
            }
                .onFailure { trace("accessory not hooked: ${Log.getStackTraceString(it)}") }
            return
        }
        if (param.packageName != HOST && param.packageName != SETTINGS) return
        // Card taps are handled in the :cards process; the device list lives in the main one.
        if (param.packageName == HOST && processName == "$HOST:cards") {
            runCatching { routeCardTaps(param.classLoader) }.onFailure { trace("card taps not routed: $it") }
            return
        }
        if (param.packageName == SETTINGS && processName == SETTINGS) {
            runCatching { BluetoothSettingsHook(this, param.classLoader, ::trace).install() }
                .onFailure { trace("bluetooth settings not hooked: ${Log.getStackTraceString(it)}") }
            return
        }
        if (processName != HOST) return
        try {
            install(param.classLoader)
        } catch (t: Throwable) {
            trace("install failed: ${Log.getStackTraceString(t)}")
        }
    }

    private fun install(loader: ClassLoader) {
        val deviceApp = loader.loadClass("com.oplus.mydevices.domain.entities.config.DeviceApp")
        val deletion = loader.loadClass("com.oplus.mydevices.domain.entities.config.DelDeviceMethod")
        val manager = loader.loadClass("com.heytap.mydevices.core.config.DeviceAppConfigManager")
        val agent = loader.loadClass("com.heytap.mydevices.core.agent.AppAgentManager")

        // DeviceApp(app, packageName, minSupportVersion, minOsVersion, associateStart, aboutVisibility,
        //           isSupportDetailPanel, delDeviceMethod)
        val ours = deviceApp.getConstructor(
            String::class.java, String::class.java, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java, deletion,
        ).newInstance("Hush", PACKAGE, 0L, 0, true, true, true, deletion.getField("NONE").get(null))
        val packageOf = deviceApp.getMethod("getPackageName")

        val cache = manager.declaredMethods.single {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 &&
                CopyOnWriteArrayList::class.java.isAssignableFrom(it.returnType)
        }
        after(cache) { _, result ->
            val list = result as? CopyOnWriteArrayList<*> ?: return@after result
            if (list.any { packageOf.invoke(it) == PACKAGE }) return@after result
            // Return a copy: the cache is rebuilt in place on package changes, and ours must survive that.
            CopyOnWriteArrayList<Any?>(list).apply { add(ours) }
        }
        for (m in manager.declaredMethods) {
            if (Modifier.isStatic(m.modifiers) || m.returnType != Boolean::class.javaPrimitiveType) continue
            if (m.parameterTypes.toList() != listOf(String::class.java)) continue
            after(m) { args, result -> if (args[0] == PACKAGE) true else result }
        }

        val register = agent.declaredMethods.single {
            !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java && it.parameterTypes[1].isInterface &&
                it.parameterTypes[1].methods.size == 3
        }
        val observerType = register.parameterTypes[1]
        val removed = observerType.methods.single { it.parameterCount == 3 }
        val (added, updated) = observerType.methods
            .filter { it.parameterTypes.toList() == listOf(String::class.java) }
            .sortedBy { it.name }
            .also { check(it.size == 2) { "observer shape changed" } }
        after(register) { args, result ->
            if (args[0] == PACKAGE) CardBridge.attach(args[1]!!, removed, added, updated, ::trace)
            result
        }
        agent.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) && it.returnType == Void.TYPE &&
                it.parameterTypes.toList() == listOf(String::class.java)
        }.forEach { m -> after(m) { args, result -> if (args[0] == PACKAGE) CardBridge.detach(); result } }

        runCatching { linkBluetoothCard(loader) }.onFailure { trace("bluetooth card not linked: $it") }
        trace("hooked cache=${cache.name} register=${register.name} observer=${observerType.name}")
    }

    /**
     * My Devices hides a headset's plain Bluetooth card when a companion app (Melody, realme Link, Health) serves
     * the same device: DeviceAppScannerImpl puts that app device's id into OplusBluetoothAdapterWrapper's
     * linked-device map, and DeviceAggregation skips Bluetooth devices whose id is in it. Do the same for ours.
     */
    private fun linkBluetoothCard(loader: ClassLoader) {
        val scanner = loader.loadClass("com.heytap.mydevices.core.device.DeviceAppScannerImpl")
        val model = loader.loadClass("com.oplus.mydevices.domain.entities.compat.DeviceModel")
        val info = loader.loadClass("com.oplus.mydevices.domain.entities.device.DeviceInfo")
        val wrapper = loader.loadClass("com.heytap.mydevices.core.bluetooth.OplusBluetoothAdapterWrapper")
        val convert = scanner.declaredMethods.single {
            it.returnType == info && it.parameterTypes.toList() == listOf(model, android.net.Uri::class.java)
        }
        val instance = wrapper.getField("a").get(null)
        val linked = wrapper.getMethod("D")
        val packageOf = info.getMethod("getPackageName")
        val idOf = info.getMethod("getId")
        after(convert) { _, result ->
            if (result != null && packageOf.invoke(result) == PACKAGE) {
                @Suppress("UNCHECKED_CAST")
                (linked.invoke(instance) as MutableMap<Any?, Any?>)[idOf.invoke(result)] = true
            }
            result
        }
    }

    /**
     * A tap on a My Devices card goes through QuickAppCardAction, which looks up a controller by (package, device
     * type) in a fixed registry (`aa.yf`); unknown packages get none and the tap is dropped. For Hush, hand back a
     * controller whose "jump to detail" opens [com.occaecat.hush.DeviceCardPanelActivity].
     */
    private fun routeCardTaps(loader: ClassLoader) {
        val action = loader.loadClass("com.oplus.mydevices.quickapp.action.QuickAppCardAction")
        val registry = action.declaredFields.map { it.type }.first { type ->
            type.declaredMethods.any { it.isLookup() }
        }
        val lookups = registry.declaredMethods.filter { it.isLookup() }
        val controllerType = lookups.first().returnType
        val controller = java.lang.reflect.Proxy.newProxyInstance(loader, arrayOf(controllerType)) { _, method, args ->
            when {
                method.returnType == Map::class.java -> emptyMap<String, List<String>>()
                method.parameterCount == 1 -> { openPanel(args!![0]); null }
                method.name == "toString" -> "HushCardController"
                else -> null
            }
        }
        for (m in lookups) after(m) { args, result -> if (result == null && args[0] == PACKAGE) controller else result }
    }

    private fun Method.isLookup() = !Modifier.isStatic(modifiers) && parameterTypes.toList() ==
        listOf(String::class.java, String::class.java) && returnType.isInterface && returnType.declaredMethods.size == 3

    private fun openPanel(device: Any?) {
        val app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Application
        val id = device?.javaClass?.methods?.firstOrNull { it.name == "getMyDeviceId" }?.invoke(device) as? String
        app.startActivity(
            android.content.Intent("com.oplus.mydevices.ACTION_DEVICE_DETAILED_PANEL")
                .setPackage(PACKAGE)
                .putExtra("device_id", id)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        trace("opened panel for $id")
    }

    /** Framework log plus logcat, so `adb logcat -s HushModule` shows the hook side too. */
    private fun trace(message: String) {
        Log.i(TAG, message)
        log(Log.INFO, TAG, message)
    }

    private fun after(method: Method, block: (List<Any?>, Any?) -> Any?) {
        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            try {
                block(chain.args, result)
            } catch (t: Throwable) {
                trace("hook ${method.name} failed: $t")
                result
            }
        }
    }

    /** Mirrors our provider into My Devices' observer: first row → added, revision change → updated, gone → removed. */
    private object CardBridge {
        private var thread: HandlerThread? = null
        private var observer: ContentObserver? = null
        private var lastId: String? = null
        private var lastRevision = Long.MIN_VALUE

        @Synchronized
        fun attach(target: Any, removed: Method, added: Method, updated: Method, log: (String) -> Unit) {
            detach()
            val app = currentApplication() ?: return log("no application yet")
            val t = HandlerThread("hush-card").also { it.start() }
            val handler = Handler(t.looper)
            fun refresh() {
                try {
                    val data = app.contentResolver.query(DeviceCard.URI, arrayOf("device_id", "device_data"), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) to JSONObject(c.getString(1)) else null }
                    val row = data?.let { it.first to it.second.optLong("revision") }
                    // Builds before 0.3 used another id; drop that card once.
                    if (row != null && lastId == null) runCatching {
                        val mac = app.contentResolver.query(DeviceCard.URI, arrayOf("device_mac"), null, null, null)
                            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        if (mac != null) removed.invoke(target, DeviceCard.legacyDeviceId(mac), false, PACKAGE)
                    }
                    val before = lastId
                    when {
                        row == null && before != null -> removed.invoke(target, before, false, PACKAGE)
                        row != null && row.first != before -> {
                            if (before != null) removed.invoke(target, before, false, PACKAGE)
                            added.invoke(target, row.first)
                        }
                        row != null && row.second != lastRevision -> updated.invoke(target, row.first)
                    }
                    lastId = row?.first
                    lastRevision = row?.second ?: Long.MIN_VALUE
                } catch (e: Exception) {
                    log("refresh failed: $e")
                }
            }
            val o = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = refresh()
            }
            app.contentResolver.registerContentObserver(DeviceCard.URI, false, o)
            thread = t
            observer = o
            handler.post(::refresh)
            log("observer attached")
        }

        @Synchronized
        fun detach() {
            val app = currentApplication()
            observer?.let { app?.contentResolver?.unregisterContentObserver(it) }
            thread?.quitSafely()
            observer = null
            thread = null
            lastId = null
            lastRevision = Long.MIN_VALUE
        }

        private fun currentApplication(): Application? = runCatching {
            Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Application?
        }.getOrNull()
    }

    private companion object {
        const val TAG = "HushModule"
        const val HOST = "com.heytap.mydevices"
        const val SETTINGS = "com.oplus.wirelesssettings"
        const val ACCESSORY = "com.heytap.accessory"
        const val PACKAGE = "com.occaecat.hush"
    }
}
