# offline_music

Reproductor de música local **offline** para Android desarrollado con **Flutter (Dart)** y componentes nativos en **Kotlin** (`AndroidX Media3` / `ExoPlayer`, `MediaSessionService` y `MediaStore`).

> **Estado del proyecto:** Reconstrucción limpia desde cero (fase de fundación). Las características arquitectónicas descritas abajo corresponden al diseño objetivo y se marcarán como implementadas únicamente tras su compilación y validación en dispositivo real.

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
```
## Licencia
Pendiente de definir antes de la primera versión pública (v0.1.0).
