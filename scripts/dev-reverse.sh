#!/usr/bin/env bash
# Expone el backend local (puerto 3000 del host) en localhost:3000 de cada
# dispositivo o emulador conectado. Requiere DEV_TARGET=device en local.properties.
# El reverse se pierde al reiniciar el emulador o desconectar el teléfono.
set -euo pipefail

PORT="${1:-3000}"
ADB="${ADB:-$(command -v adb || echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb")}"

if [[ ! -x "$ADB" ]]; then
  echo "No se encontró adb. Define ADB o ANDROID_HOME." >&2
  exit 1
fi

serials=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ -z "$serials" ]]; then
  echo "No hay dispositivos conectados (adb devices)." >&2
  exit 1
fi

for serial in $serials; do
  "$ADB" -s "$serial" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  echo "OK $serial: localhost:$PORT -> host:$PORT"
done
