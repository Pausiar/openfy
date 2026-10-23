# Política de seguridad

## Qué reportar

Si has encontrado una vulnerabilidad en Openfy, repórtala de forma privada. **No abras un issue
público** para un fallo de seguridad hasta que se haya corregido.

Canales disponibles:

- **Aviso de seguridad privado de GitHub**: *Security* → *Report a vulnerability* en
  <https://github.com/Pausiar/openfy/security/advisories/new>.
- Alternativamente, abre un issue normal indicando que requiere contacto privado.

## Qué incluir

- Descripción del problema y pasos para reproducirlo.
- Versión de Openfy afectada (`Ajustes → Información`).
- Modelo de Android y versión del sistema.
- Si es posible, trazas o registros relevantes.

## Qué no hacer

- No publiques detalles del fallo antes de que exista una versión corregida.
- No accedas a datos de terceros ni a dispositivos que no sean tuyos.
- No publiques datos personales de otras personas.
- No envíes carga maliciosa a los usuarios de la app.

## Alcance

Este proyecto distribuye la app de forma lateral, fuera de Google Play. Ten en cuenta:

- Las versiones antiguas siguen siendo instalables: un fallo corregido en la versión actual
  puede seguir existiendo en versiones anteriores ya descargadas.
- La app se conecta a servicios de terceros (YouTube, Spotify, Piped). Los problemas de esos
  servicios no son responsabilidad de este proyecto, aunque se reportarán al proveedor si
  afectan a la experiencia de uso.

## Respuesta

Habrá un acuse de recibo y se publicará una versión corregida en cuanto sea posible. Quienes lo
soliciten aparecerán agradecidos en la nota de la release, salvo que prefieran mantener el
anonimato.
