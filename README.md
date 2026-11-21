# offline_music

Reproductor de música local **offline** para Android desarrollado con **Flutter (Dart)** y componentes nativos en **Kotlin** (`AndroidX Media3` / `ExoPlayer`, `MediaSessionService` y `MediaStore`).

> **Estado del proyecto:** Reconstrucción limpia desde cero (en progreso). Las características arquitectónicas descritas abajo corresponden al diseño objetivo y se marcarán como implementadas únicamente tras su compilación y validación en dispositivo real.

---

## Objetivo

Construir una arquitectura modular, verificable y mantenible para la indexación y reproducción de bibliotecas musicales locales sin depender de conexión a internet, resolviendo limitaciones de plugins de alto nivel mediante un puente nativo explícito y persistencia local estructurada.

### Alcance inicial (MVP)
- Gestión de permisos de acceso a audio en Android (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`).
- Escaneo nativo de metadatos mediante `MediaStore` e indexación persistente en **SQLite**.
- Reproducción en segundo plano mediante `MediaSessionService` + `ExoPlayer` (`AndroidX Media3`).
- Notificación multimedia sincronizada y controles de reproducción (`play`, `pause`, `next`, `previous`, `seek`).
- Interfaz modular en Flutter con soporte de tema claro/oscuro y restauración de sesión en pausa.

### Evolución planificada (fases posteriores)
- Cola avanzada basada en índices sobre la biblioteca y modos de reproducción (`normal`, `repeatAll`, `repeatOne`, `shuffle`).
- Pipeline de validación y conversión controlada de formatos complejos (como ALAC a WAV) con caché acotada y métricas de latencia.
- Evaluación de fuentes adicionales (Google Drive / biblioteca fusionada) desacopladas del núcleo offline.

---

## Arquitectura prevista

```text
Flutter (Dart)
├── Presentation   → Pantallas, widgets y controladores de estado
├── Data           → Modelos, repositorios, SQLite y contratos de reproducción
└── Platform Bridge→ MethodChannel / EventChannel tipados
        │
        ▼
Android Nativo (Kotlin)
├── Media          → Escaneo (MediaStore), detección MIME/códecs y carátulas
└── Playback       → MediaSessionService, ExoPlayer y notificación multimedia
```

## Requisitos de entorno
Flutter SDK: 3.47.9 (stable) o compatible

Dart SDK: 3.13.7 o compatible

Android SDK y dispositivo físico o emulador Android

Git

## Estructura inicial del repositorio
```text
offline_music/
├── .gitignore
├── README.md
└── docs/
│   └── decisions/
│       └── 001-modelos-y-sqlite.md
        └── 002-permisos-y-log.md
        └── 003-escaneo-y-sincronizacion-biblioteca.md
        └── 004-repositorio-biblioteca-y-artwork.md
        └── 005-capa-reproduccion-y-puente-nativo.md

│── lib/
     ├── core/
     │   └── log/
     │       └── app_log.dart
│    └── data/
          ├── datasources/
          │   └── local/
          │     └── library_database.dart
          └── models/
                ├── play_mode.dart
                └── song.dart
          └── permissions/
                ├── media_permission_gateway.dart
                └── permission_gateway.dart
          └── playback/
                ├── audio_player_service.dart
                ├── native_audio_player_service.dart
                ├── native_playback_service.dart
                └── native_playback_state.dart
                └── format_pipeline/
                        ├── alac_cache.dart
                        ├── alac_converter.dart
                        ├── format_service.dart
                        ├── preloader.dart
                        └── source_loader.dart
     └── services/
            ├── library_service.dart
            └── library_sync_service.dart
            └── artwork_service.dart
```
## Progreso de reconstrucción
Meta 1: Modelo Song, enum PlayMode, base de datos SQLite (LibraryDatabase) y registro de decisión docs/decisions/001-modelos-y-sqlite.md.

Meta 2: Utilidad de registro AppLog, contrato PermissionGateway, implementación MediaPermissionGateway y docs/decisions/002-permisos-y-log.md.

Meta 3: Escaneo nativo LibraryService / NativeLibraryService, detección de cambios LibrarySyncService y docs/decisions/003-escaneo-y-sincronizacion-biblioteca.md.

Meta 4: Servicio de carátulas y color dominante ArtworkService (lib/services/artwork_service.dart) y docs/decisions/004-repositorio-biblioteca-y-artwork.md.

Meta 5: Capa de reproducción (AudioPlayerService, NativePlaybackState, NativePlaybackService y NativeAudioPlayerService) y docs/decisions/005-capa-reproduccion-y-puente-nativo.md.

Meta 6 (actual): Pipeline de formatos (AlacCache, AlacConverter, FormatService, Preloader, SourceLoader) y docs/decisions/006-format-pipeline-y-cache-alac.md.

## Licencia
Pendiente de definir antes de la primera versión pública (v0.1.0).
