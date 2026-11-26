# Evaluación 3 (escala de apreciación, 68,6 pts): evidencia por indicador

Para cada indicador: **qué hay**, **dónde está** y **cómo mostrarlo**. Rutas cortas:
`app/` = `app/src/main/java/com/example/ventiladorambiental/`, `ino` = `arduino/AeroSmartHome/AeroSmartHome.ino`,
`sim` = `tools/placa-simulada/placa_simulada.py`.

## 3.1.1 Seguridad, integridad y reconexión (18 pts)

| Ind. | Qué hay | Dónde | Cómo mostrarlo |
|---|---|---|---|
| **3.1.1.1** Instrucciones cumplen estándares de envío seguro (4,2) | Cada mensaje `SEQ\|CUERPO\|MAC` firmado con HMAC-SHA256, nonces nuevos por conexión, antirepetición por secuencia, clave fuera del código (`local.properties`, `secrets.h`, ambos en `.gitignore`). Red WPA2. | `app/link/SecureChannel.kt`, `computeMac` y `handleLine` en `ino` | Mostrar el formato en el monitor serie y que la clave no está en GitHub. |
| **3.1.1.2** Integridad al codificar (4,2) | Si cambia 1 carácter el MAC no coincide → se descarta. Se valida formato, largo máximo 120 y rangos (`Telemetry.parse`, `HistoryReading.parse`). | `app/link/Telemetry.kt`, `isValidBody` en `ino` | Correr `SecureChannelTest` y `HistoryReadingTest` (11 pruebas, pasan). |
| **3.1.1.3** Reconexión automática (4,8) | Reintentos con espera creciente 1-2-4-8 s (6 intentos); caída detectada si no llega nada en 10 s; latido PING cada 5 s; reenvío de órdenes sin ACK (1,5 s, 2 veces). La placa acepta la reconexión y arranca sesión nueva. | `app/link/DeviceLink.kt` (`runLoop`, `heartbeatLoop`, `retryPending`) | Apagar y prender la placa: la app muestra "Reconectando (n/6)…" y vuelve sola. Tarjeta "Estabilidad" cuenta reconexiones. |
| **3.1.1.4** Almacenamiento temporal en móvil **y** microcontrolador sin conexión (4,8) | **Placa:** búfer circular en RAM de 120 lecturas (1 cada 30 s = 1 hora). Al reconectar las reenvía como `HIST:edad,t,h,pir,compuerta`. **Móvil:** SQLite guarda todo con `sincronizado = 0` hasta que haya Internet. | `storeOffline` / `flushOneOffline` en `ino`; `app/data/ReadingRepository.kt` | Desconectar la app 2 min, reconectar: monitor serie dice "[offline] lecturas por reenviar: N" y Monitoreo muestra "Recuperadas de la placa: N". Sin placa: `AEROSMART_OFFLINE_EVERY=5 python placa_simulada.py`. |

## 3.1.2 Comunicación serial y almacenamiento en la app (15 pts)

| Ind. | Qué hay | Dónde | Cómo mostrarlo |
|---|---|---|---|
| **3.1.2.5** Envía instrucciones desde la app (4,8) | `VENT:0/1`, `FAN:0-100`, `MODE:A/M`, `PING` firmados. | `Commands` en `app/link/Telemetry.kt`, `DeviceLink.send` | Pestaña Ventilación: abrir/cerrar, velocidad. |
| **3.1.2.6** Recibe instrucciones en la app (4,8) | `DATA`, `HIST`, `ACK`, `PONG`, `ERR` verificados y procesados. | `DeviceLink.readLoop` → `MainViewModel.onDeviceMessage` | Inicio y Monitoreo con datos reales; pasar la mano por el PIR. |
| **3.1.2.7** Almacenamiento temporal en la app + sincronización externa al volver la conexión (5,4) | SQLite (`lecturas.sincronizado`). `SyncRepository` sube lotes de 100 a **Firebase Realtime Database** cuando aparece Internet validado (`NetworkCallback`), con UID anónimo. Reglas de Firebase en `firebase/database.rules.json`. Las pendientes no se borran (se guardan hasta 30 días). | `app/data/SyncRepository.kt`, `app/data/ReadingRepository.kt` | Conectado a la placa (sin Internet), Monitoreo muestra "Nube: N lecturas pendientes". Desconectar de la placa y volver a Wi-Fi con Internet → baja a "al día". En la consola de Firebase aparecen en `lecturas_iot/<uid>`. |

