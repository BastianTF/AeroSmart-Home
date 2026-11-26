# Unidad 2 (rúbrica 2.1.x): evidencia por indicador

Corregido para que coincida con lo que **realmente** hace el código. Versiones anteriores de este documento
mencionaban BLE, ESP32, HC-05, MQTT, Argon2, TLS 1.3 y RBAC: **nada de eso está implementado**, no decirlo en la presentación.

Rutas cortas: `app/` = `app/src/main/java/com/example/ventiladorambiental/`.

## 2.1.1 Definición del proyecto

| Ind. | Evidencia |
|---|---|
| **2.1.1.1** Problemática y dispositivos | En piezas y salas cerradas se acumulan humedad y calor y el aire se vicia. Dispositivos: Wemos D1 mini (ESP8266), DHT11 (temperatura/humedad), PIR HC-SR501 (presencia), servo SG90 (compuerta), turbina con transistor, teléfono Android. Sistemas: app Android, SQLite local, Firebase Realtime Database. |
| **2.1.1.2** Funcionalidades clave | 1) Registro e inicio de sesión locales y **con cuenta de Google** (Firebase Auth). 2) Lecturas en vivo y gráfico. 3) Abrir/cerrar compuerta y velocidad de turbina. 4) Modo automático (en la placa) y reglas/horario del usuario (en la app). 5) Alerta por movimiento (notificación). 6) Estabilidad de la conexión. 7) Historial sin conexión y sincronización con la nube. 8) Perfil, tema oscuro y modo demostración. |
| **2.1.1.3** Herramientas según estándares Android | Android Studio + **Kotlin** (lenguaje oficial recomendado por Google), Gradle Kotlin DSL, **MVVM** con `AndroidViewModel` + `StateFlow` + corrutinas (guía oficial de arquitectura), ViewBinding, **Material Design 3**, AndroidX (DataStore, ViewPager2, SplashScreen), SQLite nativo, Firebase BoM. Firmware en Arduino IDE (núcleo ESP8266). |
| **2.1.1.4** Presentación de la propuesta | Usar `README.md` (problemática + arquitectura) y este documento. |

## 2.1.2 Diseño

| Ind. | Evidencia |
|---|---|
| **2.1.2.5** Método inalámbrico justificado | **Wi-Fi** (punto de acceso WPA2 en la placa + socket TCP). Ver la tabla de abajo. |
| **2.1.2.6** Mockup de la UI | `mockup/index.html` (abrir en el navegador) y vista previa de los layouts reales en `tools/preview` (3 tamaños de teléfono). Navegación: Splash → Onboarding → Login/Registro → 4 pestañas (Inicio, Monitoreo, Automatización, Ventilación) → Perfil. |
| **2.1.2.7** Validación con el docente | Registrar aquí los cambios pedidos por el profe y qué se ajustó (ej.: "pidió X → se hizo Y en commit Z"). |

### ¿Por qué Wi-Fi y no Bluetooth o MQTT?

| Criterio | Wi-Fi (elegido) | Bluetooth (BLE / HC-05) | MQTT |
|---|---|---|---|
| Hardware | La Wemos D1 mini **trae Wi-Fi integrado**; no hay que agregar módulos. | El ESP8266 **no tiene Bluetooth**; habría que agregar un HC-05. | Usa Wi-Fi igual, pero además un broker. |
| Distancia | 20–30 m en interior. | 10 m típico. | Ilimitada, pero depende de Internet. |
| Estabilidad | TCP entrega en orden y retransmite; además latido, ACK y reconexión propios. | Emparejamiento y caídas frecuentes con paredes. | Depende del broker y de Internet. |
| Consumo | Mayor en la placa (~80 mA), pero la placa va enchufada a USB. En el teléfono es bajo. | Menor. | Igual que Wi-Fi + conexión permanente a Internet. |
| Sin Internet | **Funciona** (red propia). | Funciona. | **No funciona** sin Internet. |

Conclusión: Wi-Fi local da más alcance y estabilidad sin depender de Internet, con el hardware que ya existe; el mayor consumo no es problema porque la placa está enchufada.

## 2.1.3 Implementación

| Ind. | Evidencia |
|---|---|
| **2.1.3.8** Kotlin con código organizado | Paquetes: `link/` (comunicación), `data/` (SQLite, DataStore, sync), `security/` (cifrado y hash), pantallas y `MainViewModel` (estado). |
| **2.1.3.9** Permisos | Solo los necesarios: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `POST_NOTIFICATIONS` (pedido en tiempo de ejecución en Android 13+). Se **quitaron** Bluetooth y ubicación porque la app no los usa (minimización de datos). Ver `AndroidManifest.xml`. |
| **2.1.3.10** Seguridad ISO 27400 | Contraseña con **PBKDF2-HMAC-SHA256** + sal, 120.000 iteraciones (`security/PasswordHasher.kt`). Celular cifrado **AES-256-GCM** con clave en **Android Keystore** (`security/KeystoreCipher.kt`). Bloqueo de 30 s tras 5 intentos fallidos (`LoginActivity`). **Inicio con Google** por Credential Manager + Firebase Auth (token firmado por Google, sin manejar contraseñas de Google; `auth/GoogleAuth.kt`). Enlace firmado con **HMAC-SHA256** + antirepetición. Nube con UID anónimo y reglas. `allowBackup=false`. Detalle de controles ISO en `docs/rubrica-u3.md`. |
| **2.1.3.11** Pruebas | Unitarias: `SecureChannelTest`, `HistoryReadingTest` (pasan). Funcionales: ver lista en `docs/GUIA_EQUIPO.md` (autenticación, comunicación, datos). |

**Ojo:** el código de verificación del registro (`1234`) es **simulado** (no se envía correo). Si el profe pregunta: es una maqueta del flujo de verificación; no es un segundo factor real.

## 2.1.4 Presentación

| Ind. | Qué mostrar |
|---|---|
| **2.1.4.12** Demostración | Guion en `docs/GUIA_EQUIPO.md`. |
| **2.1.4.13** Explicar decisiones | Tabla Wi-Fi vs Bluetooth vs MQTT; MVVM; SQLite + Firebase. |
| **2.1.4.14** Evidenciar seguridad | Database Inspector (hash y celular cifrado), monitor serie con mensajes rechazados, reglas de Firebase. |
| **2.1.4.15** Responder preguntas | Preguntas probables con respuesta en `docs/GUIA_EQUIPO.md`. |
