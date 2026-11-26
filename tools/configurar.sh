#!/usr/bin/env bash
# Configura en 1 paso la conexión con la placa real (se corre una sola vez por PC).
#
#   bash tools/configurar.sh
#
# Pide la clave del enlace (la entrega el equipo, NO está en GitHub) y la clave del Wi-Fi de la placa,
# y crea/actualiza:
#   - local.properties                     (app Android: aerosmart.linkKey y sdk.dir)
#   - arduino/AeroSmartHome/secrets.h      (placa: LINK_KEY_HEX y AP_PASSWORD)
# Ninguno de los dos archivos se sube a GitHub (.gitignore).
set -euo pipefail

cd "$(dirname "$0")/.."
PROPS="local.properties"
SECRETS="arduino/AeroSmartHome/secrets.h"
TEMPLATE="arduino/AeroSmartHome/secrets.example.h"

# --- Clave del enlace ---
KEY="${1:-}"
while ! [[ "$KEY" =~ ^([0-9a-fA-F]{32}|[0-9a-fA-F]{64})$ ]]; do
  [[ -n "$KEY" ]] && echo "La clave debe tener 32 o 64 caracteres hexadecimales (0-9, a-f)."
  read -r -p "Clave del enlace (linkKey): " KEY
done
KEY="$(echo "$KEY" | tr 'A-F' 'a-f')"

# --- Clave del Wi-Fi de la placa ---
CURRENT_AP=""
[[ -f "$SECRETS" ]] && CURRENT_AP="$(sed -n 's/^#define AP_PASSWORD *"\(.*\)"/\1/p' "$SECRETS")"
AP="${2:-}"
while [[ ${#AP} -lt 8 ]]; do
  if [[ -n "$CURRENT_AP" && "$CURRENT_AP" != "cambia-esta-clave" ]]; then
    read -r -p "Clave del Wi-Fi de la placa [Enter = mantener la actual]: " AP
    [[ -z "$AP" ]] && AP="$CURRENT_AP"
  else
    read -r -p "Clave del Wi-Fi de la placa (mínimo 8 caracteres): " AP
  fi
  [[ ${#AP} -lt 8 ]] && echo "Debe tener al menos 8 caracteres."
done

# --- local.properties (app) ---
touch "$PROPS"
if ! grep -q '^sdk.dir=' "$PROPS"; then
  # Ruta por defecto del SDK de Android Studio en Windows; Android Studio la corrige sola si es otra.
  if [[ -n "${LOCALAPPDATA:-}" ]]; then
    SDK_WIN="$(cygpath -w "$LOCALAPPDATA" 2>/dev/null || echo "$LOCALAPPDATA")\\Android\\Sdk"
    echo "sdk.dir=$(echo "$SDK_WIN" | sed 's/\\/\\\\/g; s/:/\\:/')" >> "$PROPS"
  elif [[ -d "$HOME/Android/Sdk" ]]; then
    echo "sdk.dir=$HOME/Android/Sdk" >> "$PROPS"
  fi
fi
grep -v '^aerosmart.linkKey=' "$PROPS" > "$PROPS.tmp" || true
echo "aerosmart.linkKey=$KEY" >> "$PROPS.tmp"
mv "$PROPS.tmp" "$PROPS"

# --- secrets.h (placa) ---
[[ -f "$SECRETS" ]] || cp "$TEMPLATE" "$SECRETS"
AP_ESC="$(printf '%s' "$AP" | sed 's/[\/&|"]/\\&/g')"
sed -i "s|^#define LINK_KEY_HEX .*|#define LINK_KEY_HEX \"$KEY\"|; s|^#define AP_PASSWORD .*|#define AP_PASSWORD  \"$AP_ESC\"|" "$SECRETS"

echo
echo "Listo:"
echo "  - $PROPS        -> aerosmart.linkKey configurada"
echo "  - $SECRETS -> LINK_KEY_HEX y AP_PASSWORD configuradas"
echo
echo "Siguiente:"
echo "  1. Android Studio: File > Sync Project with Gradle Files y Run (o ./gradlew assembleDebug)."
echo "  2. Arduino IDE: abrir arduino/AeroSmartHome/AeroSmartHome.ino y subirlo a la Wemos."
echo "  3. Teléfono: Wi-Fi 'AeroSmart-Home' con la clave del Wi-Fi, y en la app Conectar > Placa AeroSmart."
