package com.occaecat.hush.wear.hub

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Wakes the app for phone state syncs and phone reachability changes, so tiles and complications stay current. */
class PhoneLinkService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) = HushHub.get(this).onDataEvents(events)

    override fun onCapabilityChanged(info: CapabilityInfo) = HushHub.get(this).onCapability(info)
}
