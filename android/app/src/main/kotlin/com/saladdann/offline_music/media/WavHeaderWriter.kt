package com.saladdann.offline_music.media

import java.io.File
import java.io.RandomAccessFile

internal object WavHeaderWriter {

    fun write(
        file: File,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val safeAudioLen = totalAudioLen.coerceAtLeast(0L)
        val safeSampleRate = sampleRate.coerceAtLeast(1)
        val safeChannels = channels.coerceAtLeast(1)
        val safeBitsPerSample = bitsPerSample.coerceAtLeast(8)

        val byteRate = safeSampleRate.toLong() * safeChannels.toLong() * safeBitsPerSample.toLong() / 8L
        val blockAlign = safeChannels * safeBitsPerSample / 8
        val totalDataLen = safeAudioLen + 36L

        val header = buildHeader(
            totalDataLen = totalDataLen,
            totalAudioLen = safeAudioLen,
            sampleRate = safeSampleRate,
            channels = safeChannels,
            bitsPerSample = safeBitsPerSample,
            byteRate = byteRate,
            blockAlign = blockAlign
        )

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0L)
            raf.write(header)
        }
    }

    private fun buildHeader(
        totalDataLen: Long,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        byteRate: Long,
        blockAlign: Int
    ): ByteArray {
        val header = ByteArray(MediaConstants.WAV_HEADER_SIZE)

        // Chunk RIFF
        header.writeAscii(0, "RIFF")
        header.writeIntLE(4, totalDataLen)
        header.writeAscii(8, "WAVE")

        // Subchunk fmt (PCM sin compresión)
        header.writeAscii(12, "fmt ")
        header.writeIntLE(16, 16L)
        header.writeShortLE(20, 1)
        header.writeShortLE(22, channels)
        header.writeIntLE(24, sampleRate.toLong())
        header.writeIntLE(28, byteRate)
        header.writeShortLE(32, blockAlign)
        header.writeShortLE(34, bitsPerSample)

        // Subchunk data
        header.writeAscii(36, "data")
        header.writeIntLE(40, totalAudioLen)

        return header
    }

    private fun ByteArray.writeAscii(offset: Int, value: String) {
        value.forEachIndexed { i, c -> this[offset + i] = c.code.toByte() }
    }

    private fun ByteArray.writeIntLE(offset: Int, value: Long) {
        this[offset] = (value and 0xffL).toByte()
        this[offset + 1] = ((value shr 8) and 0xffL).toByte()
        this[offset + 2] = ((value shr 16) and 0xffL).toByte()
        this[offset + 3] = ((value shr 24) and 0xffL).toByte()
    }

    private fun ByteArray.writeShortLE(offset: Int, value: Int) {
        this[offset] = (value and 0xff).toByte()
        this[offset + 1] = ((value shr 8) and 0xff).toByte()
    }
}