## 3.1.3 Microcontrolador y consumo de energía (20,6 pts)

| Ind. | Qué hay | Dónde | Cómo mostrarlo |
|---|---|---|---|
| **3.1.3.8** Envía desde el microcontrolador (12,8) | `DATA` cada 3 s y al instante cuando cambia el PIR o la compuerta; `HIST` al reconectar; `ACK`, `PONG`, `ERR:DHT`, `ERR:RANGO`, `ERR:CMD`. | `sendData`, `flushOneOffline`, `handleCommand` en `ino` | Monitor serie + la app actualizándose. |
| **3.1.3.9** Recibe en el microcontrolador (4,2) | `readClient` → `handleLine` verifica MAC/secuencia y `handleCommand` ejecuta (servo y turbina). | `ino` | Abrir desde la app: el servo gira; el monitor serie muestra rechazos si se manipula. |
| **3.1.3.10** Consumo de energía (sensores y móvil) (3,6) | Placa: potencia RF 14 dBm, servo con `detach` tras moverse, turbina apagada con compuerta cerrada, loop más lento sin app. App: escritura en BD cada 30 s, sincronización en lotes cada 5 min o al volver la red (sin sondeo), latido cada 5 s. | `docs/energia.md` | Presentar la tabla de `docs/energia.md` (con la medición del medidor USB si la hacen). |

## 3.1.4 ISO, datos personales, continuidad y compatibilidad (15 pts)

| Ind. | Qué hay | Dónde | Cómo mostrarlo |
|---|---|---|---|
| **3.1.4.11** Conexión cumple estándares ISO (4,8) | Tabla de controles ISO/IEC 27001:2022 Anexo A + ISO/IEC 27400:2022 (IoT). | `docs/rubrica-u3.md` (sección de controles) | Presentar la tabla. |
| **3.1.4.12** Protección de datos personales en la conexión (4,2) | Por el enlace con la placa **no viaja ningún dato personal** (solo telemetría y órdenes). A la nube va solo telemetría con UID anónimo (sin correo ni nombre), por TLS (Firebase) y con reglas que solo dejan acceder a ese UID. Celular cifrado AES-256-GCM (Keystore) en SQLite y DataStore. Sin permisos de ubicación ni Bluetooth. `allowBackup=false`. | `app/data/SyncRepository.kt`, `firebase/database.rules.json`, `app/security/KeystoreCipher.kt`, `AndroidManifest.xml` | Database Inspector: celular ilegible. Consola Firebase: nodos con UID, sin correos. |
| **3.1.4.13** Plan de contingencia y continuidad (3) | Tabla de fallas → detección → respuesta automática → acción del usuario. Modo seguro de la placa (cierra a los 15 s sin app), búfer offline, modo manual, modo demostración. | `docs/plan-contingencia.md` | Simular 2 fallas en vivo (apagar placa, cortar Wi-Fi). |
| **3.1.4.14** Compatibilidad con versiones de Android y arquitecturas (3) | minSdk 24 (Android 7.0) a targetSdk 37; permisos según versión; sin código nativo → corre en arm64-v8a, armeabi-v7a, x86 y x86_64; tema claro/oscuro. | `docs/compatibilidad.md`, `app/build.gradle.kts` | Mostrar la matriz de pruebas y la app en 2 emuladores con API distinta. |

## Pruebas hechas en este repositorio

- `SecureChannelTest` (8) y `HistoryReadingTest` (3): **pasan** (compilado con Kotlin 2.0.21 en JVM).
- Placa simulada: se probó que guarda lecturas sin app y al conectarse reenvía `HIST` firmadas; un cliente de prueba verificó el MAC de cada una.
- **Pendiente de probar en Android Studio / placa real** (en el entorno de desarrollo no había Android SDK ni toolchain del ESP8266): compilar la app, cargar el sketch, prueba de sincronización con Firebase. Ver `docs/GUIA_EQUIPO.md`.
