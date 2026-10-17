package com.occaecat.hush.xposed

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/**
 * The ColorOS "Quick device connect" card (com.heytap.accessory, PantaConnect 16.35) for the headset.
 *
 * Requesting side, run in My Devices (which holds com.oplus.permission.safe.CONNECTIVITY, the permission guarding
 * SceneService): the plugin dialog API, `com.heytap.accessory.plugin.discovery.action.DIALOG` with a
 * `DialogParams`. A card is opened with type 0 and moved to "connected" with type 2. Type 0 cards are discovery
 * cards that PantaConnect closes as soon as their address is connected, so the request names a placeholder
 * device (a new one each time, since shown tags are remembered). HuaweiPods-ColorOS found the same constraints.
 *
 * Showing side, run in com.heytap.accessory ([AccessoryHook]): a package that is not an installed PantaConnect
 * plugin gets its picture from the host's own resources by id, so [IMAGE_ID] is answered with the XM4 render
 * from Hush's APK. The automatic close (`static h(DialogActivity)`) is held back for our card, and its buttons
 * open the Hush panel or close it.
 */
internal object ConnectPopup {
    private const val ACCESSORY = "com.heytap.accessory"
    private const val SCENE_SERVICE = "com.oplus.pantaconnect.pluginmgr.services.SceneService"
    private const val ACTION_DIALOG = "com.heytap.accessory.plugin.discovery.action.DIALOG"
    const val PACKAGE = "com.occaecat.hush"

    /** Broadcast Hush sends to com.heytap.accessory when a session comes up; only Hush holds [PERMISSION]. */
    const val ACTION_SHOW = "com.occaecat.hush.action.SHOW_CONNECT_CARD"
    const val PERMISSION = "com.occaecat.hush.permission.CONNECT_CARD"
    const val EXTRA_TITLE = "title"
    const val EXTRA_STATUS = "status"

    /** Not a real id in the accessory APK (type 0xff is unused); stands for the XM4 render. */
    const val IMAGE_ID = 0x7fff7a11

    fun show(context: Context, title: String, status: String, trace: (String) -> Unit) {
        runCatching {
            val loader = context.createPackageContext(
                ACCESSORY, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            ).classLoader
            val paramsClass = loader.loadClass("com.heytap.accessory.plugin.discovery.DialogParams")
            val params = paramsClass.getDeclaredConstructor().newInstance()
            fun set(name: String, value: String) = paramsClass.getMethod(name, String::class.java).invoke(params, value)
            set("setPackageName", PACKAGE)
            set("setTitle", title)
            set("setContinueButton", "Controls")
            set("setNotificationTile", title)
            set("setNotificationContent", status)
            paramsClass.getMethod("setHideCancelButton", Boolean::class.javaPrimitiveType).invoke(params, true)
            val drawableClass = loader.loadClass("com.heytap.accessory.plugin.discovery.DialogParams\$DrawableDes")
            val drawable = drawableClass.getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType, String::class.java)
                .newInstance(status, IMAGE_ID, "")
            paramsClass.getMethod("addDrawableDes", drawableClass).invoke(params, drawable)
            // PantaConnect remembers the tag (address) of every card it showed and filters repeats, so each
            // connection gets a fresh locally administered placeholder address.
            val random = java.util.Random()
            val address = "02:" + (1..5).joinToString(":") { "%02X".format(random.nextInt(256)) }
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
            fun request(type: Int) = Intent(ACTION_DIALOG).apply {
                component = ComponentName(ACCESSORY, SCENE_SERVICE)
                putExtra("dialog_type", type)
                putExtra("sdk_version", 3)
                putExtra("bluetooth_device", device)
                putExtra("dialog_params", params as Parcelable)
            }
            context.startService(request(0))
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { context.startService(request(2)) }.onFailure { trace("popup update failed: $it") }
            }, 120)
            trace("popup requested: $title / $status")
        }.onFailure { trace("popup failed: $it") }
    }
}

