import 'package:permission_handler/permission_handler.dart';

import '../../core/log/app_log.dart';
import 'permission_gateway.dart';

/// Implementación de [PermissionGateway] para permisos de audio y almacenamiento.
class MediaPermissionGateway implements PermissionGateway {
  const MediaPermissionGateway();

  @override
  Future<bool> hasLibraryAccess() async {
    final audio = await Permission.audio.status;
    if (audio.isGranted) return true;

    final storage = await Permission.storage.status;
    if (storage.isGranted) return true;

    return false;
  }

  @override
  Future<bool> requestLibraryAccess() async {
    AppLog.library('Solicitando permisos de audio al sistema');

    final audio = await Permission.audio.request();
    if (audio.isGranted) {
      AppLog.library('Permiso de audio concedido');
      return true;
    }

    AppLog.library(
      'Permiso de audio no concedido, probando almacenamiento general',
    );
    final storage = await Permission.storage.request();
    if (storage.isGranted) {
      AppLog.library('Permiso de storage concedido');
      return true;
    }

    AppLog.library('Permisos denegados por el usuario');
    return false;
  }
}
