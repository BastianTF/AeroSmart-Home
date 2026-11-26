# Compatibilidad: versiones de Android y arquitecturas (3.1.4.14)

## Versiones de Android

| Parámetro | Valor | Significa |
|---|---|---|
| `minSdk` | 24 | Android 7.0 Nougat o superior (~98 % de los teléfonos en uso) |
| `targetSdk` / `compileSdk` | 37 | Cumple las reglas de la versión más nueva |
| Java / bytecode | 11 | Soportado por todas las versiones desde la API 24 |

Configuración en `app/build.gradle.kts`.

### Cómo se adapta según la versión

| Función | API mínima que usa | Qué se hace en versiones distintas |
|---|---|---|
| Permiso de notificaciones | 33 (Android 13) | Solo se pide en 13+; en versiones anteriores no hace falta (`Build.VERSION.SDK_INT >= TIRAMISU`). |
| Cifrado AES-256-GCM en Android Keystore | 23 | Disponible en todas las versiones soportadas. |
| `ConnectivityManager.activeNetwork` / `NetworkCallback` | 23 / 21 | Disponible en todas. |
| Socket sobre la red Wi-Fi (`Network.socketFactory`) | 21 | Evita que Android 10+ mande el tráfico por datos móviles cuando la red de la placa no tiene Internet. |
| Pantalla de inicio (`core-splashscreen`) | 23+ con respaldo | La biblioteca AndroidX entrega la misma pantalla en versiones viejas. |
| Bordes a pantalla completa (`SystemBars.kt`) | 21+ | Se ajusta en Android 15+ donde es obligatorio. |
| Tema oscuro (`values-night`) | 29 sistema | En versiones antiguas usa el tema claro. |

## Arquitecturas de hardware

La app está escrita solo en **Kotlin/Java** y no incluye bibliotecas nativas (`.so`), así que el mismo APK funciona en:

| ABI | Ejemplos |
|---|---|
| `arm64-v8a` | La mayoría de los teléfonos actuales |
| `armeabi-v7a` | Teléfonos antiguos de 32 bits |
| `x86_64` / `x86` | Emuladores de Android Studio, algunos Chromebook |

Para comprobarlo: **Build > Analyze APK** → no hay carpeta `lib/`.

Firmware: **ESP8266** (Wemos D1 mini). El protocolo es texto ASCII, así que la misma app sirve con un ESP32 o un Arduino con módulo Wi-Fi si se implementa el mismo protocolo.

## Pantallas

Layouts con `ConstraintLayout`/`ScrollView`, medidas en `dp`/`sp` y vista previa en 3 tamaños de teléfono (`tools/preview`).

## Matriz de pruebas (llenar en Android Studio)

| Dispositivo | Android (API) | ABI | Login/registro | Conexión placa | Sync Firebase | Resultado |
|---|---|---|---|---|---|---|
| Emulador Pixel 2 | 7.0 (24) | x86 | | | | |
| Emulador Pixel 5 | 11 (30) | x86_64 | | | | |
| Emulador Pixel 8 | 14 (34) o más | x86_64 | | | | |
| Teléfono físico del equipo | ___ | arm64-v8a | | | | |

Para crear los emuladores: **Device Manager > Create Device** y elegir la imagen de sistema de cada versión.
