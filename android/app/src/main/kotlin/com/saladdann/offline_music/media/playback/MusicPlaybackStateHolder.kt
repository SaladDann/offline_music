package com.saladdann.offline_music.media.playback

object MusicPlaybackStateHolder {
    var isFavorite: Boolean = false

    fun reset() {
        isFavorite = false
    }
}
