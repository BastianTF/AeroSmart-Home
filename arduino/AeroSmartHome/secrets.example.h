// Copia este archivo como "secrets.h" (en la misma carpeta) y cambia los valores.
// secrets.h NO se sube al repositorio (está en .gitignore).

// Red Wi-Fi que crea la placa. La contraseña debe tener al menos 8 caracteres (WPA2).
#define AP_SSID      "AeroSmart-Home"
#define AP_PASSWORD  "cambia-esta-clave"

// Clave compartida del enlace: 32 o 64 caracteres hexadecimales (0-9, a-f).
// Debe ser IDÉNTICA a aerosmart.linkKey en local.properties de Android Studio.
// Para generar una: en PowerShell ->  -join ((1..32) | % { '{0:x}' -f (Get-Random -Max 16) })
#define LINK_KEY_HEX "00000000000000000000000000000000"
