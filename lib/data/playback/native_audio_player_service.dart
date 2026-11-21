import 'dart:async';

import '../../core/log/app_log.dart';
import '../../services/artwork_service.dart';
import '../models/play_mode.dart';
import '../models/song.dart';
import 'audio_player_service.dart';
import 'native_playback_service.dart';
import 'native_playback_state.dart';

/// Coordinador de reproducción que adapta [NativePlaybackServiceContract] a [AudioPlayerService].
class NativeAudioPlayerService implements AudioPlayerService {
  NativeAudioPlayerService({
    NativePlaybackServiceContract? nativeService,
    ArtworkService? artworkService,
  }) : _nativeService = nativeService ?? NativePlaybackService(),
       _artworkService = artworkService ?? const ArtworkService() {
    _stateSubscription = _nativeService.stateStream.listen(_onNativeState);
    _favoriteSubscription = _nativeService.favoriteStream.listen((event) {
      if (_isDisposed || _favoriteToggledController.isClosed) return;
      _favoriteToggledController.add(
        FavoriteToggledFromNotification(
          songId: event.songId,
          isFavorite: event.isFavorite,
        ),
      );
    });
    _errorSubscription = _nativeService.playbackErrorStream.listen((error) {
      AppLog.error(
        'Playback',
        'Error nativo [${error.code ?? 'sin_codigo'}]: ${error.message}',
      );
    });
  }

  final NativePlaybackServiceContract _nativeService;
  final ArtworkService _artworkService;

  final StreamController<PlayerStateEnum> _playerStateController =
      StreamController<PlayerStateEnum>.broadcast();
  final StreamController<Duration> _positionController =
      StreamController<Duration>.broadcast();
  final StreamController<Duration?> _durationController =
      StreamController<Duration?>.broadcast();
  final StreamController<Song?> _currentSongController =
      StreamController<Song?>.broadcast();
  final StreamController<FavoriteToggledFromNotification>
  _favoriteToggledController =
      StreamController<FavoriteToggledFromNotification>.broadcast();

  StreamSubscription<NativePlaybackState>? _stateSubscription;
  StreamSubscription<FavoriteToggledEvent>? _favoriteSubscription;
  StreamSubscription<PlaybackErrorEvent>? _errorSubscription;

  List<Song> _playlist = const [];
  int _currentIndex = 0;
  bool _isDisposed = false;
  Future<void> _queueOperation = Future<void>.value();

  @override
  Stream<PlayerStateEnum> get playerStateStream =>
      _playerStateController.stream;

  @override
  Stream<Duration> get positionStream => _positionController.stream;

  @override
  Stream<Duration?> get durationStream => _durationController.stream;

  @override
  Stream<Song?> get currentSongStream => _currentSongController.stream;

  @override
  Stream<FavoriteToggledFromNotification> get favoriteToggledStream =>
      _favoriteToggledController.stream;

  @override
  Future<void> initializeDeviceCapabilities(List<Song> songs) async {
    if (_isDisposed) return;
    AppLog.playback(
      'Capacidades de reproducción verificadas para ${songs.length} pistas',
    );
  }

  @override
  Future<void> playSong(Song song) async {
    if (_isDisposed) return;
    final index = _playlist.indexWhere((item) => item.id == song.id);
    if (index >= 0) {
      await setPlaylist(_playlist, initialIndex: index);
    } else {
      await setPlaylist([song], initialIndex: 0);
    }
    if (_isDisposed) return;
    await play();
  }

  @override
  Future<void> setPlaylist(
    List<Song> playlist, {
    int initialIndex = 0,
    Duration startPosition = Duration.zero,
  }) {
    if (_isDisposed) return Future<void>.value();

    final operation = _queueOperation.then((_) async {
      if (_isDisposed || playlist.isEmpty) return;

      _playlist = List<Song>.unmodifiable(playlist);
      _currentIndex = initialIndex.clamp(0, _playlist.length - 1);
      final safePosition = startPosition.isNegative
          ? Duration.zero
          : startPosition;
      final selected = _playlist[_currentIndex];

      _emitSong(selected);
      _emitDuration(selected.duration > 0 ? selected.durationAsDuration : null);

      final artworkBySongId = <String, String>{};
      final artworkPath = await _artworkService.getArtworkPath(selected);
      if (!_isDisposed && artworkPath != null && artworkPath.isNotEmpty) {
        artworkBySongId[selected.id] = artworkPath;
      }

      final libraryIndexBySongId = <String, int>{
        for (var i = 0; i < _playlist.length; i++) _playlist[i].id: i,
      };

      if (_isDisposed) return;
      await _nativeService.setLibrary(_playlist);
      if (_isDisposed) return;
      await _nativeService.setQueue(
        _playlist,
        initialIndex: _currentIndex,
        startPosition: safePosition,
        artworkBySongId: artworkBySongId,
        libraryIndexBySongId: libraryIndexBySongId,
      );
    });

    _queueOperation = operation.catchError((Object error, StackTrace stack) {
      AppLog.error('Playback', 'Error configurando cola: $error', stack);
    });

    return operation;
  }

