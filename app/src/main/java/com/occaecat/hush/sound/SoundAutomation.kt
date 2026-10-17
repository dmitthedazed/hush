package com.occaecat.hush.sound

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.EqState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Adaptive Sound Control (Sound Connect's activity + My Places switching) and per-app profiles, done on the
 * phone: Play services reports activity transitions and geofence crossings, a notification listener reports
 * which app is playing. The three are merged field by field — app over place over activity — and only
 * changes are sent to the headset, so a manual change sticks until the situation changes.
 */
object SoundAutomation {
    private const val TAG = "HushAuto"
    private const val ACTION_ACTIVITY = "com.occaecat.hush.ACTIVITY_TRANSITION"
    private const val ACTION_GEOFENCE = "com.occaecat.hush.GEOFENCE"

    data class Inputs(val activity: Activity? = null, val inside: Set<String> = emptySet(), val playing: String? = null)

    /** What is driving the sound right now, for the UI. */
    data class Status(val inputs: Inputs = Inputs(), val action: SoundAction = SoundAction(), val sources: List<String> = emptyList())

    private val inputs = MutableStateFlow(Inputs())
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private lateinit var app: Context

    /** Called by HushService; safe to call again. */
    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        SoundStore.init(app)
        restoreInputs()
        val controller = HeadphonesController.get(app)

        // Keep Play services registrations in line with the settings.
        scope.launch {
            SoundStore.config.map { it.ascEnabled to it.places }.distinctUntilChanged().collect { (on, places) ->
                registerActivity(on)
                registerPlaces(if (on) places else emptyList())
            }
        }

        val ready = controller.state.map { it.phase == Phase.CONNECTED && it.ncAsm != null }.distinctUntilChanged()
        var applied: SoundAction? = null
        var baseline: EqState? = null // the EQ before automation first changed it, restored when no trigger wants one
        scope.launch {
            combine(SoundStore.config, inputs, ready) { c, i, r -> Triple(c, i, r) }
                .debounce(1_500)
                .collect { (config, i, isReady) ->
                    val (action, sources) = resolve(config, i)
                    _status.value = Status(i, action, sources)
                    if (!isReady) { applied = null; return@collect }
                    val last = applied
                    // Noise control: only when the wanted mode/level/voice actually changed.
                    if (action.mode != null && (last?.mode != action.mode || last.ambientLevel != action.ambientLevel || last.voiceFocus != action.voiceFocus)) {
                        if (action.mode == AmbientMode.AMBIENT) controller.setNoiseControl(action.mode, action.ambientLevel, action.voiceFocus)
                        else controller.setNoiseControl(action.mode)
                    }
                    // EQ: apply a new choice; when the last choice goes away, put the user's EQ back.
                    if (action.eq != last?.eq) {
                        val eq = action.eq
                        if (eq != null) {
                            if (last?.eq == null) baseline = controller.state.value.eq
                            applyEq(controller, config, eq)
                        } else {
                            baseline?.let { b ->
                                if (b.presetId in HeadphonesController.WRITABLE_PRESETS && b.bands.isNotEmpty()) controller.writeEqPreset(b.presetId, b.bands)
                                else controller.selectEqPreset(b.presetId)
                            }
                            baseline = null
                        }
                    }
                    applied = action
                }
        }
    }

    private fun resolve(c: SoundConfig, i: Inputs): Pair<SoundAction, List<String>> {
        val layers = buildList {
            if (c.appsEnabled) c.appRules.firstOrNull { it.pkg == i.playing }?.let { add(it.label to it.action) }
            if (c.ascEnabled) {
                c.places.firstOrNull { it.id in i.inside }?.let { add(it.name to it.action) }
                i.activity?.let { a -> c.activityActions[a]?.let { add(a.label to it) } }
            }
        }
        val nc = layers.firstOrNull { it.second.mode != null }
        val eq = layers.firstOrNull { it.second.eq != null }
        val action = SoundAction(
            mode = nc?.second?.mode,
            ambientLevel = nc?.second?.ambientLevel ?: 10,
            voiceFocus = nc?.second?.voiceFocus ?: false,
            eq = eq?.second?.eq,
        )
        return action to listOfNotNull(nc?.first, eq?.first).distinct()
    }

    fun applyEq(controller: HeadphonesController, c: SoundConfig, eq: EqChoice) {
        when (eq) {
            is EqChoice.Preset -> controller.selectEqPreset(eq.id)
            is EqChoice.Library -> c.library.firstOrNull { it.id == eq.id }?.let {
                controller.writeEqPreset(HeadphonesController.CUSTOM_PRESET, it.levels)
            }
        }
    }

    // ---- inputs ----

    fun onActivity(a: Activity) = setInputs { it.copy(activity = a) }
    fun onGeofence(entered: Collection<String>, exited: Collection<String>) =
        setInputs { it.copy(inside = it.inside - exited.toSet() + entered) }
    fun onPlaying(pkg: String?) = inputs.update { it.copy(playing = pkg) }

    private fun setInputs(change: (Inputs) -> Inputs) {
        inputs.update(change)
        if (::app.isInitialized) {
            val i = inputs.value
            app.getSharedPreferences("asc", Context.MODE_PRIVATE).edit()
                .putString("activity", i.activity?.name).putStringSet("inside", i.inside).apply()
        }
    }

    private fun restoreInputs() {
        val p = app.getSharedPreferences("asc", Context.MODE_PRIVATE)
        inputs.update {
            it.copy(
                activity = p.getString("activity", null)?.let { n -> runCatching { Activity.valueOf(n) }.getOrNull() },
                inside = p.getStringSet("inside", emptySet()).orEmpty(),
            )
        }
    }

    // ---- permissions ----

    fun hasActivityPermission(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationAccess(c: Context) = c.packageName in NotificationManagerCompat.getEnabledListenerPackages(c)

    /** Re-registers after a permission was granted from the UI. */
    fun refresh() {
        if (!::app.isInitialized) return
        val c = SoundStore.config.value
        registerActivity(c.ascEnabled)
        registerPlaces(if (c.ascEnabled) c.places else emptyList())
    }

    // ---- Play services ----

    private fun pending(action: String, code: Int) = PendingIntent.getBroadcast(
        app, code, Intent(app, AutomationReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    @SuppressLint("MissingPermission")
    private fun registerActivity(on: Boolean) {
        val client = ActivityRecognition.getClient(app)
        val pi = pending(ACTION_ACTIVITY, 1)
        client.removeActivityTransitionUpdates(pi)
        if (!on || !hasActivityPermission(app)) return
        val types = listOf(
            DetectedActivity.STILL, DetectedActivity.WALKING, DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE, DetectedActivity.IN_VEHICLE,
        )
        val request = ActivityTransitionRequest(types.map {
            ActivityTransition.Builder().setActivityType(it).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build()
        })
        client.requestActivityTransitionUpdates(request, pi).addOnFailureListener { Log.w(TAG, "activity transitions", it) }
    }

    @SuppressLint("MissingPermission")
    private fun registerPlaces(places: List<Place>) {
        val client = LocationServices.getGeofencingClient(app)
        val pi = pending(ACTION_GEOFENCE, 2)
        client.removeGeofences(pi)
        // Places that were removed can't still count as "inside".
        setInputs { i -> i.copy(inside = i.inside.filter { id -> places.any { it.id == id } }.toSet()) }
        if (places.isEmpty() || !hasLocationPermission(app) || !hasBackgroundLocation(app)) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(places.take(90).map {
                Geofence.Builder().setRequestId(it.id).setCircularRegion(it.lat, it.lng, it.radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            })
            .build()
        client.addGeofences(request, pi).addOnFailureListener { Log.w(TAG, "geofences", it) }
    }

    internal fun handle(context: Context, intent: Intent) {
        // A wake-up from Play services: start the engine first so its saved state doesn't overwrite this event.
        start(context)
        when (intent.action) {
            ACTION_ACTIVITY -> ActivityTransitionResult.extractResult(intent)?.transitionEvents?.lastOrNull()?.let { e ->
                val a = when (e.activityType) {
                    DetectedActivity.STILL -> Activity.STILL
                    DetectedActivity.WALKING -> Activity.WALKING
                    DetectedActivity.RUNNING, DetectedActivity.ON_BICYCLE -> Activity.RUNNING
                    DetectedActivity.IN_VEHICLE -> Activity.VEHICLE
                    else -> null
                }
                a?.let(::onActivity)
            }
            ACTION_GEOFENCE -> GeofencingEvent.fromIntent(intent)?.takeUnless { it.hasError() }?.let { e ->
                val ids = e.triggeringGeofences.orEmpty().map { it.requestId }
                when (e.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> onGeofence(ids, emptyList())
                    Geofence.GEOFENCE_TRANSITION_EXIT -> onGeofence(emptyList(), ids)
                }
            }
        }
        com.occaecat.hush.HushService.start(context)
    }
}

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SoundAutomation.handle(context, intent)
}

/**
 * Notification access is the only way to see other apps' media sessions. Reports the package of the session
 * that is playing (the most recently started one when several are) to the automation, and that session's
 * metadata to [NowPlaying] (the most recent paused one when nothing plays).
 */
class MediaWatcher : NotificationListenerService() {
    private var sessions: MediaSessionManager? = null
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java)
        sessions = m
        val me = ComponentName(this, MediaWatcher::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(changed, me)
            track(m.getActiveSessions(me))
        }.onFailure { Log.w("HushMedia", "media sessions", it) }
    }

    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(changed)
        callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
        SoundAutomation.onPlaying(null)
        NowPlaying.update(this, null)
    }

    private fun track(controllers: List<MediaController>) {
        callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
        controllers.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = report()
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = report()
                override fun onSessionDestroyed() = report()
            }
            c.registerCallback(cb)
            callbacks[c] = cb
        }
        report()
    }

    private fun report() {
        val playing = callbacks.keys.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        SoundAutomation.onPlaying(playing?.packageName)
        // getActiveSessions lists the most recently active first.
        NowPlaying.update(this, playing ?: callbacks.keys.firstOrNull { it.metadata != null })
    }
}
