import 'package:flutter/services.dart';

import '../core/log/app_log.dart';

/// Contrato de escaneo nativo de biblioteca de audio.
abstract interface class LibraryService {
  Future<List<Map<String, dynamic>>> scan();
}

/// Implementación de LibraryService mediante canal nativo de medios.
class NativeLibraryService implements LibraryService {
  const NativeLibraryService();

  static const MethodChannel _channel = MethodChannel('offline_music/media');

  @override
  Future<List<Map<String, dynamic>>> scan() async {
    AppLog.library('Iniciando escaneo nativo');

    try {
      final raw = await _channel.invokeMethod<List<dynamic>>('scan');

      if (raw == null) {
        throw StateError(
          'El escaneo nativo devolvió null; no se modificará la biblioteca.',
        );
      }

      final List<Map<String, dynamic>> songs = [];

      for (final item in raw) {
        if (item is Map) {
          songs.add(Map<String, dynamic>.from(item));
        }
      }

      AppLog.library('Escaneo devolvió ${songs.length} canciones');
      return songs;
    } on PlatformException catch (e) {
      AppLog.error(
        'Library',
        'Error de plataforma en scan: ${e.code}${e.message}',
        e,
      );
      rethrow;
    } catch (e, stackTrace) {
      AppLog.error('Library', 'Error inesperado en scan: $e', stackTrace);
      rethrow;
    }
  }
}
