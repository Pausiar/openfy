# Third-party notices

Openfy se apoya en bibliotecas de código abierto. Este documento enumera las dependencias
que se empaquetan en la APK y la licencia que cada una impone.

El código propio de Openfy (aplicación Android y sitio web) se publica bajo la licencia
**MIT**: ver [LICENSE](LICENSE).

> ⚠️ **Aviso importante sobre la licencia del binario.**
> La aplicación enlaza **NewPipeExtractor**, que se distribuye bajo la **GPL-3.0**. La GPL-3.0
> es copyleft: al enlazarse dentro de la aplicación, la APK compilada se ofrece bajo los mismos
> términos GPL-3.0. El *Corresponding Source* completo está disponible en este repositorio
> público, por lo que la obligación de ofrecer el código fuente se cumple.
>
> En la práctica: el código propio es MIT, pero **la app y su APK se distribuyen bajo la
> GPL-3.0**. Si en el futuro se quisiera mantener la GPL-3.0 fuera del binario, habría que
> sustituir NewPipeExtractor por una implementación propia o por consultas HTTP directas
> (InnerTube / Piped), que no enlazan código de terceros.

## Dependencias de la aplicación Android

### Licencia GPL-3.0

| Componente | Versión | Uso en Openfy |
|---|---|---|
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | v0.26.2 | Resolución de streams de audio de YouTube |

NewPipeExtractor incluye a su vez dependencias transitivas propias (entre ellas `jsoup`, con
licencia MIT). La lista exacta y vigente de todas las dependencias transitivas y sus licencias
se genera automáticamente en cada compilación por el Android Gradle Plugin en
`app/build/reports/licenses/`.

### Licencia Apache-2.0

| Componente | Versión | Uso en Openfy |
|---|---|---|
| `androidx.core:core-ktx` | 1.13.1 | Utilidades base de Android |
| `androidx.core:core-splashscreen` | 1.0.1 | Pantalla de inicio |
| `androidx.lifecycle:*` (runtime, viewmodel, compose) | 2.8.4 | Ciclo de vida y ViewModels |
| `androidx.activity:activity-compose` | 1.9.1 | Actividad host de Compose |
| `androidx.compose:*` (BOM) | 2024.09.00 | Interfaz de usuario |
| `androidx.compose.material3:material3` | 1.3.0 | Componentes Material 3 |
| `androidx.compose.material:material-icons-extended` | — | Iconos de Material |
| `com.google.android.material:material` | 1.12.0 | Componentes Material para Views |
| `androidx.navigation:navigation-compose` | 2.8.0 | Navegación entre pantallas |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Preferencias persistentes |
| `androidx.documentfile:documentfile` | 1.0.1 | Acceso a archivos del dispositivo |
| `androidx.room:*` | 2.6.1 | Base de datos local |
| `androidx.media3:*` (exoplayer, session, ui) | 1.4.1 | Reproducción de audio |
| `io.coil-kt:coil-compose` | 2.7.0 | Carga de portadas |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | Corrutinas |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.6.3 | Serialización JSON |
| `com.squareup.retrofit2:retrofit` | 2.11.0 | Cliente HTTP |
| `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` | 1.0.0 | Conversor de Retrofit |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Cliente HTTP de bajo nivel |
| `com.squareup.okhttp3:logging-interceptor` | 4.12.0 | Registro de peticiones (debug) |

Kotlin y el compilador de Android (AGP) se distribuyen bajo la licencia Apache-2.0.

## Sitio web

El sitio web **no utiliza ninguna dependencia de terceros**: ni tipografías externas, ni
scripts de analítica, ni reproductores incrustados, ni píxeles. El único recurso externo es el
logotipo del propio proyecto.

## Marcas de terceros

- **Spotify** es una marca de Spotify AB.
- **YouTube** es una marca de Google LLC.
- **Android** y **Google Play** son marcas de Google LLC.

Openfy no está afiliado, asociado, autorizado ni respaldado por Spotify AB, Google LLC ni por
ninguna de sus filiales. Las marcas se mencionan de forma meramente descriptiva.
