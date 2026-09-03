package com.gilespii.radioex.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.util.Log

/**
 * BroadcastReceiver triggered by the Android TV system upon app install, update,
 * or device boot to initialize preview channels and recommendation programs.
 */
class TvChannelReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TvChannelReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast action: $action")
        if (action == TvContract.ACTION_INITIALIZE_PROGRAMS ||
            action == "android.media.tv.action.INITIALIZE_PROGRAMS" ||
            action == Intent.ACTION_BOOT_COMPLETED
        ) {
            TvChannelManager.syncChannels(context)
        }
    }
}
