# ADR 004: Servicio de carátulas y color dominante

- **Estado:** Aceptada
- **Ámbito:** Servicios (`lib/services/artwork_service.dart`)

## Contexto
La interfaz del reproductor necesita obtener la ruta local de la carátula desde la capa nativa (`offline_music/media`, método `getAudioArtwork`) con límite de espera, validar que el archivo exista y extraer un color dominante sin fugas de recursos gráficos.

## Decisión
- Consolidar `ArtworkService` en `lib/services/artwork_service.dart` usando `Song`, `AppLog` y `MethodChannel('offline_music/media')` (`getAudioArtwork` con `{'path': path}` y `timeout` de 2 segundos).
- Verificar `File(result).existsSync()` y capturar `TimeoutException`, `PlatformException` y `MissingPluginException` devolviendo `null` de forma segura.
- Escalar la imagen a `48x48` para construir el histograma de color dominante y liberar siempre `ui.Image` y `ui.Codec` en `finally`.

## Consecuencias
- **Positivas:** Evita esperas indefinidas o bloqueos de UI ante archivos sin carátula o fallos del canal nativo, y previene fugas de memoria gráfica.
- **Riesgos:** Depende de que la capa nativa en Kotlin responda a `getAudioArtwork` con una ruta válida en el sistema de archivos.
