package com.occaecat.hush.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.link.Command
import com.occaecat.hush.link.HushLink
import com.occaecat.hush.link.Snapshot
import com.occaecat.hush.link.apply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Phone half of the watch companion route: mirrors the session to the Data Layer so the watch app, its tile and
 * complications see it, and runs the commands the watch sends (see [PhoneWearService]).
 */
object WatchBridge {
    private const val TAG = "WatchBridge"

    @OptIn(FlowPreview::class)
    fun start(context: Context, controller: HeadphonesController, scope: CoroutineScope) {
        val data = Wearable.getDataClient(context)
        scope.launch {
            controller.state.map(Snapshot::from).distinctUntilChanged()
                // Slider drags and the initial burst of reads settle into one sync.
                .debounce(250)
                .collect { snapshot ->
                    val request = PutDataMapRequest.create(HushLink.STATE_PATH).apply {
                        dataMap.putString(HushLink.KEY_SNAPSHOT, snapshot.toJson())
                    }.asPutDataRequest().setUrgent()
                    try {
                        data.putDataItem(request).await()
                    } catch (e: ApiException) {
                        // No Wear OS companion on this phone: nothing to mirror to, stop trying.
                        if (e.statusCode == CommonStatusCodes.API_NOT_CONNECTED) {
                            Log.i(TAG, "Wearable API unavailable, watch bridge off")
                            throw kotlinx.coroutines.CancellationException("no wearable api")
                        }
                        Log.w(TAG, "state sync failed", e)
                    }
                }
        }
    }
}

/** Started by Google Play services when the watch sends a command, even if Hush is not running. */
class PhoneWearService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != HushLink.COMMAND_PATH) return
        val command = Command.decode(event.data) ?: return
        Log.d("PhoneWearService", "watch -> $command")
        val controller = HeadphonesController.get(this)
        android.os.Handler(mainLooper).post { controller.apply(command) }
    }
}
