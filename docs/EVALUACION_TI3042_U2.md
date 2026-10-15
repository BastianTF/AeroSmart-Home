# Evaluación Sumativa Unidad 2: Aplicaciones Móviles Android
**Asignatura:** Aplicaciones Móviles para IoT (TI3042)  
**Ponderación:** 25%  
**Proyecto:** AeroSmart Home  
**Estudiante:** Bastián Flores  
**Repositorio Oficial:** [AeroSmart-Home](https://github.com/BastianTF/AeroSmart-Home.git)  

---

## 1. Cumplimiento de Criterios de Evaluación

### Criterio 2.1.1: Identifica herramientas de desarrollo móvil, siguiendo estándar de diseño de aplicaciones Android
* **Herramientas:** Android Studio (Ladybug / Hedgehog), Kotlin 2.0, Gradle Kotlin DSL (`build.gradle.kts`).
* **Estándar de Diseño:** Android Material Design 3, Jetpack ViewBinding para enlace seguro de vistas, SplashScreen API nativa y arquitectura reactiva MVVM con `MainViewModel`, `StateFlow` y Coroutines (`kotlinx.coroutines`).

### Criterio 2.1.2: Produce aplicación móvil Android, con uso de conexiones inalámbricas pertinentes
* **Bluetooth Low Energy (BLE):** Seleccionado para la comunicación con la estación microcontrolada ESP32 por su consumo inferior a 15 mA y alcance de hasta 15 metros en recintos cerrados.
* **Bluetooth Classic (HC-05):** Soporte alternativo serial SPP transparente.
* **Wi-Fi / Broker MQTT:** Implementado para transmisión de telemetría hacia la nube o servidores industriales.

### Criterio 2.1.3: Emplea estándar ISO de seguridad de aplicaciones IoT, siguiendo lineamiento de industria OT
* **Norma ISO/IEC 27400:2022 (Seguridad y Privacidad en IoT):**
  1. *Cifrado en Reposo:* Tokens y preferencias protegidos con algoritmo AES-256 en Android Jetpack DataStore / EncryptedSharedPreferences.
  2. *Hashing de Credenciales:* Algoritmo Argon2 / SHA-256 con salt criptográfico individual; no se almacenan contraseñas en texto plano.
  3. *Verificación en Dos Pasos (2FA):* Código OTP de 4 dígitos enviado al correo registrado para validar identidad.
  4. *Canales Cifrados:* Conexiones seguras bajo TLS v1.3 y emparejamiento BLE con clave protegida (Passkey pairing) contra ataques Man-in-the-Middle (MitM).
  5. *Control de Acceso (RBAC):* Segmentación de permisos entre Administrador/Supervisor y Operador de campo.

### Criterio 2.1.4: Demuestra interconexión entre aplicaciones móviles mediante conexión inalámbrica
* **Simulación Multi-Dispositivo:** Se implementó una arquitectura maestro-esclavo:
  * **Dispositivo 1 (Master / Supervisor):** Configura umbrales de temperatura, conmuta el modo manual/automático y transmite órdenes de control.
  * **Dispositivo 2 (Esclavo / Cliente Monitor):** Recibe instantáneamente la telemetría reflejada y el estado del servomotor SG90 en tiempo real con acuse de recibo (ACK).

---

## 2. Desarrollo Paso a Paso según la Guía

### Paso 1: Definir el Proyecto
* **Problemática:** Automatización de la climatización y renovación de aire en espacios interiores cerrados ante aumentos críticos de temperatura y acumulación de calor metabólico humano, disminuyendo el gasto energético.
* **Dispositivos:** Sensor DHT11 (temperatura/humedad), sensor PIR (presencia humana), servomotor SG90 (compuerta 0°-90°) y turbina extractora.
* **Almacenamiento:** Arquitectura que soporta SQLite local (sin conexión), Firebase Authentication (nube) y API REST con MySQL (industrial).

### Paso 2: Diseñar la Solución
* **Método Inalámbrico:** Bluetooth BLE para enlaces locales directos y Wi-Fi/MQTT para telemetría distribuida.
* **Autenticación:** Credenciales cifradas con Argon2, validación en tiempo real y recuperación mediante enlaces seguros.
* **Mockup de Usuario (UI):** Prototipo interactivo autónomo en [`mockup/index.html`](../mockup/index.html).

### Paso 3: Construir la Aplicación
* **Lógica de Automatización:**
  * SI (Temperatura > 26°C Y Presencia == 1) -> ABRIR ESCOTILLA (90°) Y ACTIVAR TURBINA.
  * SI (Temperatura < 22°C O Presencia == 0) -> CERRAR ESCOTILLA (0°) Y APAGAR TURBINA.
* **Seguridad:** Cumplimiento íntegro de la norma ISO 27400.

### Paso 4: Presentación y Validación
* **Demostración en Clase:** Panel lateral de inyección de hardware con controles de temperatura, humedad, sensor PIR y consola serial a 115200 baudios.
* **Modo Dual:** Demostración simultánea de los dos dispositivos Android enlazados.
