/// Modelo inmutable que representa una pista de audio indexada.
class Song {
  const Song({
    required this.id,
    required this.title,
    required this.artist,
    required this.album,
    required this.uri,
    this.data,
    required this.duration,
    required this.fileSize,
    this.dateModified = 0,
    required this.mimeType,
    required this.fileName,
  });

  final String id;
  final String title;
  final String artist;
  final String album;
  final String uri;
  final String? data;

  /// Duración en milisegundos.
  final int duration;

  /// Tamaño del archivo en bytes.
  final int fileSize;

  /// Marca de tiempo de última modificación en milisegundos desde Unix epoch.
  /// Un valor de `0` indica que no está disponible.
  final int dateModified;

  final String mimeType;
  final String fileName;

  /// Duración convertida a [Duration].
  Duration get durationAsDuration => Duration(milliseconds: duration);

  /// Duración formateada como `MM:SS`.
  String get formattedDuration {
    final total = durationAsDuration;
    final minutes = total.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = total.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  /// Construye una instancia de [Song] a partir de un mapa (canal nativo o BD).
  factory Song.fromMap(Map<String, dynamic> map) {
    final rawData = map['data'];
    return Song(
      id: _toNonEmptyString(map['id'], fallback: ''),
      title: _toNonEmptyString(map['title'], fallback: 'Desconocido'),
      artist: _toNonEmptyString(map['artist'], fallback: 'Desconocido'),
      album: _toNonEmptyString(map['album'], fallback: 'Desconocido'),
      uri: _toNonEmptyString(map['uri'], fallback: ''),
      data: rawData?.toString(),
      duration: _toInt(map['duration']),
      fileSize: _toInt(map['fileSize']),
      dateModified: _parseDateModified(map['dateModified']),
      mimeType: _toNonEmptyString(map['mimeType'], fallback: 'audio/mpeg'),
      fileName: _toNonEmptyString(map['fileName'], fallback: ''),
    );
  }

  /// Serializa la instancia a un mapa clave-valor.
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'artist': artist,
      'album': album,
      'uri': uri,
      'data': data,
      'duration': duration,
      'fileSize': fileSize,
      'dateModified': dateModified,
      'mimeType': mimeType,
      'fileName': fileName,
    };
  }

  Song copyWith({
    String? id,
    String? title,
    String? artist,
    String? album,
    String? uri,
    String? data,
    int? duration,
    int? fileSize,
    int? dateModified,
    String? mimeType,
    String? fileName,
  }) {
    return Song(
      id: id ?? this.id,
      title: title ?? this.title,
      artist: artist ?? this.artist,
      album: album ?? this.album,
      uri: uri ?? this.uri,
      data: data ?? this.data,
      duration: duration ?? this.duration,
      fileSize: fileSize ?? this.fileSize,
      dateModified: dateModified ?? this.dateModified,
      mimeType: mimeType ?? this.mimeType,
      fileName: fileName ?? this.fileName,
    );
  }

  static String _toNonEmptyString(dynamic value, {required String fallback}) {
    if (value == null) return fallback;
    final text = value.toString().trim();
    return text.isEmpty ? fallback : text;
  }

  static int _toInt(dynamic value) {
    if (value == null) return 0;
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value.toString()) ?? 0;
  }

  static int _parseDateModified(dynamic value) {
    final raw = _toInt(value);
    if (raw <= 0) return 0;
    // Normaliza timestamps en segundos (~10 dígitos) a milisegundos (~13 dígitos).
    if (raw < 100000000000) return raw * 1000;
    return raw;
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Song && runtimeType == other.runtimeType && id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  String toString() =>
      'Song(id: $id, title: $title, artist: $artist, dateModified: $dateModified)';
}