internal class AccessoryHook(
    private val module: XposedModule,
    private val loader: ClassLoader,
    private val trace: (String) -> Unit,
) {
    private val dialogClass = loader.loadClass("com.oplus.pantaconnect.appcore.devicediscovery.ui.DialogActivity")
    private var image: Drawable? = null

    /** Main process: receives Hush's request and asks SceneService for the card. */
    fun installTrigger() = registerTrigger()

    /** `:ui` process, where DialogActivity runs: picture, close guard and buttons. */
    fun install() {
        for (m in Resources::class.java.declaredMethods.filter { it.name == "getDrawable" && it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType }) {
            hook(m) { chain ->
                if (chain.getArg(0) as? Int == ConnectPopup.IMAGE_ID) render(chain.thisObject as Resources) ?: chain.proceed() else chain.proceed()
            }
        }
        val autoClose = dialogClass.declaredMethods.single {
            Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterTypes.toList() == listOf(dialogClass)
        }
        hook(autoClose) { chain ->
            val activity = chain.getArg(0) as Activity
            // PantaConnect closes a type-0 card as soon as it sees the device connected; skip that first close
            // once (as HuaweiPods-ColorOS does) and let its later timeout close the card normally.
            if (isOurs(activity) && !userClosing.contains(activity) && heldOnce.add(activity)) {
                trace("kept our card open")
                null
            } else chain.proceed()
        }
        hook(View::class.java.getDeclaredMethod("performClick")) { chain ->
            val view = chain.thisObject as View
            val activity = view.context.activity()
            val id = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
            if (activity != null && activity.javaClass == dialogClass && isOurs(activity) &&
                (id == "button_single_big" || id == "button_close")
            ) {
                if (id == "button_single_big") {
                    activity.startActivity(
                        Intent("com.oplus.mydevices.ACTION_DEVICE_DETAILED_PANEL").setPackage(ConnectPopup.PACKAGE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                userClosing.add(activity)
                activity.finish()
                true
            } else chain.proceed()
        }
        trace("accessory hooked autoClose=${autoClose.name}")
    }

    /**
     * The request side runs here, in the process that owns SceneService: My Devices, which also holds the
     * permission, is frozen in the background and misses Hush's changes.
     */
    private fun registerTrigger() {
        val app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
        if (app == null) {
            module.hook(android.app.Application::class.java.getDeclaredMethod("onCreate")).intercept { chain ->
                chain.proceed().also { listen(chain.thisObject as Context) }
            }
        } else listen(app)
    }

    private fun listen(context: Context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                ConnectPopup.show(
                    context, intent.getStringExtra(ConnectPopup.EXTRA_TITLE) ?: "Headphones",
                    intent.getStringExtra(ConnectPopup.EXTRA_STATUS) ?: "Connected", trace,
                )
            }
        }
        context.registerReceiver(
            receiver, android.content.IntentFilter(ConnectPopup.ACTION_SHOW), ConnectPopup.PERMISSION, null,
            Context.RECEIVER_EXPORTED,
        )
        trace("connect card trigger registered")
    }

    private val heldOnce = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())

    private val userClosing = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())

    private fun hook(method: java.lang.reflect.Method, body: (XposedInterface.Chain) -> Any?) {
        module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain -> body(chain) }
    }

    private fun render(resources: Resources): Drawable? = image ?: runCatching {
        val app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Context
        val hush = app.createPackageContext(ConnectPopup.PACKAGE, 0).resources
        val id = hush.getIdentifier("card_xm4", "drawable", ConnectPopup.PACKAGE)
        android.graphics.drawable.BitmapDrawable(resources, android.graphics.BitmapFactory.decodeResource(hush, id))
    }.onFailure { trace("popup image failed: $it") }.getOrNull()?.also { image = it }

    /** The card's serialized view data (protobuf bytes) carries our package name as the plugin package. */
    private fun isOurs(activity: Activity): Boolean = runCatching {
        val bytes = activity.intent.getBundleExtra("intent_bundle")?.getByteArray("base_view_data") ?: return false
        String(bytes, Charsets.ISO_8859_1).contains(ConnectPopup.PACKAGE)
    }.getOrDefault(false)

    private fun Context.activity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
