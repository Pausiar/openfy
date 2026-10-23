# Openfy

Openfy es una aplicación Android moderna para importar playlists por enlace, organizarlas localmente y reproducir contenido permitido dentro de la app.

La app reconoce enlaces de Spotify y YouTube, guarda metadatos localmente con Room, permite añadir archivos de audio del dispositivo, mantiene la reproducción en segundo plano con Media3 y ofrece apertura oficial en Spotify o YouTube cuando el contenido no puede reproducirse legalmente dentro de Openfy.

- **Web:** <https://pausiar.github.io/openfy/>
- **Código:** <https://github.com/Pausiar/openfy>
- **Descargas:** <https://github.com/Pausiar/openfy/releases>

## Versión actual

| | |
|---|---|
| Versión | `0.2.3` (`versionCode` 10) |
| Requisitos | Android 9 (API 28) o superior |
| Descarga principal | `https://pausiar.github.io/openfy/downloads/openfy-latest.apk` |
| Espejo | `https://github.com/Pausiar/openfy/releases/latest/download/openfy-latest.apk` |
| Licencia del código | MIT · el binario se distribuye bajo GPL-3.0 (ver [Licencias](#licencias-y-cumplimiento-legal)) |

La versión se declara en un único sitio: [`version.properties`](version.properties). La app, la
web, los workflows de CI y el manifiesto de descarga la leen de ahí, de modo que no puede
desincronizarse.

### Novedades

**0.2.3**

- Importación de playlists, álbumes y enlaces cortos de Spotify sin necesidad de proxy externo.
- La APK release se firma siempre con la clave de release oficial: el build falla si faltan las
  credenciales, en lugar de publicar una APK firmada con la clave de depuración.
- Firma con los esquemas v2 y v3, verificada con `apksigner` en CI y con la huella publicada
  en la web.
- La app muestra su propia huella de firma en *Ajustes → Información legal*.
- Sitio web rediseñado con el sistema de diseño de la app (paleta "Ember", tema claro/oscuro).
- Descarga servida desde el CDN de GitHub Pages, con espejo de GitHub Releases, suma SHA-256 y
  comprobación de disponibilidad en tiempo real.
- Textos legales revisados (aviso legal, privacidad, cookies, condiciones de uso y licencias) y
  ficheros `LICENSE`, `THIRD_PARTY_NOTICES.md`, `SECURITY.md`, `CONTRIBUTING.md` y
  `CODE_OF_CONDUCT.md`.

**0.2.2** — Reproducción más rápida (resolución de streams bajo demanda y precarga de la
siguiente canción), rediseño completo de la interfaz y correcciones de rendimiento.

**0.2.1** — Nuevo `applicationId` (`com.pausiar.openfy.player`) para que la app se instale como
una aplicación nueva.

**0.2.0** — Rediseño completo de la interfaz y mejoras de reproducción.

## Web

La web vive en [`web/`](web/) y es estática: HTML, CSS y JavaScript sin dependencias ni recursos
de terceros. Se despliega automáticamente con GitHub Actions en cada push a `main`
(`.github/workflows/pages.yml`).

**Configuración necesaria una sola vez:**

1. Ve a **Settings → Pages** en el repositorio.
2. En **Source**, selecciona **GitHub Actions** (no "Deploy from a branch").
3. El workflow se encarga del resto en cada push a `main`.

> Si Pages está configurado en "Deploy from a branch → main / (root)", se mostrará el README
> en vez de la landing. El source correcto es **GitHub Actions**.

La web replica el sistema de diseño de la app: los mismos colores (`presentation/theme/Color.kt`),
radios, espaciado y jerarquía tipográfica, con tema oscuro por defecto y conmutador a tema claro.

### Sistema de descarga

La descarga es un sistema con redundancia, no un único enlace:

- **CDN de GitHub Pages:** `downloads/openfy-latest.apk`, servido desde la CDN de Fastly, sin
  redirecciones ni límites de peticiones.
- **Espejo:** la release de GitHub, por si el CDN no está disponible.
- **Comprobación en vivo:** la web hace un `HEAD` sobre el archivo y, si no responde, cambia el
  botón al espejo automáticamente y avisa de ello.
- **Manifiesto:** `web/downloads/latest.json` (generado por CI) lleva versión, tamaño, SHA-256 y
  huella del certificado de firma.
- **Verificación:** cada release publica `SHA256SUMS.txt` y `.sha256`, y la web explica cómo
  comprobar la integridad del archivo descargado.

## Firma oficial

La APK que se publica está firmada con la clave de release del proyecto (RSA 4096), y esa huella
se publica para que cualquiera pueda verificar que el archivo descargado es el oficial.

```
Huella SHA-256 del certificado de release
78:B4:EC:CA:E9:6D:C9:74:28:87:7A:18:E7:E4:AD:1C:E4:7B:85:C0:BD:C0:CB:67:56:0D:B7:07:3E:A2:7A:64
```

Comprobación manual:

```bash
apksigner verify --print-certs openfy-latest.apk
```

La huella está guardada en [`signing/release-certificate.txt`](signing/release-certificate.txt) y
el CI falla la publicación si la APK compilada no está firmada con ese mismo certificado.

Reglas del proyecto:

- **Nunca** se publica una APK release firmada con la clave de depuración. `assembleRelease` falla
  con un mensaje explícito si no encuentra las credenciales de firma.
- La clave de release no está en el repositorio: vive cifrada en los secrets del repositorio y en
  una copia de seguridad local del mantenedor.
- La app muestra su propia huella en *Ajustes → Información legal*, para poder compararla con la
  publicada en la web.

Detalle completo del proceso en [`docs/RELEASING.md`](docs/RELEASING.md).

> **Sobre el aviso de Android al instalar.** Una app instalada fuera de Google Play muestra
> siempre un aviso de "origen desconocido" o "no verificada": Android no puede validar al
> desarrollador de un APK lateral. Lo que sí se controla (y este proyecto controla) es que la
> firma sea la oficial, que la clave no cambie entre versiones —para que las actualizaciones se
> instalen encima— y que el usuario pueda verificar la integridad del archivo. Publicar en
> Google Play es la única forma de eliminar el aviso de origen desconocido.

## Funciones principales

- Pegado de enlaces de Spotify o YouTube desde la pantalla inicial.
- Detección automática del tipo de enlace: playlist, álbum, canción, vídeo o enlace no válido.
- Importación de playlists de Spotify (incluidos enlaces cortos `spotify.link`) y de YouTube sin
  necesidad de proxy externo.
- Biblioteca local con búsqueda, filtros, ordenación, renombrado y borrado de playlists.
- Pantalla de detalle con lista de canciones, favoritos, reproducir, aleatorio y apertura del origen.
- Reproductor interno con cola, barra de progreso, play/pause, anterior, siguiente, repeat y shuffle.
- Reproducción en segundo plano mediante MediaSessionService y notificación multimedia.
- Importación de audio local con el selector de documentos del sistema.
- Playlist demo incluida con audio original para probar el reproductor sin depender de APIs externas.
- Exportación e importación de la biblioteca en JSON.
- Ajustes locales con DataStore: tema, autoplay, repeat, shuffle y última playlist abierta.
- Pantalla legal con los límites de uso y la huella de firma de la app.

## Limitaciones legales

Openfy no descarga audio protegido, no evita DRM, no bloquea anuncios de terceros y no intenta reproducir contenido completo de Spotify o YouTube sin permisos válidos.

La app solo reproduce dentro de Openfy:

- archivos locales del usuario,
- la demo incluida,
- previews oficiales si un backend seguro las suministra,
- streams autorizados.

Cuando una pista solo dispone de su URL original de Spotify o YouTube, Openfy muestra la referencia en la biblioteca y abre la app oficial o el navegador.

## Licencias y cumplimiento legal

- El código propio se publica bajo la **licencia MIT** ([`LICENSE`](LICENSE)).
- La APK incluye **NewPipeExtractor**, que es **GPL-3.0**. Como está enlazado dentro de la app, el
  binario distribuido se ofrece bajo los términos de la GPL-3.0 y el *Corresponding Source*
  completo está disponible en este repositorio público.
- El detalle de cada dependencia está en [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) y en
  [`web/licencias.html`](web/licencias.html).

Documentación legal del sitio:

- [`web/legal.html`](web/legal.html) — aviso legal (art. 10 LSSI).
- [`web/privacidad.html`](web/privacidad.html) — política de privacidad (RGPD y LOPDGDD).
- [`web/cookies.html`](web/cookies.html) — política de cookies (art. 22 LSSI). El sitio no usa
  cookies ni carga recursos de terceros.
- [`web/terminos.html`](web/terminos.html) — condiciones de uso.

> **Pendiente antes de publicar:** los datos identificativos del titular (nombre, NIF/CIF,
> domicilio y correo) están centralizados en [`web/site-config.js`](web/site-config.js) y se
> muestran como "pendiente de completar" mientras no se rellenen. El artículo 10 de la LSSI y el
> RGPD obligan a publicarlos.

## Stack técnico

- Kotlin
- Jetpack Compose + Material 3
- MVVM + Repository Pattern
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
- `data/remote`: integraciones con YouTube (Data API opcional o endpoints públicos) y Spotify (página pública de embed, con un proxy opcional).
- `data/repository`: lógica de importación, biblioteca local, historial y preferencias.
- `domain/models`: modelos tipados de negocio, filtros y backups.
- `domain/usecases`: casos de uso para importar, observar playlists y exportar/importar biblioteca.
- `presentation`: Compose UI, componentes reutilizables, navegación y ViewModels.
- `presentation/theme`: el sistema de diseño "Ember" (color, forma, espaciado y tipografía) que comparten la app y la web.
- `player/controller`: controlador del `MediaController` y estado del mini-player.
- `playback`: `MediaSessionService` en foreground para segundo plano y notificación.
- `utils`: parser de enlaces, formatters y helpers de apertura externa.

## Importación de enlaces

### Soporte actual sin configuración extra

- **Spotify** (playlist, álbum o canción, incluidos los enlaces cortos `spotify.link`): Openfy lee la página pública de embed de Spotify (`open.spotify.com/embed/...`) para obtener título, artistas, duración, portada y preview. La playlist debe ser pública. Si tiene más de ~100 canciones, se intenta completar con la Web API usando el token anónimo del embed (hasta 2000); si no es posible, se importan las que trae el embed.
- Las canciones de Spotify se reproducen buscando "artista - título" en YouTube.
- **YouTube** (playlist o vídeo): crea una referencia local, que puede enriquecerse con la API oficial.

### Soporte enriquecido opcional

- **YouTube:** si configuras `OPENFY_YOUTUBE_API_KEY`, Openfy consulta la YouTube Data API para traer títulos, descripciones, miniaturas y la lista de elementos.
- **Spotify (opcional):** si configuras `OPENFY_SPOTIFY_METADATA_BASE_URL`, Openfy prueba primero ese backend y, si no responde, usa automáticamente el embed público.

## Configuración local

1. Copia `local.properties.example` a `local.properties`.
2. Ajusta `sdk.dir` a tu SDK Android.
3. Rellena opcionalmente:

```properties
OPENFY_YOUTUBE_API_KEY=tu_api_key
OPENFY_SPOTIFY_METADATA_BASE_URL=https://tu-backend-seguro/
```

### Firma release en local

```properties
OPENFY_KEYSTORE_PATH=ruta/al/openfy-release.p12
OPENFY_KEYSTORE_PASSWORD=tu_contraseña
OPENFY_KEY_ALIAS=openfy
OPENFY_KEY_PASSWORD=tu_contraseña
```

Genera la clave con `scripts/create-release-keystore.ps1` (Windows) o
`scripts/create-release-keystore.sh` (Linux/macOS). Si no defines estas variables,
`assembleDebug` funciona igual, pero `assembleRelease` falla a propósito.

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

## Compilar y ejecutar

### Requisitos

- JDK 17
- Android SDK (API 35)
- Un dispositivo Android o emulador

```powershell
cd .\openfy
.\gradlew.bat :app:assembleDebug
```

APK de depuración esperado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Developing del sitio web

```bash
python -m http.server 8000 --directory web
```

El manifiesto de descarga se regenera con:

```bash
node scripts/generate-release-manifest.mjs --tag v0.2.3
```

## Exportar e importar biblioteca

Desde Ajustes puedes:

- exportar playlists, canciones, favoritos, historial y preferencias a JSON,
- importar un JSON generado por Openfy para restaurar el estado local,
- borrar toda la biblioteca del dispositivo.

## Experiencia de reproducción

- Mini-player fijo en la parte inferior.
- Pantalla completa de reproducción con progreso y cola.
- Controles desde la notificación multimedia.
- Servicio en foreground para segundo plano.
- Historial local de reproducción.

## Estado actual

Incluido en esta base:

- UI principal moderna en Compose.
- Parser de enlaces.
- Biblioteca local con Room.
- Importación de audio local.
- Servicio Media3 funcionando para contenido permitido.
- Demo original incluida.
- Export/import JSON.
- Firma de release verificada en CI y huella publicada.

Pendiente natural para iteraciones futuras:

- tests UI e instrumentados,
- paginación de playlists largas de YouTube,
- editor de cola manual,
- carátulas locales extraídas de archivos,
- integración con backend real para Spotify.

## Contribuir

Lee [`CONTRIBUTING.md`](CONTRIBUTING.md) y [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md). Los
problemas de seguridad se reportan en privado según [`SECURITY.md`](SECURITY.md).

## Licencia

Código bajo [licencia MIT](LICENSE). El binario distribuido se ofrece bajo GPL-3.0 por la
dependencia NewPipeExtractor: ver [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
