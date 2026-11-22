package com.saladdann.offline_music.media.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.ArrayDeque

class MusicPlaybackBridge(
    private val context: Context,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler,
    MusicPlaybackService.Listener {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val methodChannel = MethodChannel(
        messenger,
        MusicPlaybackConstants.METHOD_CHANNEL
    )

    private val eventChannel = EventChannel(
        messenger,
        MusicPlaybackConstants.EVENT_CHANNEL
    )

    private var service: MusicPlaybackService? = null
    private var eventSink: EventChannel.EventSink? = null
    private var bound = false
    private var stopped = false

    private data class PendingCall(
        val call: MethodCall,
        val result: MethodChannel.Result
    )

    private val pendingCalls = ArrayDeque<PendingCall>()

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {
            runOnMain {
                if (stopped) return@runOnMain

                val local = binder as? MusicPlaybackService.LocalBinder
                if (local == null) {
                    bound = false
                    service = null
                    failPendingCalls(
                        "INVALID_SERVICE_BINDER",
                        "El enlace recibido no corresponde a MusicPlaybackService."
                    )
                    return@runOnMain
                }

                val boundService = local.getService()
                service = boundService
                boundService.setListener(this@MusicPlaybackBridge)

                emitCurrentState()
                drainPendingCalls()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            runOnMain {
                service?.setListener(null)
                service = null
                bound = false
            }
        }
    }

    fun start() {
        runOnMain {
            stopped = false
            methodChannel.setMethodCallHandler(this)
            eventChannel.setStreamHandler(this)
        }
    }

    fun stop() {
        runOnMain {
            stopped = true

            methodChannel.setMethodCallHandler(null)
            eventChannel.setStreamHandler(null)

            failPendingCalls(
                "BRIDGE_STOPPED",
                "El puente de reproducción se ha detenido."
            )

            if (bound) {
                service?.setListener(null)
                try {
                    appContext.unbindService(connection)
                } catch (_: IllegalArgumentException) {
                }
                bound = false
            }

            service = null
            eventSink = null
        }
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        if (stopped) {
            result.error(
                "BRIDGE_STOPPED",
                "El puente de reproducción no está activo.",
                null
            )
            return
        }

        val svc = service
        if (svc != null) {
            executeCall(svc, call, result)
            return
        }

        pendingCalls.addLast(PendingCall(call, result))
        ensureBound()
    }

    private fun executeCall(
        svc: MusicPlaybackService,
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        try {
            when (call.method) {

                MusicPlaybackConstants.METHOD_SET_LIBRARY -> {
                    val songs = extractSongMaps(
                        call,
                        MusicPlaybackConstants.KEY_SONGS
                    )
                    svc.setLibrary(songs)
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_SET_QUEUE_BY_INDEX -> {
                    val indices = extractIntList(
                        call,
                        MusicPlaybackConstants.KEY_INDICES
                    )

                    val initialIndex = call.argument<Int>(
                        MusicPlaybackConstants.KEY_INITIAL_INDEX
                    ) ?: 0

                    val startPositionMs = call.argument<Number>(
                        MusicPlaybackConstants.KEY_START_POSITION_MS
                    )?.toLong() ?: 0L

                    svc.setQueueByIndex(
                        indices = indices,
                        initialIndex = initialIndex,
                        startPositionMs = startPositionMs
                    )

                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_REPLACE_UPCOMING_QUEUE -> {
                    val indices = extractIntList(
                        call,
                        MusicPlaybackConstants.KEY_INDICES
                    )

                    val currentIndex = call.argument<Int>(
                        MusicPlaybackConstants.KEY_LIBRARY_INDEX
                    ) ?: 0

                    svc.replaceUpcomingQueue(
                        indices = indices,
                        currentIndex = currentIndex
                    )

                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_SET_QUEUE -> {
                    val songs = extractSongMaps(
                        call,
                        MusicPlaybackConstants.KEY_SONGS
                    )

                    val initialIndex = call.argument<Int>(
                        MusicPlaybackConstants.KEY_INITIAL_INDEX
                    ) ?: 0

                    val startPositionMs = call.argument<Number>(
                        MusicPlaybackConstants.KEY_START_POSITION_MS
                    )?.toLong() ?: 0L

                    val libraryIndexBySongId = extractStringIntMap(
                        call,
                        "libraryIndexBySongId"
                    )

                    svc.setQueue(
                        songs = songs,
                        initialIndex = initialIndex,
                        startPositionMs = startPositionMs,
                        libraryIndexBySongId = libraryIndexBySongId
                    )

                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_PLAY -> {
                    svc.play()
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_PAUSE -> {
                    svc.pause()
                    result.success(null)
                }

                "toggle" -> {
                    svc.toggle()
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_SKIP_NEXT -> {
                    svc.skipToNext()
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_SKIP_PREVIOUS -> {
                    svc.skipToPrevious()
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_SEEK -> {
                    val positionMs = call.argument<Number>(
                        MusicPlaybackConstants.KEY_POSITION_MS
                    )?.toLong() ?: 0L

                    svc.seek(positionMs)
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_SET_PLAY_MODE -> {
                    val mode = call.argument<String>(
                        MusicPlaybackConstants.KEY_PLAY_MODE
                    ) ?: MusicPlaybackConstants.PLAY_MODE_ORDER

                    svc.setPlayMode(mode)
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_SET_SPEED -> {
                    val speed = call.argument<Number>(
                        MusicPlaybackConstants.KEY_SPEED
                    )?.toFloat() ?: 1.0f

                    svc.setSpeed(speed)
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_SET_FAVORITE -> {
                    val isFavorite = call.argument<Boolean>(
                        MusicPlaybackConstants.KEY_IS_FAVORITE
                    ) ?: false

                    svc.setFavorite(isFavorite)
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_STOP -> {
                    svc.stopPlayback()
                    result.success(null)
                }

                MusicPlaybackConstants.METHOD_GET_STATE -> {
                    result.success(svc.getCurrentState())
                }

                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error(
                "PLAYBACK_ERROR",
                e.localizedMessage
                    ?: "Error desconocido al ejecutar ${call.method}",
                null
            )
        }
    }

    private fun extractSongMaps(
        call: MethodCall,
        key: String
    ): List<Map<String, Any?>> {
        val rawList = call.argument<List<*>>(key) ?: return emptyList()

        return rawList.mapNotNull { item ->
            val rawMap = item as? Map<*, *> ?: return@mapNotNull null

            rawMap.entries.associate { (k, v) ->
                k.toString() to v
            }
        }
    }

    private fun extractIntList(
        call: MethodCall,
        key: String
    ): List<Int> {
        val rawList = call.argument<List<*>>(key) ?: return emptyList()
        return rawList.mapNotNull { (it as? Number)?.toInt() }
    }

    private fun extractStringIntMap(
        call: MethodCall,
        key: String
    ): Map<String, Int>? {
        val rawMap = call.argument<Map<*, *>>(key) ?: return null

        return rawMap.entries.mapNotNull { (k, v) ->
            val mapKey = k as? String ?: return@mapNotNull null
            val mapValue = (v as? Number)?.toInt()
                ?: return@mapNotNull null

            mapKey to mapValue
        }.toMap()
    }

    private fun ensureBound() {
        if (bound || stopped) return

        val intent = Intent(
            appContext,
            MusicPlaybackService::class.java
        )

        try {
            val didBind = appContext.bindService(
                intent,
                connection,
                Context.BIND_AUTO_CREATE
            )

            if (!didBind) {
                failPendingCalls(
                    "SERVICE_BIND_FAILED",
                    "Android no pudo enlazar MusicPlaybackService."
                )
                return
            }

            bound = true
        } catch (e: Exception) {
            failPendingCalls(
                "SERVICE_BIND_FAILED",
                e.localizedMessage ?: "No se pudo enlazar el servicio."
            )
        }
    }

    private fun drainPendingCalls() {
        while (!stopped && service != null && pendingCalls.isNotEmpty()) {
            val pending = pendingCalls.removeFirst()
            val svc = service ?: break
            executeCall(svc, pending.call, pending.result)
        }
    }

    private fun failPendingCalls(
        code: String,
        message: String
    ) {
        while (pendingCalls.isNotEmpty()) {
            val pending = pendingCalls.removeFirst()
            runCatching {
                pending.result.error(code, message, null)
            }
        }
    }

    override fun onListen(
        arguments: Any?,
        sink: EventChannel.EventSink?
    ) {
        runOnMain {
            eventSink = sink
            if (stopped) return@runOnMain

            ensureBound()
            emitCurrentState()
        }
    }

    override fun onCancel(arguments: Any?) {
        runOnMain {
            eventSink = null
        }
    }

    override fun onStateChanged(state: Map<String, Any?>) {
        emitEvent(
            mapOf(MusicPlaybackConstants.EVENT_STATE to state)
        )
    }

    override fun onFavoriteToggled(
        songId: String,
        isFavorite: Boolean
    ) {
        emitEvent(
            mapOf(
                MusicPlaybackConstants.EVENT_FAVORITE_TOGGLED to mapOf(
                    MusicPlaybackConstants.KEY_ID to songId,
                    MusicPlaybackConstants.KEY_IS_FAVORITE to isFavorite
                )
            )
        )
    }

    override fun onCloseRequested() {
        emitEvent(mapOf("closeRequested" to true))
    }

    override fun onPlaybackError(
        errorCode: Int,
        errorMessage: String
    ) {
        emitEvent(
            mapOf(
                "playbackError" to mapOf(
                    "code" to errorCode,
                    "message" to errorMessage
                )
            )
        )
    }

    private fun emitCurrentState() {
        val svc = service ?: return

        emitEvent(
            mapOf(
                MusicPlaybackConstants.EVENT_STATE to svc.getCurrentState()
            )
        )
    }

    private fun emitEvent(payload: Map<String, Any?>) {
        runOnMain {
            if (stopped) return@runOnMain

            runCatching {
                eventSink?.success(payload)
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
