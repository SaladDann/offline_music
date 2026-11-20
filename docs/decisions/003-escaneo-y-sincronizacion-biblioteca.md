# ADR 003: Escaneo nativo vía canal y sincronización de biblioteca

- **Estado:** Aceptada
- **Ámbito:** Servicios de biblioteca (`lib/services/`) y persistencia local (`lib/data/datasources/local/library_database.dart`)

## Contexto
El escaneo de canciones se delega al canal nativo de medios para evitar repetir consultas costosas en cada arranque cuando existe caché local en SQLite, y se requiere detectar incorporaciones de pistas sin marcar todo el catálogo como nuevo en el primer escaneo.

## Decisión
- Definir `LibraryService` e implementarlo en `NativeLibraryService` (`lib/services/library_service.dart`) sobre `MethodChannel('offline_music/media')` (`scan`), lanzando `StateError` si el resultado es `null` para no sobrescribir datos válidos.
- Adaptar `LibrarySyncService` (`lib/services/library_sync_service.dart`) al modelo `Song` y `AppLog`, priorizando `Song.data` (ruta física) sobre `Song.uri` y guardando la instantánea inicial en el primer escaneo sin reportar falsos positivos.

## Consecuencias
- **Positivas:** Se protege el catálogo ante respuestas nulas del canal y se unifica el registro mediante `AppLog`.
- **Riesgos:** Si se modifica el nombre del canal o método en Kotlin, `NativeLibraryService` requerirá actualización coordinada.
