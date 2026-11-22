package com.saladdann.offline_music.media

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

internal object AudioMimeDetector {

    /**
     * Devuelve el MIME real de la primera pista de audio del archivo,
     * o `null` si no se encuentra ninguna pista de audio.
     */
    fun detect(inputPath: String): String? {
        val inputFile = File(inputPath)
        require(inputFile.exists() && inputFile.isFile) {
            "El archivo no existe: $inputPath"
        }

        var extractor: MediaExtractor? = null
        return try {
            extractor = MediaExtractor().apply {
                setDataSource(inputFile.absolutePath)
            }

            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith(MediaConstants.AUDIO_MIME_PREFIX)) {
                    return mime
                }
            }
            null
        } finally {
            runCatching { extractor?.release() }
        }
    }
}