  @override
  Future<void> play() async {
    if (_isDisposed) return;
    await _nativeService.play();
  }

  @override
  Future<void> pause() async {
    if (_isDisposed) return;
    await _nativeService.pause();
  }

  @override
  Future<void> stop() async {
    if (_isDisposed) return;
    await _nativeService.stop();
    if (!_playerStateController.isClosed) {
      _playerStateController.add(PlayerStateEnum.stopped);
    }
  }

  @override
  Future<void> skipToNext() async {
    if (_isDisposed) return;
    await _queueOperation;
    if (_isDisposed) return;
    await _nativeService.skipToNext();
  }

  @override
  Future<void> skipToPrevious() async {
    if (_isDisposed) return;
    await _queueOperation;
    if (_isDisposed) return;
    await _nativeService.skipToPrevious();
  }

  @override
  Future<void> seek(Duration position) async {
    if (_isDisposed) return;
    final safe = position.isNegative ? Duration.zero : position;
    await _nativeService.seek(safe);
    if (!_positionController.isClosed) {
      _positionController.add(safe);
    }
  }

  @override
  Future<void> setSpeed(double speed) async {
    if (_isDisposed) return;
    await _nativeService.setSpeed(speed);
  }

  @override
  Future<void> setPlayMode(PlayMode mode) async {
    if (_isDisposed) return;
    await _nativeService.setPlayMode(mode);
  }

  @override
  Future<void> setFavorite(bool isFavorite) async {
    if (_isDisposed) return;
    await _nativeService.setFavorite(isFavorite);
  }

  @override
  Future<void> updatePlaylistOrder(List<Song> playlist) async {
    if (_isDisposed || playlist.isEmpty) return;
    await _queueOperation;
    if (_isDisposed) return;

    final currentSong = _currentIndex >= 0 && _currentIndex < _playlist.length
        ? _playlist[_currentIndex]
        : null;
    final newIndex = currentSong == null
        ? 0
        : playlist
              .indexWhere((s) => s.id == currentSong.id)
              .clamp(0, playlist.length - 1);

    await setPlaylist(playlist, initialIndex: newIndex);
  }

  void _onNativeState(NativePlaybackState state) {
    if (_isDisposed) return;

    if (_playlist.isNotEmpty &&
        state.index >= 0 &&
        state.index < _playlist.length &&
        state.index != _currentIndex) {
      _currentIndex = state.index;
      _emitSong(_playlist[_currentIndex]);
    }

    if (!_positionController.isClosed) {
      _positionController.add(state.position);
    }

    _emitDuration(state.duration > Duration.zero ? state.duration : null);

    if (!_playerStateController.isClosed) {
      _playerStateController.add(_mapPlayerState(state));
    }
  }

  PlayerStateEnum _mapPlayerState(NativePlaybackState state) {
    if (state.isPlaying) return PlayerStateEnum.playing;
    if (state.duration > Duration.zero && state.position >= state.duration) {
      return PlayerStateEnum.completed;
    }
    if (_playlist.isEmpty &&
        state.queueSize == 0 &&
        state.position == Duration.zero) {
      return PlayerStateEnum.stopped;
    }
    return PlayerStateEnum.paused;
  }

  void _emitSong(Song song) {
    if (!_currentSongController.isClosed) {
      _currentSongController.add(song);
    }
  }

  void _emitDuration(Duration? duration) {
    if (!_durationController.isClosed) {
      _durationController.add(duration);
    }
  }

  @override
  Future<void> dispose() async {
    if (_isDisposed) return;
    _isDisposed = true;

    await _stateSubscription?.cancel();
    await _favoriteSubscription?.cancel();
    await _errorSubscription?.cancel();

    await _playerStateController.close();
    await _positionController.close();
    await _durationController.close();
    await _currentSongController.close();
    await _favoriteToggledController.close();

    await _nativeService.dispose();
  }
}
