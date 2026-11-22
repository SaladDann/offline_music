package com.saladdann.offline_music.media.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.saladdann.offline_music.MainActivity
import com.saladdann.offline_music.R
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max

@UnstableApi
class MusicNotificationProvider(private val context: Context) : MediaNotification.Provider {

    private val appContext: Context = context.applicationContext

    private val notificationManager: NotificationManager by lazy {
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(
                MusicPlaybackConstants.NOTIFICATION_CHANNEL_ID
            ) != null
        ) {
            return
        }

        val channel = NotificationChannel(
            MusicPlaybackConstants.NOTIFICATION_CHANNEL_ID,
            appContext.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val songId = player.currentMediaItem?.mediaId

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.notification_unknown_title)
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.notification_unknown_artist)

        val artworkUri = metadata.artworkUri?.toString()
        val isPlaying = player.isPlaying
        val isFavorite = MusicPlaybackStateHolder.isFavorite

        val favoriteTint = ContextCompat.getColor(
            appContext,
            if (isFavorite) {
                R.color.notification_icon_tint_active
            } else {
                R.color.notification_icon_tint
            }
        )

        val remoteViews = RemoteViews(appContext.packageName, R.layout.notification_media).apply {
            setImageViewBitmap(R.id.notification_artwork, loadArtwork(artworkUri))
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_artist, artist)

            setImageViewResource(
                R.id.notification_play_pause,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            setImageViewResource(
                R.id.notification_favorite,
                if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            setInt(R.id.notification_favorite, "setColorFilter", favoriteTint)

            setOnClickPendingIntent(
                R.id.notification_close,
                actionIntent(MusicPlaybackConstants.ACTION_CLOSE)
            )
            setOnClickPendingIntent(
                R.id.notification_favorite,
                actionIntent(MusicPlaybackConstants.ACTION_FAVORITE)
            )
            setOnClickPendingIntent(
                R.id.notification_previous,
                actionIntent(MusicPlaybackConstants.ACTION_PREVIOUS)
            )
            setOnClickPendingIntent(
                R.id.notification_play_pause,
                actionIntent(MusicPlaybackConstants.ACTION_TOGGLE)
            )
            setOnClickPendingIntent(
                R.id.notification_next,
                actionIntent(MusicPlaybackConstants.ACTION_NEXT)
            )
        }

        val notification = NotificationCompat.Builder(
            appContext,
            MusicPlaybackConstants.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setContentIntent(buildContentIntent(songId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()

        return MediaNotification(MusicPlaybackConstants.NOTIFICATION_ID, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = false

    private fun buildContentIntent(songId: String?): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!songId.isNullOrBlank()) {
                putExtra(MusicPlaybackConstants.KEY_ID, songId)
            }
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(appContext, MusicPlaybackActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            appContext,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadArtwork(uri: String?): Bitmap {
        val bitmap = if (uri.isNullOrBlank()) defaultArtwork() else loadArtworkFromUri(uri)
        return roundCorners(bitmap, ARTWORK_CORNER_RADIUS_DP)
    }

    private fun loadArtworkFromUri(uri: String): Bitmap {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return defaultArtwork()
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    TARGET_ARTWORK_PX
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }

            decoded ?: defaultArtwork()
        } catch (_: Exception) {
            defaultArtwork()
        }
    }

    private fun openInputStream(uri: String): InputStream? = when {
        uri.startsWith("content://") -> {
            appContext.contentResolver.openInputStream(Uri.parse(uri))
        }

        uri.startsWith("file://") -> {
            val path = Uri.parse(uri).path
            if (!path.isNullOrBlank()) FileInputStream(File(path)) else null
        }

        uri.startsWith("/") -> {
            FileInputStream(File(uri))
        }

        else -> {
            runCatching { appContext.contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
        var inSampleSize = 1
        val largestSide = max(width, height)
        if (largestSide > reqSize) {
            val halfSide = largestSide / 2
            while ((halfSide / inSampleSize) >= reqSize) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    private fun defaultArtwork(): Bitmap {
        val size = TARGET_ARTWORK_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tintColor = ContextCompat.getColor(appContext, R.color.notification_icon_tint)
        canvas.drawColor(adjustAlpha(tintColor, 0.15f))

        val drawable = ContextCompat.getDrawable(appContext, R.drawable.ic_notification)
        if (drawable != null) {
            val wrapped = drawable.mutate()
            wrapped.setBounds(size / 4, size / 4, size * 3 / 4, size * 3 / 4)
            wrapped.setTint(tintColor)
            wrapped.draw(canvas)
        }
        return bitmap
    }

    private fun roundCorners(bitmap: Bitmap, radiusDp: Float): Bitmap {
        val width = max(1, bitmap.width)
        val height = max(1, bitmap.height)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radiusPx = radiusDp * appContext.resources.displayMetrics.density
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private companion object {
        const val TARGET_ARTWORK_PX = 256
        const val ARTWORK_CORNER_RADIUS_DP = 10f
    }
}
