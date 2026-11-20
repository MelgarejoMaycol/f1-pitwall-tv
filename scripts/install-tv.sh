
#!/usr/bin/env bash
set -Eeuo pipefail

# Dispositivo Android TV físico
TV="192.168.101.5:5555"
PACKAGE="com.f1pitwall.tv"
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "Conectando con el televisor..."
adb start-server
adb connect "$TV" || true

STATE=$(adb -s "$TV" get-state 2>/dev/null || true)

if [[ "$STATE" != "device" ]]; then
    echo "ERROR: No se pudo conectar con el televisor."
    exit 1
fi

echo "Conectado al televisor físico."
echo "Compilando la aplicación..."

./gradlew :app:assembleDebug

if [[ ! -f "$APK" ]]; then
    echo "ERROR: No se encontró el APK."
    exit 1
fi

echo "Instalando actualización en el televisor..."
adb -s "$TV" install -r "$APK"

echo "Abriendo F1PitWallTV..."
adb -s "$TV" shell am force-stop "$PACKAGE"
adb -s "$TV" shell am start -n "$PACKAGE/.ui.MainActivity"

echo "Instalación finalizada correctamente."
