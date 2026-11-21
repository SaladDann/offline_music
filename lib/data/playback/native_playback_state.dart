import '../models/play_mode.dart';
import '../models/song.dart';

/// Estado inmutable sincronizado con la reproducción nativa.
class NativePlaybackState {
  const NativePlaybackState({
    this.currentSong,
    this.isPlaying = false,
    this.position = Duration.zero,
    this.duration = Duration.zero,
    this.playMode = PlayMode.order,
    this.index = 0,
    this.queueSize = 0,
    this.libraryIndex = -1,
  });

  const NativePlaybackState.initial() : this();

  final Song? currentSong;
  final bool isPlaying;
  final Duration position;
  final Duration duration;
  final PlayMode playMode;
  final int index;
  final int queueSize;

  /// Índice de la canción actual dentro de la biblioteca original (`-1` si no aplica).
  final int libraryIndex;

  bool get hasSong => currentSong != null;
  bool get hasQueue => queueSize > 0;
  bool get isIdle => currentSong == null;

  NativePlaybackState copyWith({
    Song? currentSong,
    bool? isPlaying,
    Duration? position,
    Duration? duration,
    PlayMode? playMode,
    int? index,
    int? queueSize,
    int? libraryIndex,
  }) {
    return NativePlaybackState(
      currentSong: currentSong ?? this.currentSong,
      isPlaying: isPlaying ?? this.isPlaying,
      position: position ?? this.position,
      duration: duration ?? this.duration,
      playMode: playMode ?? this.playMode,
      index: index ?? this.index,
      queueSize: queueSize ?? this.queueSize,
      libraryIndex: libraryIndex ?? this.libraryIndex,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is NativePlaybackState &&
          runtimeType == other.runtimeType &&
          currentSong == other.currentSong &&
          isPlaying == other.isPlaying &&
          position == other.position &&
          duration == other.duration &&
          playMode == other.playMode &&
          index == other.index &&
          queueSize == other.queueSize &&
          libraryIndex == other.libraryIndex;

  @override
  int get hashCode => Object.hash(
    currentSong,
    isPlaying,
    position,
    duration,
    playMode,
    index,
    queueSize,
    libraryIndex,
  );

  @override
  String toString() =>
      'NativePlaybackState('
      'song: ${currentSong?.title}, '
      'isPlaying: $isPlaying, '
      'position: ${position.inMilliseconds}ms, '
      'duration: ${duration.inMilliseconds}ms, '
      'playMode: ${playMode.name}, '
      'index: $index/$queueSize, '
      'libraryIndex: $libraryIndex'
      ')';
}
