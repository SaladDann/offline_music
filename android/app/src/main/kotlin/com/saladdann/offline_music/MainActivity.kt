package com.saladdann.offline_music

import com.saladdann.offline_music.media.MediaChannelHandler
import com.saladdann.offline_music.media.MediaConstants
import com.saladdann.offline_music.media.playback.MusicPlaybackBridge
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private var mediaHandler: MediaChannelHandler? = null
    private var playbackBridge: MusicPlaybackBridge? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val appContext = applicationContext
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        val handler = MediaChannelHandler(
            context = appContext,
            runOnUiThread = { runnable -> runOnUiThread(runnable) }
        )
        mediaHandler = handler

        MethodChannel(messenger, MediaConstants.CHANNEL)
            .setMethodCallHandler(handler)

        val bridge = MusicPlaybackBridge(
            context = appContext,
            messenger = messenger
        )
        bridge.start()
        playbackBridge = bridge
    }

    override fun onDestroy() {
        playbackBridge?.stop()
        playbackBridge = null

        mediaHandler?.shutdown()
        mediaHandler = null

        super.onDestroy()
    }
}
