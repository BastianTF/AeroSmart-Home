# AeroSmart Home

Sistema IoT de ventilación inteligente: una app Android (Kotlin) controla una compuerta y una turbina
conectadas a una **Wemos D1 mini (ESP8266)** con sensores DHT11 (temperatura/humedad) y PIR (presencia).

**Asignatura:** Aplicaciones Móviles para IoT (TI3042).

## Problemática

En espacios cerrados (salas, oficinas, piezas) la humedad y el calor se acumulan y el aire se vicia.
AeroSmart Home mide el ambiente, abre o cierra una compuerta de ventilación con turbina de forma automática
o manual desde el celular, y guarda el historial aunque no haya Internet.

## Arquitectura real

```
App Android (Kotlin, MVVM)                         Wemos D1 mini (ESP8266)
 DeviceLink + SecureChannel  ── Wi-Fi WPA2 ──►  AeroSmartHome.ino
   socket TCP 3333 (flujo serial)                 DHT11 (D5), PIR (D6), servo SG90 (D7), turbina (RX)
   mensajes SEQ|CUERPO|HMAC                       búfer offline 1 h en RAM
 SQLite (usuarios + lecturas)
   └─ SyncRepository ── Internet ──► Firebase Realtime Database (UID anónimo)
```

- **Comunicación:** Wi-Fi. La placa crea su propia red WPA2 (`AeroSmart-Home`) y la app abre un socket TCP
  que se usa como canal serial. Por qué Wi-Fi y no Bluetooth/MQTT: ver [`docs/rubrica-u2.md`](docs/rubrica-u2.md).
- **Seguridad:** HMAC-SHA256 por mensaje, nonces por sesión, antirepetición, contraseñas con PBKDF2,
  datos personales cifrados con AES-256-GCM (Android Keystore). Detalle en [`docs/rubrica-u3.md`](docs/rubrica-u3.md).
- **Sin conexión:** la placa guarda lecturas y las reenvía al reconectar; la app guarda todo en SQLite y lo sube a
  Firebase cuando vuelve Internet.

## Documentación

| Documento | Para qué |
|---|---|
| [`docs/GUIA_EQUIPO.md`](docs/GUIA_EQUIPO.md) | **Empezar aquí.** Cómo compilar, cargar la placa, demostrar y responder al profe. |
| [`docs/rubrica-eva3.md`](docs/rubrica-eva3.md) | Evidencia por indicador de la escala de apreciación eva3 (Excel). |
| [`docs/rubrica-u3.md`](docs/rubrica-u3.md) | Evidencia por indicador de la rúbrica 3.1.x (Sí/No). |
| [`docs/rubrica-u2.md`](docs/rubrica-u2.md) | Evidencia por indicador de la rúbrica 2.1.x (U2). |
| [`docs/plan-contingencia.md`](docs/plan-contingencia.md) | Fallas de comunicación y cómo responde el sistema. |
| [`docs/energia.md`](docs/energia.md) | Consumo de energía y optimizaciones. |
| [`docs/compatibilidad.md`](docs/compatibilidad.md) | Versiones de Android y arquitecturas soportadas. |
| [`docs/PLAN_EVA3.md`](docs/PLAN_EVA3.md) | Plan de trabajo y bitácora de cambios. |
| [`arduino/README.md`](arduino/README.md) | Conexiones y carga del firmware. |

## Ejecutar rápido

1. Abrir la carpeta en Android Studio y sincronizar Gradle.
2. En `local.properties` agregar `aerosmart.linkKey=<32 hex>` (la misma clave de `arduino/AeroSmartHome/secrets.h`).
3. Run 'app' en un teléfono o emulador con Android 7.0+ (API 24).
4. Sin placa: usar **Modo demostración** en la app, o la placa simulada (`tools/placa-simulada/placa_simulada.py`).

## Equipo

Bastián Flores y equipo — TI3042.
