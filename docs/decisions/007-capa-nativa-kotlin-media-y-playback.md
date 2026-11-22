# ADR 007: Capa nativa Kotlin de medios, notificación y reproducción en segundo plano

- **Estado:** Aceptada
- **Ámbito:** Android Nativo (`android/app/src/main/kotlin/com/saladdann/offline_music/`)

## Contexto
Durante la migración desde `just_audio` y las referencias históricas a `app_offline` o `com.example.*`, los módulos nativos en Kotlin requerían unificar su paquete base bajo `com.saladdann.offline_music`, alinear sus canales y contratos con la capa Flutter (`offline_music/media`, `offline_music/playback` y `offline_music/playback_events`), y resolver fallos de compilación en `:app:checkDebugAarMetadata` (`compileSdk = 37` requerido por `:permission_handler_android`) y `:app:compileDebugKotlin` (firmas de métodos y referencias a recursos/dependencias).

## Decisión
- Estandarizar el paquete base en `com.saladdann.offline_music` para `MainActivity`, `media/` (`MediaConstants`, `AudioScanner`, `ArtworkExtractor`, `AudioMimeDetector`, `AudioDecoderChecker`, `WavHeaderWriter`, `AudioWavConverter`, `MediaChannelHandler`) y `media/playback/` (`MusicPlaybackService`, `MusicNotificationProvider`, `MusicPlaybackBridge`, `MusicPlaybackActionReceiver`, `MusicPlaybackStateHolder`, `MusicPlaybackConstants`).
- Mantener `compileSdk = 37` en `android/app/build.gradle.kts` para satisfacer los requisitos de metadatos AAR de dependencias como `:permission_handler_android`.
- Centralizar en `MusicPlaybackService` (`MediaSessionService` + `ExoPlayer`) el ciclo de vida del reproductor en segundo plano, la caché de `MediaItem` y el mapeo de cola por índices (`queueIndices`), exponiendo el estado y las acciones mediante `MusicPlaybackBridge` y `MusicPlaybackActionReceiver`, y construyendo la notificación personalizada (`R.layout.notification_media`) mediante `MusicNotificationProvider` y `RemoteViews`.

## Consecuencias
- **Positivas:** La compilación de Kotlin (`:app:compileDebugKotlin`) queda verificada sin errores bloqueantes y la arquitectura nativa separa escaneo/extracción de metadatos/conversión frente a sesión multimedia, cola y notificación.
- **Riesgos:** Persisten advertencias de Gradle/AGP sobre `compileSdk` frente a la versión máxima probada por AGP y migraciones futuras a Built-in Kotlin; además, la reproducción en segundo plano y la notificación personalizada dependen de los recursos XML (`layout`, `drawable`, `color`, `string`) y de pruebas en dispositivo físico para validar el comportamiento en ejecución.
