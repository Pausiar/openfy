# Openfy

Openfy es una aplicacion Android moderna para importar playlists por enlace, organizarlas localmente y reproducir contenido permitido dentro de la app.

La app reconoce enlaces de Spotify y YouTube, guarda metadatos localmente con Room, permite anadir archivos de audio del dispositivo, mantiene reproduccion en segundo plano con Media3 y ofrece apertura oficial en Spotify o YouTube cuando el contenido no puede reproducirse legalmente dentro de Openfy.

## Web y GitHub Pages

La web de Openfy está en la carpeta `web/`. Se despliega automáticamente con GitHub Actions.

**Configuración necesaria una sola vez:**
1. Ve a **Settings → Pages** en el repositorio.
2. En **Source**, selecciona **GitHub Actions** (no "Deploy from a branch").
3. El workflow `.github/workflows/pages.yml` se encargará del resto en cada push a `main`.

URL esperada: `https://pausiar.github.io/openfy/`

> ⚠️ Si Pages está configurado en "Deploy from a branch → main / (root)", se mostrará el README
> en vez de la landing. El source correcto es **GitHub Actions**.


## Funciones principales

- Pegado de enlaces de Spotify o YouTube desde la pantalla inicial.
- Deteccion automatica de tipo de enlace:
  Spotify playlist, Spotify track, YouTube playlist, YouTube video o enlace no valido.
- Importacion local de playlists con fallback legal cuando faltan APIs.
- Biblioteca local con busqueda, filtros, ordenacion, renombrado y borrado de playlists.
- Pantalla de detalle con lista de canciones, favoritos, reproducir, aleatorio y apertura del origen.
- Reproductor interno con cola, barra de progreso, play/pause, anterior, siguiente, repeat y shuffle.
- Reproduccion en segundo plano para contenido permitido mediante MediaSessionService y notificacion multimedia.
- Importacion de audio local con el selector de documentos del sistema.
- Playlist demo incluida con audio original para probar el reproductor sin depender de APIs externas.
- Exportacion e importacion de biblioteca en JSON.
- Ajustes locales con DataStore:
  tema, autoplay, repeat, shuffle y ultima playlist abierta.
- Pantalla legal explicando limites de uso y ausencia de funciones ilegales.

## Limitaciones legales

Openfy no descarga audio protegido, no evita DRM, no bloquea anuncios de terceros y no intenta reproducir contenido completo de Spotify o YouTube sin permisos validos.

La app solo reproduce dentro de Openfy:

- archivos locales del usuario,
- la demo incluida,
- previews oficiales si un backend seguro las suministra,
- streams autorizados.

Cuando una pista solo dispone de su URL original de Spotify o YouTube, Openfy muestra la referencia en la biblioteca y abre la app oficial o el navegador.

## Stack tecnico

- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- Room Database
- DataStore Preferences
- Media3 / ExoPlayer
- Navigation Compose
- Coil
- Retrofit + Kotlinx Serialization

## Arquitectura

La app sigue una estructura MVVM con Repository Pattern:

```text
app/src/main/java/com/pausiar/openfy/
  data/
    local/
    remote/
    repository/
  domain/
    models/
    usecases/
  presentation/
    components/
    navigation/
    screens/
    theme/
    viewmodel/
  player/
    controller/
  playback/
  utils/
```

### Responsabilidades por capa

- `data/local`: Room entities, DAOs y base de datos.
- `data/remote`: integraciones permitidas con YouTube Data API y un proxy seguro opcional para Spotify.
- `data/repository`: logica de importacion, biblioteca local, historial y preferencias.
- `domain/models`: modelos tipados de negocio, filtros y backups.
- `domain/usecases`: casos de uso para importar, observar playlists y exportar/importar biblioteca.
- `presentation`: Compose UI, componentes reutilizables, navegacion y ViewModels.
- `player/controller`: controlador del `MediaController` y estado del mini-player.
- `playback`: `MediaSessionService` en foreground para segundo plano y notificacion.
- `utils`: parser de enlaces, formatters y helpers de apertura externa.

