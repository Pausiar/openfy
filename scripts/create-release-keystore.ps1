<#
.SYNOPSIS
    Crea la clave de firma release de Openfy (RSA 4096, PKCS#12, 30 años).

.DESCRIPTION
    La clave de release es la identidad criptográfica de la app. Con ella:
      - las actualizaciones se instalan encima de la versión anterior,
      - la APK se puede verificar con su huella SHA-256 publicada en la web,
      - Android no la marca como "app firmada con clave de depuración".

    La clave NUNCA se sube al repositorio: guárdala en un sitio seguro y
    respalda el archivo .p12 junto con su contraseña.

.EXAMPLE
    .\scripts\create-release-keystore.ps1 -KeystorePath ..\openfy-release.p12 -Alias openfy

.EXAMPLE
    .\scripts\create-release-keystore.ps1 -KeystorePath ..\openfy-release.p12 -Alias openfy -StorePassword "s3cr3t" -KeyPassword "s3cr3t"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$KeystorePath,

    [string]$Alias = "openfy",

    [string]$StorePassword,

    [string]$KeyPassword,

    [int]$ValidityDays = 10950,

    [string]$DistinguishedName = "CN=Openfy Release, OU=Openfy, O=Pausiar, L=, ST=, C=ES"
)

$ErrorActionPreference = 'Stop'

function Resolve-Keytool {
    $candidates = @()

    $javaHome = $env:JAVA_HOME
    if ($javaHome) { $candidates += (Join-Path $javaHome 'bin\keytool.exe') }

    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }

    $candidates += Get-ChildItem 'C:\Program Files\Java' -Recurse -Filter 'keytool.exe' -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }

    throw 'No se ha encontrado keytool. Instala un JDK 17 (Temurin) o define JAVA_HOME.'
}

if (-not $StorePassword) {
    $StorePassword = [System.Guid]::NewGuid().ToString('N') + [System.Guid]::NewGuid().ToString('N')
}
if (-not $KeyPassword) { $KeyPassword = $StorePassword }

$resolvedPath = [System.IO.Path]::GetFullPath($KeystorePath)
if (Test-Path $resolvedPath) {
    throw "El keystore ya existe: $resolvedPath. No se sobrescribe (una clave de release no se regenera)."
}

$parent = Split-Path -Parent $resolvedPath
if ($parent -and -not (Test-Path $parent)) {
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
}

$keytool = Resolve-Keytool
Write-Host "Generando clave de release de Openfy con $keytool" -ForegroundColor Cyan

& $keytool -genkeypair `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -sigalg SHA256withRSA `
    -dname $DistinguishedName `
    -validity $ValidityDays `
    -keystore $resolvedPath `
    -storetype PKCS12 `
    -storepass $StorePassword `
    -keypass $KeyPassword | Out-Null

if ($LASTEXITCODE -ne 0) { throw "keytool ha fallado con el código $LASTEXITCODE." }

Write-Host ''
Write-Host 'Keystore creado:' -ForegroundColor Green
Write-Host "  Ruta      : $resolvedPath"
Write-Host "  Alias     : $Alias"
Write-Host "  Validez   : $ValidityDays días (~$([math]::Round($ValidityDays / 365.25, 1)) años)"
Write-Host ''
Write-Host 'Huellas del certificado:' -ForegroundColor Green
$details = & $keytool -list -v -keystore $resolvedPath -storetype PKCS12 -storepass $StorePassword
# El texto de keytool depende del idioma del sistema, así que se buscan las
# líneas de huella por patrón y no por texto literal.
$details | Select-String -Pattern '^\s*(SHA-?1|SHA-?256)\s*:' | ForEach-Object {
    Write-Host "  $($_.Line.Trim())"
}
$details | Select-String -Pattern 'Owner|Propietario|Valid from|Válido desde' | ForEach-Object {
    Write-Host "  $($_.Line.Trim())"
}
Write-Host ''
Write-Host 'Contraseñas (guárdalas en tu gestor de contraseñas):' -ForegroundColor Green
Write-Host "  Store password : $StorePassword"
Write-Host "  Key password   : $KeyPassword"
Write-Host ''
Write-Host 'Para subirlo como secret de GitHub Actions:' -ForegroundColor Yellow
Write-Host '  gh secret set OPENFY_KEYSTORE_BASE64 --body "$(base64 -w 0 openfy-release.p12)"'
Write-Host '  gh secret set OPENFY_KEYSTORE_PASSWORD --body "<store password>"'
Write-Host '  gh secret set OPENFY_KEY_ALIAS --body "' + $Alias + '"'
Write-Host '  gh secret set OPENFY_KEY_PASSWORD --body "<key password>"'
