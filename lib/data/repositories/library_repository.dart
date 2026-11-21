import '../../core/log/app_log.dart';
import '../../services/library_service.dart';
import '../../services/library_sync_service.dart';
import '../datasources/local/library_database.dart';
import '../models/song.dart';

/// Contrato de acceso a la biblioteca local de canciones.
abstract interface class LibraryRepository {
  /// Lee las canciones almacenadas en caché local (SQLite).
  Future<List<Song>> getCachedSongs();

  /// Devuelve el conteo actual de canciones en caché local.
  Future<int> getCachedCount();

  /// Ejecuta el escaneo nativo, persiste atómicamente en SQLite y devuelve el catálogo.
  Future<List<Song>> refreshFromDevice({bool detectNew = false});
}

/// Implementación local respaldada por SQLite y escaneo nativo.
class LocalLibraryRepository implements LibraryRepository {
  LocalLibraryRepository({
    LibraryDatabase? database,
    LibraryService? libraryService,
  }) : _database = database ?? LibraryDatabase.instance,
       _libraryService = libraryService ?? const NativeLibraryService();

  final LibraryDatabase _database;
  final LibraryService _libraryService;

  @override
  Future<List<Song>> getCachedSongs() async {
    final songs = await _database.getSongs();
    AppLog.library('Biblioteca SQLite cargada: ${songs.length} canciones.');
    return songs;
  }

  @override
  Future<int> getCachedCount() => _database.countSongs();

  @override
  Future<List<Song>> refreshFromDevice({bool detectNew = false}) async {
    final rawSongs = await _libraryService.scan();
    final parsed = rawSongs
        .map(Song.fromMap)
        .where((song) => song.id.isNotEmpty)
        .toList(growable: false);

    await _database.replaceSongs(parsed);
    final updatedSongs = await _database.getSongs();

    if (detectNew) {
      await LibrarySyncService.detectNewSongs(updatedSongs);
    }

    AppLog.library(
      'Biblioteca sincronizada en SQLite: ${updatedSongs.length} canciones.',
    );
    return updatedSongs;
  }
}
