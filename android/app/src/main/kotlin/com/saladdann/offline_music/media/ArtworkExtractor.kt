package com.saladdann.offline_music.media

import android.media.MediaMetadataRetriever
import java.io.File
import java.security.MessageDigest

internal class ArtworkExtractor(private val cacheDir: File) {

    private val artworkDir: File by lazy {
        File(cacheDir, "offline_music_artwork").apply {
            if (!exists()) mkdirs()
        }
    }

    fun artworkCacheDirPath(): String = artworkDir.absolutePath

    fun alacCacheDirPath(): String =
        File(cacheDir, "offline_music_alac").absolutePath

    fun appCacheDirPath(): String = cacheDir.absolutePath

    fun extract(inputPath: String): String? {
        val inputFile = File(inputPath)
        if (!inputFile.exists() || !inputFile.isFile) return null

        val cached = cachedFileFor(inputFile)
        if (cached.exists() && cached.length() > 0L) {
            return cached.absolutePath
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(inputFile.absolutePath)
            val picture = retriever.embeddedPicture ?: return null
            if (picture.isEmpty()) return null

            if (!artworkDir.exists() && !artworkDir.mkdirs()) {
                return null
            }

            cached.writeBytes(picture)
            cached.absolutePath
        } catch (_: Exception) {
            runCatching { if (cached.exists()) cached.delete() }
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun cachedFileFor(inputFile: File): File {
        val key = "${inputFile.absolutePath}|${inputFile.lastModified()}|${inputFile.length()}"
        val digest = sha256Hex(key).take(16)
        return File(artworkDir, "art_$digest.img")
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
