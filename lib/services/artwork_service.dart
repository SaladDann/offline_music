import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

//import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/log/app_log.dart';
import '../data/models/song.dart';

/// Resuelve rutas de carátulas desde la capa nativa y extrae su color dominante.
class ArtworkService {
  const ArtworkService();

  static const MethodChannel _channel = MethodChannel('offline_music/media');

  /// Obtiene la ruta local de la carátula asociada a [song].
  Future<String?> getArtworkPath(Song song) async {
    final path = song.data?.trim() ?? '';
    if (path.isEmpty) return null;

    try {
      final result = await _channel
          .invokeMethod<String>('getAudioArtwork', {'path': path})
          .timeout(const Duration(seconds: 2));

      if (result == null || result.isEmpty) return null;
      if (!File(result).existsSync()) return null;

      return result;
    } on TimeoutException {
      AppLog.artwork('Timeout extrayendo artwork para ${song.fileName}');
      return null;
    } on PlatformException catch (e) {
      AppLog.error(
        'Artwork',
        'Error de plataforma extrayendo artwork para ${song.fileName}',
        e,
      );
      return null;
    } on MissingPluginException catch (e) {
      AppLog.error(
        'Artwork',
        'Canal de medios no disponible para ${song.fileName}',
        e,
      );
      return null;
    } catch (e, stackTrace) {
      AppLog.error(
        'Artwork',
        'Error inesperado extrayendo artwork para ${song.fileName}',
        stackTrace,
      );
      return null;
    }
  }

  /// Método auxiliar de compatibilidad si se invoca con una instancia de [Song].
  Future<String?> getArtwork(dynamic songOrId) async {
    if (songOrId is Song) {
      return getArtworkPath(songOrId);
    }
    return null;
  }

  /// Extrae un color dominante saturado y con contraste moderado desde [path].
  Future<Color?> extractDominantColor(String path) async {
    ui.Codec? codec;
    ui.Image? image;

    try {
      final bytes = await File(path).readAsBytes();
      if (bytes.isEmpty) return null;

      codec = await ui.instantiateImageCodec(
        bytes,
        targetWidth: 48,
        targetHeight: 48,
      );

      final frame = await codec.getNextFrame();
      image = frame.image;

      final byteData = await image.toByteData(
        format: ui.ImageByteFormat.rawRgba,
      );

      if (byteData == null) return null;
      return _findDominantColor(byteData, image.width, image.height);
    } catch (e, stackTrace) {
      AppLog.error(
        'Artwork',
        'Error extrayendo color dominante desde $path',
        stackTrace,
      );
      return null;
    } finally {
      image?.dispose();
      codec?.dispose();
    }
  }

  Color? _findDominantColor(ByteData pixels, int width, int height) {
    final histogram = <int, int>{};

    for (var i = 0; i < width * height; i++) {
      final offset = i * 4;
      final r = pixels.getUint8(offset);
      final g = pixels.getUint8(offset + 1);
      final b = pixels.getUint8(offset + 2);
      final a = pixels.getUint8(offset + 3);

      if (a < 180) continue;

      final maxChannel = r > g ? (r > b ? r : b) : (g > b ? g : b);
      final minChannel = r < g ? (r < b ? r : b) : (g < b ? g : b);

      if (maxChannel < 35 || minChannel > 235) continue;

      final key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
      histogram[key] = (histogram[key] ?? 0) + 1;
    }

    if (histogram.isEmpty) return null;

    final dominant = histogram.entries.reduce(
      (a, b) => a.value >= b.value ? a : b,
    );

    final r = ((dominant.key >> 8) & 0xF) * 16 + 8;
    final g = ((dominant.key >> 4) & 0xF) * 16 + 8;
    final b = (dominant.key & 0xF) * 16 + 8;

    return Color.fromARGB(255, r, g, b);
  }
}
