# ADR 005: Capa de reproducción y puente nativo de canales

- **Estado:** Aceptada
- **Ámbito:** Reproducción (`lib/data/playback/`)

## Contexto
En versiones anteriores existían dos implementaciones de reproducción con rutas, modelos (`SongModel` vs `Song`), nombres de canales (`app_offline/*` vs `offline_music/*`) y dependencias de pipelines futuros aún no construidos.

## Decisión
- Definir `NativePlaybackState` (`lib/data/playback/native_playback_state.dart`) y `AudioPlayerService` (`lib/data/playback/audio_player_service.dart`) sobre `Song` y `PlayMode`.
- Implementar `NativePlaybackService` (`lib/data/playback/native_playback_service.dart`) bajo `NativePlaybackServiceContract` usando `MethodChannel('offline_music/playback')` y `EventChannel('offline_music/playback_events')`, soportando `setLibrary`, `setQueue`, `setQueueByIndex`, `replaceUpcomingQueue`, `playbackError` y `favoriteToggled`.
- Implementar `NativeAudioPlayerService` (`lib/data/playback/native_audio_player_service.dart`) serializando operaciones de cola y resolviendo carátulas mediante `ArtworkService` sin depender de pipelines de conversión o precarga aún no creados.

## Consecuencias
- **Positivas:** Unifica canales y modelos en `lib/data/playback/` y mantiene el MVP desacoplado de módulos experimentales o futuros.
- **Riesgos:** Requiere correspondencia exacta en nombres de métodos, argumentos y mapas de eventos en la capa nativa Kotlin.
