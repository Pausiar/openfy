# Cómo contribuir

Gracias por querer mejorar Openfy. El proyecto es pequeño y abierto: las contribuciones
pequeñas y bien enfocadas son las más útiles.

## Antes de empezar

1. Revisa los [issues abiertos](https://github.com/Pausiar/openfy/issues) para no duplicar trabajo.
2. Para cambios grandes, abre primero un issue describiendo la idea y el motivo.

## Entorno de desarrollo

Requisitos:

- **JDK 17** (Android Gradle Plugin 8.5 no funciona con versiones anteriores).
- **Android SDK** con la API 35 y las build-tools 35.
- El *wrapper* de Gradle incluido en el repositorio (`./gradlew`).

```bash
git clone https://github.com/Pausiar/openfy.git
cd openfy
cp local.properties.example local.properties   # ajusta sdk.dir
./gradlew :app:assembleDebug
```

La clave de la API de YouTube es opcional: si no la defines, la app funciona igual.

## Estilo del código

- Kotlin con el estilo oficial (`kotlin.code.style=official` en `gradle.properties`).
- Composables con el prefijo `Openfy` cuando forman parte del sistema de diseño
  (`OpenfyCard`, `OpenfyTextField`, ...).
- Los tokens de color, forma y espaciado viven en `presentation/theme/`. No hardcodees colores ni
  medidas: la app (y la web) comparten el mismo sistema visual.
- Los textos de la interfaz, en español.

## Versión

La versión vive en `version.properties` y es la fuente única para la app, la web y las releases:

```properties
versionName=0.2.3
versionCode=10
```

Si tu cambio altera el comportamiento de la app, sube `versionCode`. El `versionName` lo gestiona
el mantenedor al preparar una release.

## Pull requests

- Una funcionalidad o una corrección por PR.
- Describe el motivo y, si aplica, cómo probarlo.
- Ejecuta `./gradlew :app:assembleDebug` antes de enviarlo.
- La CI compila la app y comprueba los flujos de trabajo; ambos deben pasar.

## Sitio web

La web vive en `web/` y es estática (HTML, CSS y JavaScript sin dependencias). Para verla en
local basta con abrir `web/index.html` o servir la carpeta:

```bash
python -m http.server 8000 --directory web
```

Reglas del sitio:

- Sin recursos de terceros (ni tipografías, ni analítica): es lo que permite que la web no
  necesite cookies ni banner de consentimiento.
- Los datos legales del titular se editan solo en `web/site-config.js`.
- El manifiesto de descarga (`web/downloads/latest.json`) lo genera
  `scripts/generate-release-manifest.mjs`; no lo edites a mano.

## Conducta

Al participar aceptas el [código de conducta](CODE_OF_CONDUCT.md).
