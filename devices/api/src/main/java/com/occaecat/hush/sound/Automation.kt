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
import com.occaecat.hush.scenes.Activity
import com.occaecat.hush.scenes.DeviceScenes
import com.occaecat.hush.scenes.Place
import com.occaecat.hush.scenes.SceneAction
import com.occaecat.hush.scenes.SceneStore
import com.occaecat.hush.scenes.SceneTarget
import com.occaecat.hush.scenes.Scenes
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
import kotlinx.coroutines.tasks.await

/**
 * Adaptive Sound Control (Sound Connect's activity + My Places switching) and per-app profiles, done on the phone for
 * any device: Play services reports activity transitions and geofence crossings, a notification listener reports
 * which app is playing. Each connected device resolves its own rules — app over place over activity, field by field —
 * and only changes are sent, so a manual change sticks until the situation changes.
 */
object SoundAutomation {
    private const val TAG = "HushAuto"
    private const val ACTION_ACTIVITY = "com.occaecat.hush.ACTIVITY_TRANSITION"
    private const val ACTION_GEOFENCE = "com.occaecat.hush.GEOFENCE"

    data class Inputs(val activity: Activity? = null, val inside: Set<String> = emptySet(), val playing: String? = null)

    private val _inputs = MutableStateFlow(Inputs())
    /** What the phone sees now, for the pages. */
    val inputs: StateFlow<Inputs> = _inputs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private lateinit var app: Context

    /** From the app's start, with the connected devices; safe to call again. */
    @OptIn(FlowPreview::class)
    fun start(context: Context, targets: StateFlow<List<SceneTarget>>) {
        if (started) return
        started = true
        app = context.applicationContext
        SceneStore.init(app)
        restoreInputs()

        // Keep Play services registrations in line with the settings: on while any device uses them.
        scope.launch {
            SceneStore.config.map { c -> c.devices.values.any { it.ascEnabled } to c.places }.distinctUntilChanged().collect { (on, places) ->
                registerActivity(on)
                registerPlaces(if (on) places else emptyList())
            }
        }

        val applied = mutableMapOf<String, SceneAction>()
        val baseline = mutableMapOf<String, Any?>() // a device's EQ before automation first changed it
        scope.launch {
            combine(SceneStore.config, _inputs, targets) { c, i, t -> Triple(c, i, t) }
                .debounce(1_500)
                .collect { (config, i, connected) ->
                    applied.keys.retainAll(connected.map { it.key }.toSet())
                    baseline.keys.retainAll(connected.map { it.key }.toSet())
                    connected.forEach { t ->
                        val c = t.controller
                        val action = resolve(SceneStore.device(config, t.key, c), i).first
                        val last = applied[t.key]
                        if (action.noise != null && (last?.noise != action.noise || last.level != action.level || last.voiceFocus != action.voiceFocus))
                            Scenes.perform(c, SceneAction(action.noise, action.level, action.voiceFocus))
                        // EQ: apply a new choice; when the last choice goes away, put the device's EQ back.
                        if (action.eq != last?.eq) {
                            if (action.eq != null) {
                                if (last?.eq == null) baseline[t.key] = c.equalizer?.snapshot()
                                Scenes.performEq(c, action.eq)
                            } else if (t.key in baseline) {
                                c.equalizer?.restore(baseline.remove(t.key))
                            }
                        }
                        applied[t.key] = action
                    }
                }
        }
    }

    /** What [d]'s rules want under [i], and which triggers it came from (for the pages). */
    fun resolve(d: DeviceScenes, i: Inputs, places: List<Place> = SceneStore.config.value.places): Pair<SceneAction, List<String>> {
        val layers = buildList {
            if (d.appsEnabled) d.apps.firstOrNull { it.pkg == i.playing }?.let { add(it.label to it.action) }
            if (d.ascEnabled) {
                places.firstOrNull { it.id in i.inside && d.places[it.id] != null }?.let { add(it.name to d.places.getValue(it.id)) }
                i.activity?.let { a -> d.activities[a]?.let { add(a.label to it) } }
            }
        }
        val nc = layers.firstOrNull { it.second.noise != null }
        val eq = layers.firstOrNull { it.second.eq != null }
        val action = SceneAction(noise = nc?.second?.noise, level = nc?.second?.level, voiceFocus = nc?.second?.voiceFocus, eq = eq?.second?.eq)
        return action to listOfNotNull(nc?.first, eq?.first).distinct()
    }

    // ---- inputs ----

    fun onActivity(a: Activity) = setInputs { it.copy(activity = a) }
    fun onGeofence(entered: Collection<String>, exited: Collection<String>) =
        setInputs { it.copy(inside = it.inside - exited.toSet() + entered) }
    fun onPlaying(pkg: String?) = _inputs.update { it.copy(playing = pkg) }

    private fun setInputs(change: (Inputs) -> Inputs) {
        _inputs.update(change)
        if (::app.isInitialized) {
            val i = _inputs.value
            app.getSharedPreferences("asc", Context.MODE_PRIVATE).edit()
                .putString("activity", i.activity?.name).putStringSet("inside", i.inside).apply()
        }
    }

    private fun restoreInputs() {
        val p = app.getSharedPreferences("asc", Context.MODE_PRIVATE)
        _inputs.update {
            it.copy(
                activity = p.getString("activity", null)?.let { n -> runCatching { Activity.valueOf(n) }.getOrNull() },
                inside = p.getStringSet("inside", emptySet()).orEmpty(),
            )
        }
    }

    /** Where the phone is now, for "Add current location"; null without permission or a fix. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(c: Context): android.location.Location? = runCatching {
        LocationServices.getFusedLocationProviderClient(c)
            .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                com.google.android.gms.tasks.CancellationTokenSource().token)
            .await()
    }.getOrNull()

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
        val c = SceneStore.config.value
        val on = c.devices.values.any { it.ascEnabled }
        registerActivity(on)
        registerPlaces(if (on) c.places else emptyList())
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
        // A wake-up from Play services: the app's start has begun the engine, so its saved state can't overwrite this.
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
