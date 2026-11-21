import 'dart:async';

import 'package:flutter/services.dart';

import '../../core/log/app_log.dart';
import '../models/play_mode.dart';
import '../models/song.dart';
import 'native_playback_state.dart';

class FavoriteToggledEvent {
  const FavoriteToggledEvent({required this.songId, required this.isFavorite});

  final String songId;
  final bool isFavorite;
}

class PlaybackErrorEvent {
  const PlaybackErrorEvent({
    required this.message,
    this.code,
    this.songId,
    this.index,
  });

  final String message;
  final String? code;
  final String? songId;
  final int? index;
}

abstract interface class NativePlaybackServiceContract {
  Future<void> setLibrary(List<Song> songs);

  Future<void> setQueue(
    List<Song> songs, {
    int initialIndex = 0,
    Duration startPosition = Duration.zero,
    Map<String, String>? artworkBySongId,
    Map<String, int>? libraryIndexBySongId,
  });

  Future<void> setQueueByIndex(
    List<int> indices, {
    int initialIndex = 0,
    Duration startPosition = Duration.zero,
  });

  Future<void> replaceUpcomingQueue(
    List<int> indices, {
    required int currentLibraryIndex,
  });

  Future<void> play();
  Future<void> pause();
  Future<void> skipToNext();
  Future<void> skipToPrevious();
  Future<void> seek(Duration position);
  Future<void> setSpeed(double speed);
  Future<void> setPlayMode(PlayMode mode);
  Future<void> setFavorite(bool isFavorite);
  Future<void> stop();
  Future<NativePlaybackState> getState();

  Stream<NativePlaybackState> get stateStream;
  Stream<FavoriteToggledEvent> get favoriteStream;
  Stream<PlaybackErrorEvent> get playbackErrorStream;
  Stream<void> get closeStream;

  Future<void> dispose();
}

/// Cliente de canales de plataforma para reproducción nativa en Android.
class NativePlaybackService implements NativePlaybackServiceContract {
  NativePlaybackService() {
    AppLog.native('Playback service inicializado');
    _eventSubscription = _events.receiveBroadcastStream().listen(
      _handleEvent,
      onError: _handleError,
    );
  }

  static const MethodChannel _method = MethodChannel('offline_music/playback');
  static const EventChannel _events = EventChannel(
    'offline_music/playback_events',
  );

  final StreamController<NativePlaybackState> _stateController =
      StreamController<NativePlaybackState>.broadcast();
  final StreamController<FavoriteToggledEvent> _favoriteController =
      StreamController<FavoriteToggledEvent>.broadcast();
  final StreamController<PlaybackErrorEvent> _playbackErrorController =
      StreamController<PlaybackErrorEvent>.broadcast();
  final StreamController<void> _closeController =
      StreamController<void>.broadcast();

  StreamSubscription<dynamic>? _eventSubscription;
  bool _isDisposed = false;

  @override
  Stream<NativePlaybackState> get stateStream => _stateController.stream;

  @override
  Stream<FavoriteToggledEvent> get favoriteStream => _favoriteController.stream;

  @override
  Stream<PlaybackErrorEvent> get playbackErrorStream =>
      _playbackErrorController.stream;

  @override
  Stream<void> get closeStream => _closeController.stream;

  @override
  Future<void> setLibrary(List<Song> songs) async {
    _ensureNotDisposed();
    final payload = songs
        .map((song) => _songToNative(song, null))
        .toList(growable: false);
    await _method.invokeMethod<void>('setLibrary', {'songs': payload});
  }

  @override
  Future<void> setQueue(
    List<Song> songs, {
    int initialIndex = 0,
    Duration startPosition = Duration.zero,
    Map<String, String>? artworkBySongId,
    Map<String, int>? libraryIndexBySongId,
  }) async {
    _ensureNotDisposed();
    final payload = songs
        .map((song) => _songToNative(song, artworkBySongId?[song.id]))
        .toList(growable: false);
    await _method.invokeMethod<void>('setQueue', {
      'songs': payload,
      'initialIndex': initialIndex,
      'startPositionMs': startPosition.inMilliseconds,
      'libraryIndexBySongId': libraryIndexBySongId,
    });
  }

  @override
  Future<void> setQueueByIndex(
    List<int> indices, {
    int initialIndex = 0,
    Duration startPosition = Duration.zero,
  }) async {
    _ensureNotDisposed();
    await _method.invokeMethod<void>('setQueueByIndex', {
      'indices': indices,
      'initialIndex': initialIndex,
      'startPositionMs': startPosition.inMilliseconds,
    });
  }

