# ADR 002: Registro centralizado y pasarela de permisos de medios

- **Estado:** Aceptada
- **Ámbito:** Núcleo (`lib/core/log/app_log.dart`) y permisos (`lib/data/permissions/`)

## Contexto
Durante el desarrollo se utilizaron trazas prefijadas por módulo para diagnosticar arranque, permisos, biblioteca y reproducción, junto con comprobación previa y solicitud de permisos de audio/almacenamiento sin acoplar la interfaz a llamadas directas del sistema.

## Decisión
- Centralizar el registro en `AppLog` (`lib/core/log/app_log.dart`) con métodos por subsistema, `warn` y `error`, restringido a `kDebugMode` y sin registrar datos sensibles.
- Definir `PermissionGateway` (`lib/data/permissions/permission_gateway.dart`) con `hasLibraryAccess()` y `requestLibraryAccess()`.
- Implementar `MediaPermissionGateway` (`lib/data/permissions/media_permission_gateway.dart`) verificando/solicitando primero `Permission.audio` y con respaldo en `Permission.storage`.

## Consecuencias
- **Positivas:** Contrato desacoplado para arranque y pantallas de permisos, con trazas uniformes solo en modo depuración.
- **Riesgos:** El comportamiento exacto de `Permission.audio` y `Permission.storage` depende de la versión de Android y la configuración del manifiesto en el dispositivo de prueba.
