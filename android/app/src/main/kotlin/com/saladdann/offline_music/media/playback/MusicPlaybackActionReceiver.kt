package com.saladdann.offline_music.media.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class MusicPlaybackActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it.isNotBlank() } ?: return
        val appContext = context.applicationContext

        val serviceIntent = Intent(appContext, MusicPlaybackService::class.java).apply {
            this.action = action
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.startService(serviceIntent)
        }
    }
}
