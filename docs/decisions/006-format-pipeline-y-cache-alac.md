# ADR 006: Pipeline de formatos, inspección anticipada y caché WAV

- **Estado:** Aceptada
- **Ámbito:** Reproducción (`lib/data/playback/format_pipeline/`)

## Contexto
Las pistas de audio locales pueden requerir detección de MIME real o conversión de respaldo a WAV (especialmente formatos como ALAC) con control de caché en disco y deduplicación de operaciones concurrentes, evitando dependencias problemáticas como `FFmpegKit` y alineándose con el modelo `Song` y `AppLog`.

## Decisión
- Consolidar en `lib/data/playback/format_pipeline/` los módulos `AlacCache`, `AlacConverter`, `FormatService`, `Preloader` y `SourceLoader` sobre `Song` y `AppLog`.
- Mantener el límite por defecto de caché en `256 MB`, validación mínima `> 44` bytes, protección de rutas en uso y expulsión LRU por fecha de modificación en `AlacCache`.
- Deduplicar conversiones simultáneas por clave (`_inFlight` / `_nativeWavInFlight`) y delegar la conversión de audio a WAV al canal nativo `offline_music/media` (`convertAudioToWav`), con opción de inyectar un invocador en pruebas.

## Consecuencias
- **Positivas:** Evita conversiones duplicadas y bloqueos por caché ilimitada, manteniendo el núcleo de `format_pipeline/` desacoplado de la UI y del controlador de reproducción.
- **Riesgos:** La conversión a WAV en disco consume espacio y tiempo de E/S en el dispositivo, por lo que conviene medir su impacto en pruebas reales antes de activarla en producción.
