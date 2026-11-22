package com.saladdann.offline_music.media.playback

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.saladdann.offline_music.MainActivity
import com.saladdann.offline_music.R
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class MusicPlaybackService : MediaSessionService() {

    interface Listener {
        fun onStateChanged(state: Map<String, Any?>)
        fun onFavoriteToggled(songId: String, isFavorite: Boolean)
        fun onCloseRequested()
        fun onPlaybackError(errorCode: Int, errorMessage: String)
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = LocalBinder()

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var notificationProvider: MusicNotificationProvider? = null
    private var listener: Listener? = null
    private var isForeground = false

    private val handler = Handler(Looper.getMainLooper())

    private val stateTick = object : Runnable {
        override fun run() {
            emitState()

            if (listener != null) {
                handler.postDelayed(
                    this,
                    MusicPlaybackConstants.STATE_UPDATE_INTERVAL_MS
                )
            }
        }
    }

    private val mediaItemCache = mutableListOf<MediaItem>()
    private val queueIndices = mutableListOf<Int>()
    private var lastReportedErrorKey: String? = null

    override fun onCreate() {
        super.onCreate()

        val provider = MusicNotificationProvider(applicationContext)
        provider.createChannel()
        notificationProvider = provider
        setMediaNotificationProvider(provider)

        val renderersFactory = DefaultRenderersFactory(applicationContext)
            .setExtensionRendererMode(
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            )

        val exo = ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )

                setHandleAudioBecomingNoisy(true)
                addListener(playerListener)
            }

        player = exo
        mediaSession = MediaSession.Builder(applicationContext, exo).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent) ?: binder
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        intent?.action?.let(::handleAction)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        handler.removeCallbacks(stateTick)
        exitForeground()

        mediaSession?.release()
        mediaSession = null

        player?.removeListener(playerListener)
        player?.release()
        player = null

        mediaItemCache.clear()
        queueIndices.clear()
        lastReportedErrorKey = null

        listener = null
        notificationProvider = null

        super.onDestroy()
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
        handler.removeCallbacks(stateTick)

        if (listener != null) {
            handler.post(stateTick)
            emitState()
        }
    }

    // Recibe la biblioteca completa desde Flutter.
    fun setLibrary(songs: List<Map<String, Any?>>) {
        mediaItemCache.clear()
        mediaItemCache.addAll(songs.map(::buildMediaItem))
    }

    // Construye la cola utilizando los índices de la biblioteca.
    fun setQueueByIndex(
        indices: List<Int>,
        initialIndex: Int,
        startPositionMs: Long = 0L
    ) {
        val validIndices = indices.filter { it in mediaItemCache.indices }
        val items = validIndices.map { mediaItemCache[it] }

        queueIndices.clear()
        queueIndices.addAll(validIndices)

        val p = player ?: return

        if (items.isEmpty()) {
            p.clearMediaItems()
            emitState()
            return
        }

        val startIndex = initialIndex.coerceIn(0, items.lastIndex)
        lastReportedErrorKey = null

        p.setMediaItems(
            items,
            startIndex,
            startPositionMs.coerceAtLeast(0L)
        )

        p.prepare()
        emitState()
    }

    // Sustituye las canciones pendientes conservando la reproducción actual.
    fun replaceUpcomingQueue(
        indices: List<Int>,
        currentIndex: Int
    ) {
        val p = player ?: return

        val validIndices = indices.filter { it in mediaItemCache.indices }
        val items = validIndices.map { mediaItemCache[it] }

        if (items.isEmpty()) return

        val safeCurrent = currentIndex.coerceIn(0, items.lastIndex)
        val keepUpTo = safeCurrent.coerceAtMost(p.mediaItemCount - 1)

        if (keepUpTo < 0) {
            queueIndices.clear()
            queueIndices.addAll(validIndices)
            lastReportedErrorKey = null

            p.setMediaItems(items, safeCurrent, 0L)
            p.prepare()
            emitState()
            return
        }

        p.removeMediaItems(keepUpTo + 1, p.mediaItemCount)

        if (safeCurrent + 1 < items.size) {
            p.addMediaItems(
                keepUpTo + 1,
                items.subList(safeCurrent + 1, items.size)
            )
        }

        queueIndices.clear()
        queueIndices.addAll(validIndices.take(keepUpTo + 1))
        queueIndices.addAll(validIndices.drop(safeCurrent + 1))

        emitState()
    }

    // Recibe una cola directamente desde Flutter.
    fun setQueue(
        songs: List<Map<String, Any?>>,
        initialIndex: Int,
        startPositionMs: Long = 0L,
        libraryIndexBySongId: Map<String, Int>? = null
    ) {
        val items = songs.map(::buildMediaItem)

        queueIndices.clear()
        queueIndices.addAll(
            items.mapIndexed { index, item ->
                libraryIndexBySongId?.get(item.mediaId) ?: index
            }
        )

        val p = player ?: return

        if (items.isEmpty()) {
            p.clearMediaItems()
            emitState()
            return
        }

        val startIndex = initialIndex.coerceIn(0, items.lastIndex)
        lastReportedErrorKey = null

        p.setMediaItems(
            items,
            startIndex,
            startPositionMs.coerceAtLeast(0L)
        )

        p.prepare()
        emitState()
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun skipToNext() {
        val p = player ?: return
        if (p.mediaItemCount <= 1) return
        p.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        val p = player ?: return
        if (p.mediaItemCount <= 1) return
        p.seekToPreviousMediaItem()
    }

    fun seek(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        emitState()
    }

    fun setSpeed(speed: Float) {
        if (!speed.isFinite()) return
        player?.setPlaybackSpeed(speed.coerceIn(0.25f, 4.0f))
    }

    fun setPlayMode(mode: String) {
        val p = player ?: return

        when (mode) {
            MusicPlaybackConstants.PLAY_MODE_ORDER -> {
                p.repeatMode = Player.REPEAT_MODE_OFF
                p.shuffleModeEnabled = false
            }

            MusicPlaybackConstants.PLAY_MODE_REPEAT_ALL -> {
                p.repeatMode = Player.REPEAT_MODE_ALL
                p.shuffleModeEnabled = false
            }

            MusicPlaybackConstants.PLAY_MODE_REPEAT_ONE -> {
                p.repeatMode = Player.REPEAT_MODE_ONE
                p.shuffleModeEnabled = false
            }

            MusicPlaybackConstants.PLAY_MODE_SHUFFLE -> {
                p.repeatMode = Player.REPEAT_MODE_ALL
                p.shuffleModeEnabled = true
            }
        }

        emitState()
    }

    fun setFavorite(isFavorite: Boolean) {
        MusicPlaybackStateHolder.isFavorite = isFavorite
        refreshNotification()
        emitState()

        val songId = player?.currentMediaItem?.mediaId

        if (!songId.isNullOrEmpty()) {
            listener?.onFavoriteToggled(songId, isFavorite)
        }
    }

    fun stopPlayback() {
        player?.stop()
        player?.clearMediaItems()

        queueIndices.clear()
        lastReportedErrorKey = null

        exitForeground()
        emitState()
        stopSelf()
    }

    fun getCurrentState(): Map<String, Any?> {
        val p = player ?: return emptyMap()

        val position = p.currentMediaItemIndex

        val libraryIndex = if (position in queueIndices.indices) {
            queueIndices[position]
        } else {
            -1
        }

        return mapOf(
            MusicPlaybackConstants.STATE_CURRENT_ID to p.currentMediaItem?.mediaId,
            MusicPlaybackConstants.STATE_IS_PLAYING to p.isPlaying,
            MusicPlaybackConstants.STATE_POSITION_MS to p.currentPosition,
            MusicPlaybackConstants.STATE_DURATION_MS to p.duration.coerceAtLeast(0L),
            MusicPlaybackConstants.STATE_INDEX to position,
            MusicPlaybackConstants.STATE_QUEUE_SIZE to p.mediaItemCount,
            MusicPlaybackConstants.STATE_PLAY_MODE to currentPlayMode(),
            MusicPlaybackConstants.STATE_LIBRARY_INDEX to libraryIndex
        )
    }

    private fun handleAction(action: String) {
        when (action) {
            MusicPlaybackConstants.ACTION_PLAY -> play()
            MusicPlaybackConstants.ACTION_PAUSE -> pause()
            MusicPlaybackConstants.ACTION_TOGGLE -> toggle()
            MusicPlaybackConstants.ACTION_NEXT -> skipToNext()
            MusicPlaybackConstants.ACTION_PREVIOUS -> skipToPrevious()
            MusicPlaybackConstants.ACTION_FAVORITE -> toggleFavorite()
            MusicPlaybackConstants.ACTION_CLOSE -> closeApp()
        }
    }

    private fun toggleFavorite() {
        val songId = player?.currentMediaItem?.mediaId ?: return
        val newValue = !MusicPlaybackStateHolder.isFavorite

        MusicPlaybackStateHolder.isFavorite = newValue
        refreshNotification()

        listener?.onFavoriteToggled(songId, newValue)
        emitState()
    }

    private fun closeApp() {
        listener?.onCloseRequested()
        stopPlayback()
    }

    private fun enterForeground() {
        if (isForeground) return

        startForeground(
            MusicPlaybackConstants.NOTIFICATION_ID,
            buildNotification()
        )

        isForeground = true
    }

    private fun exitForeground() {
        if (!isForeground) return

        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
    }

    private fun refreshNotification() {
        if (!isForeground) return

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        manager.notify(
            MusicPlaybackConstants.NOTIFICATION_ID,
            buildNotification()
        )
    }

    private fun buildNotification(): Notification {
        val p = player ?: return buildMinimalNotification()

        val metadata = p.mediaMetadata
        val songId = p.currentMediaItem?.mediaId

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.notification_unknown_title)

        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.notification_unknown_artist)

        val artworkUri = metadata.artworkUri?.toString()
        val isPlaying = p.isPlaying
        val isFavorite = MusicPlaybackStateHolder.isFavorite

        val remoteViews = RemoteViews(packageName, R.layout.notification_media)

        remoteViews.setImageViewBitmap(
            R.id.notification_artwork,
            loadArtworkForService(artworkUri)
        )

        remoteViews.setTextViewText(R.id.notification_title, title)
        remoteViews.setTextViewText(R.id.notification_artist, artist)

        remoteViews.setImageViewResource(
            R.id.notification_play_pause,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        remoteViews.setImageViewResource(
            R.id.notification_favorite,
            if (isFavorite) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )

        remoteViews.setInt(
            R.id.notification_favorite,
            "setColorFilter",
            ContextCompat.getColor(
                this,
                if (isFavorite) {
                    R.color.notification_icon_tint_active
                } else {
                    R.color.notification_icon_tint
                }
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.notification_close,
            serviceActionIntent(MusicPlaybackConstants.ACTION_CLOSE)
        )

        remoteViews.setOnClickPendingIntent(
            R.id.notification_favorite,
            serviceActionIntent(MusicPlaybackConstants.ACTION_FAVORITE)
        )

        remoteViews.setOnClickPendingIntent(
            R.id.notification_previous,
            serviceActionIntent(MusicPlaybackConstants.ACTION_PREVIOUS)
        )

        remoteViews.setOnClickPendingIntent(
            R.id.notification_play_pause,
            serviceActionIntent(MusicPlaybackConstants.ACTION_TOGGLE)
        )

        remoteViews.setOnClickPendingIntent(
            R.id.notification_next,
            serviceActionIntent(MusicPlaybackConstants.ACTION_NEXT)
        )

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP

                if (songId != null) {
                    putExtra(MusicPlaybackConstants.KEY_ID, songId)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(
            this,
            MusicPlaybackConstants.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    private fun buildMinimalNotification(): Notification {
        return NotificationCompat.Builder(
            this,
            MusicPlaybackConstants.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_unknown_title))
            .setContentText(getString(R.string.notification_unknown_artist))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun serviceActionIntent(action: String): PendingIntent {
        val intent = Intent(
            this,
            MusicPlaybackActionReceiver::class.java
        ).apply {
            this.action = action
        }

        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildMediaItem(
        map: Map<String, Any?>
    ): MediaItem {
        val id = map[MusicPlaybackConstants.KEY_ID]?.toString().orEmpty()
        val title = map[MusicPlaybackConstants.KEY_TITLE]?.toString()
        val artist = map[MusicPlaybackConstants.KEY_ARTIST]?.toString()
        val album = map[MusicPlaybackConstants.KEY_ALBUM]?.toString()

        val uri = map[MusicPlaybackConstants.KEY_URI]
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: map[MusicPlaybackConstants.KEY_DATA]?.toString().orEmpty()

        val artworkUri = map[MusicPlaybackConstants.KEY_ARTWORK_URI]?.toString()

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply {
                if (!artworkUri.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(artworkUri))
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun loadArtworkForService(uri: String?): Bitmap {
        val bitmap = if (uri.isNullOrBlank()) {
            defaultArtworkForService()
        } else {
            try {
                val parsedUri = Uri.parse(uri)

                val input: InputStream? = when {
                    uri.startsWith("content://") ->
                        contentResolver.openInputStream(parsedUri)

                    uri.startsWith("file://") ->
                        parsedUri.path?.let { path ->
                            FileInputStream(File(path))
                        }

                    uri.startsWith("/") ->
                        FileInputStream(File(uri))

                    else ->
                        contentResolver.openInputStream(parsedUri)
                }

                input?.use(BitmapFactory::decodeStream)
                    ?: defaultArtworkForService()

            } catch (_: Exception) {
                defaultArtworkForService()
            }
        }

        return roundCornersForService(bitmap, 10f)
    }

    private fun defaultArtworkForService(): Bitmap {
        val size = 256

        val bitmap = Bitmap.createBitmap(
            size,
            size,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val drawable = ContextCompat.getDrawable(
            this,
            R.drawable.ic_notification
        ) ?: return bitmap

        drawable.setBounds(
            size / 4,
            size / 4,
            size * 3 / 4,
            size * 3 / 4
        )

        drawable.setTint(
            ContextCompat.getColor(
                this,
                R.color.notification_icon_tint
            )
        )

        drawable.draw(canvas)

        return bitmap
    }

    private fun roundCornersForService(
        bitmap: Bitmap,
        radiusDp: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
        }

        val rect = RectF(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat()
        )

        val radiusPx = radiusDp * resources.displayMetrics.density

        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        paint.xfermode = null

        return output
    }

    private fun currentPlayMode(): String {
        val p = player ?: return MusicPlaybackConstants.PLAY_MODE_ORDER

        return when {
            p.shuffleModeEnabled ->
                MusicPlaybackConstants.PLAY_MODE_SHUFFLE

            p.repeatMode == Player.REPEAT_MODE_ONE ->
                MusicPlaybackConstants.PLAY_MODE_REPEAT_ONE

            p.repeatMode == Player.REPEAT_MODE_ALL ->
                MusicPlaybackConstants.PLAY_MODE_REPEAT_ALL

            else ->
                MusicPlaybackConstants.PLAY_MODE_ORDER
        }
    }

    private fun emitState() {
        listener?.onStateChanged(getCurrentState())
    }

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastReportedErrorKey = null
                enterForeground()
            }

            refreshNotification()
            emitState()
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int
        ) {
            MusicPlaybackStateHolder.isFavorite = false
            lastReportedErrorKey = null

            val newId = mediaItem?.mediaId

            if (!newId.isNullOrEmpty()) {
                listener?.onFavoriteToggled(newId, false)
            }

            refreshNotification()
            emitState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            emitState()
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player
            val songId = p?.currentMediaItem?.mediaId.orEmpty()
            val queueIndex = p?.currentMediaItemIndex ?: -1

            val errorKey = "$songId:${error.errorCode}:$queueIndex"

            if (lastReportedErrorKey != errorKey) {
                lastReportedErrorKey = errorKey

                val message = error.localizedMessage
                    ?: error.message
                    ?: "Error de reproducción"

                listener?.onPlaybackError(error.errorCode, message)
            }

            emitState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            emitState()
        }

        override fun onShuffleModeEnabledChanged(
            shuffleModeEnabled: Boolean
        ) {
            emitState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            emitState()
        }
    }
}
