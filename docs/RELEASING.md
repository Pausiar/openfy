# Guía de publicación de Openfy

Cómo se prepara y se publica una release: versión, firma, CI, web y descarga.

## 1. Requisitos

- JDK 17 y Android SDK (API 35) para compilar en local.
- Los secrets de firma release configurados en el repositorio (ver más abajo).
- Permisos `contents: write`, `pages: write` e `id-token: write` para los workflows.

## 2. Versión

La versión se declara en un único sitio, `version.properties`:

```properties
versionName=0.2.3
versionCode=10
```

- `versionName` es la versión visible (semver sin la "v").
- `versionCode` debe **subir** en cada publicación; Android lo usa para ordenar las
  actualizaciones.
- El tag de GitHub es siempre `v` + `versionName`. El workflow falla si no coinciden.

## 3. Firma de release

Openfy **nunca** publica una APK firmada con la clave de depuración: una APK con clave debug no
puede actualizarse sobre una versión firmada con otra clave y Android la marca como aplicación
no verificada. Si faltan las credenciales, la build falla con un mensaje explícito.

### Generar una clave (solo la primera vez, o para rotar)

```bash
# Linux / macOS
./scripts/create-release-keystore.sh --path ./openfy-release.p12 --alias openfy

# Windows
.\scripts\create-release-keystore.ps1 -KeystorePath .\openfy-release.p12 -Alias openfy
```

La clave se genera con RSA 4096, SHA256withRSA, PKCS#12 y 30 años de validez. Guarda el
archivo y las contraseñas en un lugar seguro: **la clave de release no se puede recuperar**.

### Subir la clave a GitHub Actions

```bash
gh secret set OPENFY_KEYSTORE_BASE64 --body "$(base64 -w 0 openfy-release.p12)"
gh secret set OPENFY_KEYSTORE_PASSWORD --body "<contraseña del keystore>"
gh secret set OPENFY_KEY_ALIAS --body "openfy"
gh secret set OPENFY_KEY_PASSWORD --body "<contraseña de la clave>"
```

### Certificados de firma (Play App Signing)

Si el proyecto se publica algún día en Google Play, la APK que se sube a Play debe firmarse con
la **clave de subida** de Play y la que se distribuye es la **clave de app** que genera Google.
En ese caso hay que actualizar `signing/release-certificate.txt` con la huella de la clave de
app, porque es la que verá el usuario final. La verificación que hace el CI en
`signing/release-certificate.txt` es justamente la que detecta ese cambio.

## 4. Publicar

```bash
# 1. Actualiza version.properties y haz commit
git add version.properties
git commit -m "release: 0.2.3"

# 2. Crea el tag y súbelo
git tag v0.2.3
git push origin main
git push origin v0.2.3
```

El workflow `.github/workflows/release.yml` hace el resto:

1. Comprueba que el tag coincide con `version.properties`.
2. Decodifica el keystore y **falla** si no hay secrets de firma.
3. Compila `:app:assembleRelease`.
4. Verifica la APK con `apksigner` y comprueba que el certificado coincide con
   `signing/release-certificate.txt`.
5. Publica la release con `openfy-v<versión>.apk`, `openfy-latest.apk` y `SHA256SUMS.txt`.
6. Despliega la web con la APK y el manifiesto `downloads/latest.json` actualizados.

Para relanzar una versión concreta sin tag:

```bash
gh workflow run release.yml -f tag=v0.2.3
```

## 5. Verificación manual

```bash
# La huella debe coincidir con la publicada en la web
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

# El hash debe coincidir con SHA256SUMS.txt
sha256sum -c SHA256SUMS.txt
```

## 6. Sistema de descarga

La web sirve la APK desde GitHub Pages (CDN) y usa GitHub Releases como espejo:

- `https://pausiar.github.io/openfy/downloads/openfy-latest.apk` → distribución principal.
- `https://github.com/Pausiar/openfy/releases/latest/download/openfy-latest.apk` → espejo.

`web/downloads/latest.json` es el manifiesto que consume la web (versión, tamaño, SHA-256 y
huella de firma). Lo genera:

```bash
# Con la APK en local
node scripts/generate-release-manifest.mjs --apk app-release.apk --tag v0.2.3

# Consultando la API de GitHub (lo que hace el workflow de Pages)
node scripts/generate-release-manifest.mjs --tag v0.2.3
```

Los binarios de `web/downloads/` están en `.gitignore`: los publica la CI, no se commitean.

## 7. Rotación de claves

Si la clave de release se pierde, deja de ser posible publicar actualizaciones instalables sobre
las versiones existentes. Procedimiento:

1. Genera una clave nueva con `scripts/create-release-keystore.*`.
2. Súbela a los secrets.
3. Actualiza `signing/release-certificate.txt` con la huella nueva.
4. Publica la release: el CI verificará la coherencia y fallará si no cuadra.

Los usuarios con una versión instalada firmada con la clave anterior tendrán que desinstalar la
app antes de instalar la nueva, porque Android no permite cambiar la clave de firma de un paquete
ya instalado.
