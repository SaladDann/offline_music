import 'package:shared_preferences/shared_preferences.dart';

import '../core/log/app_log.dart';
import '../data/models/song.dart';

/// Detecta y persiste el estado conocido de rutas/URIs de la biblioteca.
class LibrarySyncService {
  const LibrarySyncService._();

  static const String _cachedSongsKey = 'cached_known_songs_paths';

  static String _songIdentifier(Song song) {
    final path = song.data;
    if (path != null && path.trim().isNotEmpty) {
      return path.trim();
    }
    return song.uri;
  }

  /// Compara la biblioteca actual con la última conocida y devuelve las nuevas.
  /// En la primera sincronización guarda el estado y retorna una lista vacía.
  static Future<List<Song>> detectNewSongs(List<Song> currentSongs) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final storedPaths = prefs.getStringList(_cachedSongsKey);

      if (storedPaths == null || storedPaths.isEmpty) {
        AppLog.library(
          'Primera sincronización de biblioteca: ${currentSongs.length} canciones.',
        );
        await saveCurrentLibraryState(currentSongs);
        return const [];
      }

      final knownPaths = storedPaths.toSet();
      final newSongs = currentSongs
          .where((song) {
            final identifier = _songIdentifier(song);
            return identifier.isNotEmpty && !knownPaths.contains(identifier);
          })
          .toList(growable: false);

      if (newSongs.isNotEmpty) {
        AppLog.library(
          'Se detectaron ${newSongs.length} canciones nuevas en el dispositivo.',
        );
        await saveCurrentLibraryState(currentSongs);
      } else {
        AppLog.library('No se encontraron canciones nuevas.');
      }

      return newSongs;
    } catch (e, stackTrace) {
      AppLog.error(
        'Library',
        'Error detectando canciones nuevas: $e',
        stackTrace,
      );
      return const [];
    }
  }

  /// Guarda el conjunto de identificadores de la biblioteca actual.
  static Future<void> saveCurrentLibraryState(List<Song> songs) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final paths = songs
          .map(_songIdentifier)
          .where((id) => id.isNotEmpty)
          .toSet()
          .toList(growable: false);

      await prefs.setStringList(_cachedSongsKey, paths);
      AppLog.library(
        'Estado de biblioteca guardado: ${paths.length} canciones.',
      );
    } catch (e, stackTrace) {
      AppLog.error(
        'Library',
        'Error guardando estado de la biblioteca: $e',
        stackTrace,
      );
    }
  }
}
