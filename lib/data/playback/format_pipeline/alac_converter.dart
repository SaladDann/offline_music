// ignore_for_file: avoid_slow_async_io

import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

import '../../../core/log/app_log.dart';
import '../../models/song.dart';
import 'alac_cache.dart';

typedef WavConversionInvoker = Future<String?> Function({
  required String inputPath,
  required String outputPath,
});

/// Coordina la conversión a WAV mediante canal nativo y la reutilización en [AlacCache].
class AlacConverter {
  AlacConverter({
    AlacCache? cache,
    MethodChannel? channel,
    WavConversionInvoker? conversionInvoker,
  }) : _cache = cache ?? AlacCache(),
       _channel = channel ?? const MethodChannel(_channelName),
       _conversionInvoker = conversionInvoker;

  static const String _channelName = 'offline_music/media';

  final AlacCache _cache;
  final MethodChannel _channel;
  final WavConversionInvoker? _conversionInvoker;

  final Map<String, Future<String?>> _inFlight = <String, Future<String?>>{};
  bool _isDisposed = false;

  int get inFlightConversionCount => _inFlight.length;
  bool get isDisposed => _isDisposed;

  Future<String?> getOrCreateWav(Song song, String inputPath) async {
    if (_isDisposed) return null;
    final normalizedInput = inputPath.trim();
    if (normalizedInput.isEmpty) return null;

    final inputFile = File(normalizedInput);
    if (!await inputFile.exists()) return null;

    final songId = song.id.trim();
    final key = songId.isNotEmpty ? songId : normalizedInput;

    final existing = _inFlight[key];
    if (existing != null) return existing;

    final future = _convertIfNecessary(song, normalizedInput);
    _inFlight[key] = future;

    try {
      return await future;
    } finally {
      if (identical(_inFlight[key], future)) {
        _inFlight.remove(key);
      }
    }
  }

  Future<String?> _convertIfNecessary(Song song, String inputPath) async {
    if (_isDisposed) return null;

    try {
      await _cache.ensureCacheDirectory();
      if (_isDisposed) return null;

      final outputPath = _cache.getCachePathForSongSync(song.id, inputPath);
      final outputFile = File(outputPath);

      if (await _cache.containsValidWav(outputPath)) {
        if (_isDisposed) return null;
        await _touchFile(outputFile);
        return outputPath;
      }

      if (await outputFile.exists()) {
        await _deleteIfExists(outputFile);
      }

      if (_isDisposed) return null;
      _cache.protectPath(outputPath);

      try {
        await _cache.cleanup(additionalProtectedPaths: <String>[outputPath]);
        if (_isDisposed) return null;

        final currentInputFile = File(inputPath);
        if (!await currentInputFile.exists()) return null;

        final convertedPath = await _invokeConversion(
          inputPath: inputPath,
          outputPath: outputPath,
        );

        if (_isDisposed ||
            convertedPath == null ||
            convertedPath.trim().isEmpty) {
          await _deleteIfExists(outputFile);
          return null;
        }

        if (!await outputFile.exists()) return null;
        final outputLength = await outputFile.length();

        if (outputLength <= _cache.minimumValidWavBytes) {
          await _deleteIfExists(outputFile);
          return null;
        }

        await _touchFile(outputFile);
        return outputPath;
      } finally {
        _cache.unprotectPath(outputPath);
      }
    } catch (e, stackTrace) {
      AppLog.error('Format', 'Error durante conversión ALAC: $e', stackTrace);
      return null;
    }
  }

  Future<String?> _invokeConversion({
    required String inputPath,
    required String outputPath,
  }) {
    final invoker = _conversionInvoker;
    if (invoker != null) {
      return invoker(inputPath: inputPath, outputPath: outputPath);
    }

    return _channel.invokeMethod<String>('convertAudioToWav', <String, String>{
      'path': inputPath,
      'outputPath': outputPath,
    });
  }

  Future<void> _touchFile(File file) async {
    try {
      if (await file.exists()) {
        await file.setLastModified(DateTime.now());
      }
    } catch (_) {}
  }

  Future<void> _deleteIfExists(File file) async {
    try {
      if (await file.exists()) await file.delete();
    } catch (_) {}
  }

  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _inFlight.clear();
  }
}
