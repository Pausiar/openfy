#!/usr/bin/env bash
#
# Crea la clave de firma release de Openfy (RSA 4096, PKCS#12, 30 años).
#
# La clave de release es la identidad criptográfica de la app: sin ella no se pueden
# publicar actualizaciones instalables encima de la versión anterior.
#
# Uso:
#   ./scripts/create-release-keystore.sh --path ./openfy-release.p12 [--alias openfy]
#                                      [--store-password X] [--key-password X]
#                                      [--validity-days 10950] [--print-base64]
#
set -euo pipefail

KEYSTORE_PATH=""
ALIAS="openfy"
STORE_PASSWORD=""
KEY_PASSWORD=""
VALIDITY_DAYS=10950
DNAME="CN=Openfy Release, OU=Openfy, O=Pausiar, L=, ST=, C=ES"
PRINT_BASE64=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --path) KEYSTORE_PATH="$2"; shift 2 ;;
    --alias) ALIAS="$2"; shift 2 ;;
    --store-password) STORE_PASSWORD="$2"; shift 2 ;;
    --key-password) KEY_PASSWORD="$2"; shift 2 ;;
    --validity-days) VALIDITY_DAYS="$2"; shift 2 ;;
    --dname) DNAME="$2"; shift 2 ;;
    --print-base64) PRINT_BASE64=1; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "Argumento desconocido: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$KEYSTORE_PATH" ]]; then
  echo "Falta --path" >&2
  exit 1
fi

command -v keytool >/dev/null 2>&1 || { echo "No se ha encontrado keytool (necesitas un JDK 17)." >&2; exit 1; }

if [[ -z "$STORE_PASSWORD" ]]; then
  STORE_PASSWORD="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)"
fi
if [[ -z "$KEY_PASSWORD" ]]; then
  KEY_PASSWORD="$STORE_PASSWORD"
fi

if [[ -e "$KEYSTORE_PATH" ]]; then
  echo "El keystore ya existe: $KEYSTORE_PATH. No se sobrescribe." >&2
  exit 1
fi

mkdir -p "$(dirname "$KEYSTORE_PATH")"

echo "Generando clave de release de Openfy..."
keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -dname "$DNAME" \
  -validity "$VALIDITY_DAYS" \
  -keystore "$KEYSTORE_PATH" \
  -storetype PKCS12 \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD"

echo
echo "Keystore creado: $KEYSTORE_PATH"
echo "Alias: $ALIAS"
echo
echo "Huellas del certificado:"
keytool -list -v -keystore "$KEYSTORE_PATH" -storetype PKCS12 -storepass "$STORE_PASSWORD" \
  | grep -E 'Owner|Valid|SHA-?256|SHA-?1' || true
echo
echo "Store password: $STORE_PASSWORD"
echo "Key password  : $KEY_PASSWORD"

if [[ "$PRINT_BASE64" -eq 1 ]]; then
  echo
  echo "Base64 del keystore (para OPENFY_KEYSTORE_BASE64):"
  base64 -w 0 "$KEYSTORE_PATH"
  echo
fi

cat <<'EOF'

Para subirlo como secret de GitHub Actions:
  gh secret set OPENFY_KEYSTORE_BASE64 --body "$(base64 -w 0 openfy-release.p12)"
  gh secret set OPENFY_KEYSTORE_PASSWORD --body "<store password>"
  gh secret set OPENFY_KEY_ALIAS --body "openfy"
  gh secret set OPENFY_KEY_PASSWORD --body "<key password>"
EOF
