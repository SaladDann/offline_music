import 'package:offline_music/data/models/song.dart';
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';



/// Gestiona la base de datos SQLite local para la biblioteca de música.
class LibraryDatabase {
  LibraryDatabase._();

  static final LibraryDatabase instance = LibraryDatabase._();

  static const String _databaseName = 'offline_music.db';
  static const int _databaseVersion = 1;
  static const String _table = 'library_songs';

  Database? _database;
  Future<Database>? _openingDatabase;

  Future<Database> get database async {
    final existing = _database;
    if (existing != null) return existing;

    final opening = _openingDatabase;
    if (opening != null) return opening;

    final future = _openDatabase();
    _openingDatabase = future;

    try {
      final db = await future;
      _database = db;
      return db;
    } finally {
      _openingDatabase = null;
    }
  }

  Future<Database> _openDatabase() async {
    final databasesPath = await getDatabasesPath();
    final dbPath = path.join(databasesPath, _databaseName);

    return openDatabase(
      dbPath,
      version: _databaseVersion,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE $_table (
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            artist TEXT NOT NULL,
            album TEXT NOT NULL,
            uri TEXT NOT NULL,
            data TEXT,
            duration INTEGER NOT NULL,
            fileSize INTEGER NOT NULL,
            dateModified INTEGER NOT NULL,
            mimeType TEXT NOT NULL,
            fileName TEXT NOT NULL
          )
        ''');

        await db.execute(
          'CREATE INDEX idx_library_songs_title ON $_table(title)',
        );
      },
    );
  }

  /// Recupera todas las canciones ordenadas alfabéticamente por título.
  Future<List<Song>> getSongs() async {
    final db = await database;
    final rows = await db.query(_table, orderBy: 'title COLLATE NOCASE ASC');

    return rows.map(Song.fromMap).toList(growable: false);
  }

  /// Sustituye el catálogo completo en una transacción atómica.
  Future<void> replaceSongs(List<Song> songs) async {
    final db = await database;

    final uniqueSongs = <String, Song>{};
    for (final song in songs) {
      if (song.id.isNotEmpty) {
        uniqueSongs[song.id] = song;
      }
    }

    await db.transaction((txn) async {
      await txn.delete(_table);

      final batch = txn.batch();
      for (final song in uniqueSongs.values) {
        batch.insert(
          _table,
          song.toMap(),
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }

      await batch.commit(noResult: true);
    });
  }

  /// Devuelve el número total de pistas almacenadas.
  Future<int> countSongs() async {
    final db = await database;
    final result = await db.rawQuery('SELECT COUNT(*) AS total FROM $_table');

    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Cierra la conexión activa si está abierta.
  Future<void> close() async {
    final db = _database;
    _database = null;

    if (db != null) {
      await db.close();
    }
  }
}