  @override
  Future<void> replaceUpcomingQueue(
    List<int> indices, {
    required int currentLibraryIndex,
  }) async {
    _ensureNotDisposed();
    await _method.invokeMethod<void>('replaceUpcomingQueue', {
      'indices': indices,
      'libraryIndex': currentLibraryIndex,
    });
  }

  @override
  Future<void> play() {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('play');
  }

  @override
  Future<void> pause() {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('pause');
  }

  @override
  Future<void> skipToNext() {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('skipToNext');
  }

  @override
  Future<void> skipToPrevious() {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('skipToPrevious');
  }

  @override
  Future<void> seek(Duration position) {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('seek', {
      'positionMs': position.inMilliseconds,
    });
  }

  @override
  Future<void> setSpeed(double speed) {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('setSpeed', {'speed': speed});
  }

  @override
  Future<void> setPlayMode(PlayMode mode) {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('setPlayMode', {'playMode': mode.name});
  }

  @override
  Future<void> setFavorite(bool isFavorite) {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('setFavorite', {
      'isFavorite': isFavorite,
    });
  }

  @override
  Future<void> stop() {
    _ensureNotDisposed();
    return _method.invokeMethod<void>('stop');
  }

  @override
  Future<NativePlaybackState> getState() async {
    _ensureNotDisposed();
    final raw = await _method.invokeMethod<Map<dynamic, dynamic>>('getState');
    if (raw == null) return const NativePlaybackState.initial();
    return _mapToState(raw.cast<String, dynamic>());
  }

  @override
  Future<void> dispose() async {
    if (_isDisposed) return;
    _isDisposed = true;

    await _eventSubscription?.cancel();
    _eventSubscription = null;

    await _stateController.close();
    await _favoriteController.close();
    await _playbackErrorController.close();
    await _closeController.close();
  }

  Map<String, dynamic> _songToNative(Song song, String? artworkUri) {
    return {
      'id': song.id,
      'title': song.title,
      'artist': song.artist,
      'album': song.album,
      'uri': song.uri,
      'data': song.data,
      'duration': song.duration,
      'mimeType': song.mimeType,
      'artworkUri': artworkUri,
    };
  }

  void _handleEvent(dynamic event) {
    if (_isDisposed || event is! Map) return;
    final map = event.cast<dynamic, dynamic>();

    if (map.containsKey('state')) {
      final raw = map['state'];
      if (raw is Map && !_stateController.isClosed) {
        _stateController.add(_mapToState(raw.cast<String, dynamic>()));
      }
      return;
    }

    if (map.containsKey('favoriteToggled')) {
      final raw = map['favoriteToggled'];
      if (raw is Map && !_favoriteController.isClosed) {
        final favorite = raw.cast<String, dynamic>();
        _favoriteController.add(
          FavoriteToggledEvent(
            songId: (favorite['id'] ?? '').toString(),
            isFavorite: favorite['isFavorite'] == true,
          ),
        );
      }
      return;
    }

    if (map.containsKey('playbackError')) {
      final raw = map['playbackError'];
      if (!_playbackErrorController.isClosed) {
        if (raw is Map) {
          final error = raw.cast<String, dynamic>();
          _playbackErrorController.add(
            PlaybackErrorEvent(
              message: (error['message'] ?? 'Error de reproducción').toString(),
              code: error['code']?.toString(),
              songId: error['songId']?.toString(),
              index: _toIntOrNull(error['index']),
            ),
          );
        } else {
          _playbackErrorController.add(
            PlaybackErrorEvent(
              message: raw?.toString() ?? 'Error de reproducción',
            ),
          );
        }
      }
      return;
    }

    if (map['closeRequested'] == true && !_closeController.isClosed) {
      _closeController.add(null);
    }
  }

  void _handleError(Object error, StackTrace stack) {
    AppLog.warn('Native', 'Error en EventChannel: $error');
  }

  NativePlaybackState _mapToState(Map<String, dynamic> map) {
    return NativePlaybackState(
      currentSong: null,
      isPlaying: map['isPlaying'] == true,
      position: Duration(milliseconds: _toInt(map['positionMs'])),
      duration: Duration(milliseconds: _toInt(map['durationMs'])),
      playMode: PlayMode.fromName(map['playMode']?.toString()),
      index: _toInt(map['index']),
      queueSize: _toInt(map['queueSize']),
      libraryIndex: _toIntOrNull(map['libraryIndex']) ?? -1,
    );
  }

  int _toInt(dynamic value) {
    if (value == null) return 0;
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value.toString()) ?? 0;
  }

  int? _toIntOrNull(dynamic value) {
    if (value == null) return null;
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value.toString());
  }

  void _ensureNotDisposed() {
    if (_isDisposed) {
      throw StateError('NativePlaybackService ya fue liberado.');
    }
  }
}