## Importacion de enlaces

### Soporte actual sin configuracion extra

- Spotify playlist: crea una referencia local de la playlist.
- Spotify track: crea una referencia local de la cancion.
- YouTube playlist: crea una referencia local y puede enriquecerse con API oficial.
- YouTube video: crea una referencia local con thumbnail publica.

### Soporte enriquecido opcional

- YouTube: si configuras `OPENFY_YOUTUBE_API_KEY`, Openfy consulta la YouTube Data API para traer titulos, descripciones, thumbnails y lista de items.
- Spotify: si configuras `OPENFY_SPOTIFY_METADATA_BASE_URL`, Openfy llama a un backend/proxy seguro para obtener metadatos y previews oficiales sin exponer secretos en el cliente.

## Configuracion local

1. Copia `local.properties.example` a `local.properties`.
2. Ajusta `sdk.dir` a tu SDK Android.
3. Rellena opcionalmente:

```properties
OPENFY_YOUTUBE_API_KEY=tu_api_key
OPENFY_SPOTIFY_METADATA_BASE_URL=https://tu-backend-seguro/
```

### Contrato esperado para el proxy de Spotify

Openfy espera dos endpoints base:

- `GET /playlist/{id}`
- `GET /track/{id}`

Con respuestas JSON compatibles con estas propiedades:

```json
{
  "id": "spotify-id",
  "title": "Nombre",
  "description": "Descripcion opcional",
  "imageUrl": "https://...",
  "originalUrl": "https://open.spotify.com/...",
  "tracks": [
    {
      "id": "track-id",
      "title": "Cancion",
      "artist": "Artista",
      "album": "Album",
      "durationMs": 123000,
      "imageUrl": "https://...",
      "originalUrl": "https://open.spotify.com/track/...",
      "previewUrl": "https://..."
    }
  ]
}
```

## Como ejecutar la app

### Requisitos

- JDK 17
- Android SDK instalado
- Un dispositivo Android o emulador

### Compilar en Windows

```powershell
cd .\openfy
.\gradlew.bat :app:assembleDebug
```

Si prefieres solo validar compilacion Kotlin:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

### Generar APK

```powershell
.\gradlew.bat :app:assembleDebug
```

APK esperado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Exportar e importar biblioteca

Desde Ajustes puedes:

- exportar playlists, canciones, favoritos, historial y preferencias a JSON,
- importar un JSON generado por Openfy para restaurar el estado local,
- borrar toda la biblioteca del dispositivo.

## Experiencia de reproduccion

- Mini-player fijo en la parte inferior.
- Pantalla completa de reproduccion con progreso y cola.
- Controles desde notificacion multimedia.
- Servicio en foreground para segundo plano.
- Historial local de reproduccion.

## Estado actual

Incluido en esta base:

- UI principal moderna en Compose.
- Parser de enlaces.
- Biblioteca local con Room.
- Importacion de audio local.
- Servicio Media3 funcionando para contenido permitido.
- Demo original incluida.
- Export/import JSON.

Pendiente natural para iteraciones futuras:

- tests UI e instrumentados,
- paginacion de playlists largas de YouTube,
- editor de cola manual,
- caratulas locales extraidas de archivos,
- integracion con backend real para Spotify.

## Capturas

No se han generado capturas dentro de este repositorio todavia. Puedes anadirlas mas adelante en una carpeta `captures/` y enlazarlas desde este README.

## Roadmap

1. Añadir tests de parser, repositorios y navegacion.
2. Mejorar permisos y onboarding para notificaciones multimedia.
3. Implementar seleccion de multiples carpetas de audio.
4. Añadir soporte de playlists locales mixtas con reordenacion manual.
5. Integrar backend seguro para Spotify con previews oficiales donde existan.