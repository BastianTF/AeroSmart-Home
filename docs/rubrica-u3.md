# TI3042 U3: evidencia por criterio de la rúbrica

Cada criterio indica **dónde está en el código** y **cómo demostrarlo** en la presentación.
Rutas cortas: `link/` = `app/src/main/java/com/example/ventiladorambiental/link/`, `ino` = `arduino/AeroSmartHome/AeroSmartHome.ino`.

## Arquitectura del enlace

```
Teléfono Android  ──Wi-Fi WPA2 (red AeroSmart-Home)──►  Wemos D1 mini (ESP8266)
  DeviceLink.kt  ◄─── canal serial TCP 3333: líneas SEQ|CUERPO|MAC ───►  AeroSmartHome.ino
  SecureChannel.kt                                                        DHT11, PIR, servo, turbina, LCD
```

## 3.1.1 Seguridad e integridad de la información

| Criterio | Evidencia | Cómo demostrarlo |
|---|---|---|
| 3.1.1.1 Envío de información segura | Cada mensaje lleva HMAC-SHA256 con clave compartida y sesión nueva por conexión (`link/SecureChannel.kt`, `computeMac` en el `ino`). La clave no está en el código: `local.properties` y `secrets.h` (ambos en `.gitignore`). | Mostrar el formato `SEQ\|CUERPO\|MAC` y que la clave no aparece en GitHub. |
| 3.1.1.2 Integridad al codificar | Si cambia un carácter, el MAC no coincide y se descarta. Se valida formato, largo máximo (120) y rangos de los datos (`Telemetry.parse`: temperatura −10…60, humedad 0…100). | Ejecutar `SecureChannelTest` en Android Studio: pruebas `tamperedDataIsRejected` y `telemetryRejectsImpossibleValues`. |
| 3.1.1.3 Cifrado o medidas de seguridad en la comunicación serial | Red WPA2 (cifrado AES-CCMP del Wi-Fi), autenticación HMAC por mensaje, antirepetición por número de secuencia, nonces aleatorios (SecureRandom en Android, generador por hardware `ESP.random` en la placa), máximo 1 teléfono conectado. | Monitor serie: enviar un mensaje repetido o con otra clave muestra "mensaje repetido descartado" / "MAC inválido". |

## 3.1.2 App → microcontrolador

| Criterio | Evidencia | Cómo demostrarlo |
|---|---|---|
| 3.1.2.4 Envía instrucciones | `DeviceLink.send` con `VENT:0/1`, `FAN:0-100`, `MODE:A/M`, `PING` (`link/Telemetry.kt`, objeto `Commands`). | Pestaña Ventilación: Abrir/Cerrar y deslizador de velocidad. |
| 3.1.2.5 Recibe en la app | `DeviceLink.readLoop` → `MainViewModel.onDeviceMessage` (DATA, ACK, PONG). | Inicio y Monitoreo muestran temperatura, humedad y PIR reales; el gráfico se llena. |
| 3.1.2.6 Sensores y actuadores responden | `handleCommand` en el `ino` mueve el servo y la turbina y responde `ACK` + `DATA` con el estado real. | Abrir desde la app: el servo gira y la LCD dice ABIERTA; la app confirma con el estado que manda la placa. |

## 3.1.3 Microcontrolador → app

| Criterio | Evidencia | Cómo demostrarlo |
|---|---|---|
| 3.1.3.7 Envía desde el microcontrolador | `sendData` cada 2 s y al instante cuando cambia el PIR; `PONG`, `ACK`, `ERR:DHT`. | Pasar la mano por el PIR: la app cambia a "Movimiento detectado" al momento. |
| 3.1.3.8 Recibe en el microcontrolador | `readClient` + `handleLine` verifican y ejecutan las órdenes. | LCD y monitor serie muestran cada orden recibida. |
| 3.1.3.9 Estabilidad bidireccional | Latido PING/PONG cada 3 s con latencia; reconexión automática (1, 2, 4, 8 s, 6 intentos); reenvío si no llega ACK en 1,5 s; conteo de pérdidas por saltos de secuencia. Si la app calla 15 s la placa cierra la compuerta (modo seguro). | Tarjeta "Estabilidad de la conexión" en Monitoreo. Apagar y prender la placa: la app muestra "Reconectando…" y vuelve sola. |

## 3.1.4 Seguridad de la conexión y datos personales

| Criterio | Evidencia | Cómo demostrarlo |
|---|---|---|
| 3.1.4.10 Estándares ISO | Ver tabla de controles ISO/IEC 27001:2022 abajo. | Presentar la tabla. |
| 3.1.4.11 Protección de datos personales | Celular cifrado con AES-256-GCM y clave en Android Keystore (`security/KeystoreCipher.kt`); `allowBackup="false"`; los datos quedan solo en el teléfono (SQLite local). | Mostrar la tabla `usuarios` con el celular ilegible (App Inspection > Database Inspector). |
| 3.1.4.12 Credenciales y datos sensibles | Contraseña con PBKDF2 + sal, 120.000 iteraciones (`security/PasswordHasher.kt`); consultas con parámetros (sin inyección SQL); bloqueo de 30 s tras 5 intentos; clave del enlace fuera del código. | Database Inspector: columna `password_hash`. Intentar 5 contraseñas malas. |

## Controles ISO/IEC 27001:2022 (Anexo A) aplicados

| Control | Cómo se aplica |
|---|---|
| 5.14 Transferencia de información | Protocolo firmado app ↔ placa, red WPA2 propia. |
| 5.17 Información de autenticación | Contraseñas solo como hash; clave del enlace en archivos locales no versionados. |
| 5.34 Privacidad y protección de datos personales | Celular cifrado, datos solo en el dispositivo, sin respaldo en la nube. |
| 8.5 Autenticación segura | Login contra SQLite con hash y bloqueo por intentos fallidos. |
| 8.20 Seguridad de redes | Red dedicada para la placa, máximo 1 cliente, puerto único. |
| 8.24 Uso de criptografía | HMAC-SHA256, AES-256-GCM (Keystore), PBKDF2. |
| 8.28 Codificación segura | Validación de entradas y rangos, largo máximo de línea, consultas parametrizadas. |

Para IoT también se pueden citar como referencia ISO/IEC 27400:2022 (seguridad y privacidad en IoT) y la Ley 19.628 sobre protección de la vida privada (Chile), modificada por la Ley 21.719 de protección de datos personales. Si el docente pide detalle legal, conviene confirmarlo en bcn.cl.

## Limitaciones conocidas (para mencionar con honestidad)

- La clave compartida va dentro de la APK; alguien con la APK podría extraerla. Para un producto real se usaría una clave distinta por placa, configurada al emparejar.
- Los mensajes se autentican (no se pueden alterar ni falsificar), pero el contenido viaja legible dentro de la red WPA2. El cifrado del contenido lo da la red Wi-Fi.
- El nonce de la placa viene del generador por hardware del ESP8266.
