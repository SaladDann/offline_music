/// Contrato de acceso a permisos de biblioteca multimedia.
abstract interface class PermissionGateway {
  /// Indica si el acceso a la biblioteca ya está concedido.
  Future<bool> hasLibraryAccess();

  /// Solicita el permiso necesario y devuelve `true` si fue concedido.
  Future<bool> requestLibraryAccess();
}
