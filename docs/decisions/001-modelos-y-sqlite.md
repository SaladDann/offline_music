# ADR 001: Modelos de datos y persistencia local con SQLite

- **Estado:** Aceptada
- **Ámbito:** Capa de datos (`lib/data/`)

## Contexto
En versiones anteriores convivían duplicidades de modelos (`Song` y `SongModel`) y una base local (`offline_music.db`, tabla `library_songs`) utilizada para evitar consultas repetitivas al sistema de archivos/MediaStore y normalizar metadatos provenientes de distintas fuentes.

## Decisión
- Unificar el modelo de pista en `Song` (`lib/data/models/song.dart`) como clase inmutable con serialización `fromMap` / `toMap`, formato `MM:SS` y normalización de `dateModified` (segundos a milisegundos cuando el valor es menor a `100000000000`).
- Definir `PlayMode` (`order`, `repeatAll`, `repeatOne`, `shuffle`) en `lib/data/models/play_mode.dart` con rotación circular (`next`) y resolución segura `fromName`.
- Implementar `LibraryDatabase` (`lib/data/datasources/local/library_database.dart`) sobre SQLite (`offline_music.db`, tabla `library_songs`, índice en `title`) con apertura concurrente protegida y reemplazo atómico vía `transaction` + `batch` deduplicando por `id`.

## Consecuencias
- **Positivas:** Contrato único para persistencia, listas y reproducción; actualizaciones atómicas sin dejar la biblioteca a medias ante un error.
- **Riesgos:** La igualdad (`==` y `hashCode`) de `Song` se basa en `id`, por lo que `id` vacío o repetido en entradas malformadas debe filtrarse al construir catálogos/colas.
