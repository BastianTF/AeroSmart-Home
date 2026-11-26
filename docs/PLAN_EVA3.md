# Plan de trabajo — Evaluación 3 (TI3042)

Estado al 26-11-2026. Último commit revisado: `31cf7bb "ns"` (sincronización SQLite → Firebase de lecturas pendientes).

## Qué se hizo en los últimos commits

| Commit | Qué hizo |
|---|---|
| `55373cf upgrade` | Mejoras de conexión en la app (ConnectionUi, Home, Monitoreo) y firmware. |
| `caf0f57 optimizacion` | Tema oscuro, ajustes de perfil, permisos en Manifest, potencia Wi-Fi a 14 dBm en la placa. |
| `31cf7bb ns` | Columna `sincronizado` en SQLite (BD v2) + `SyncRepository` que sube lecturas pendientes a Firebase cuando vuelve Internet. |

## Rúbricas a cumplir (las 3)

- **A. Escala eva3 (Excel, 14 indicadores, 68,6 pts)** → `docs/rubrica-eva3.md`
- **B. Rúbrica 3.1.x Sí/No (12 indicadores, 100 pts)** → ya cubierta en `docs/rubrica-u3.md`
- **C. Rúbrica 2.1.x (U2, 15 indicadores)** → `docs/EVALUACION_TI3042_U2.md` (hay que corregirlo)

## Estado por indicador nuevo (Excel)

| Indicador | Estado | Falta |
|---|---|---|
| 3.1.1.1 / 3.1.1.2 Envío seguro e integridad | ✅ HMAC-SHA256, secuencia, tests | Solo documentar |
| 3.1.1.3 Reconexión automática | ✅ backoff 1-2-4-8 s | Solo documentar |
| 3.1.1.4 Almacenamiento temporal en móvil **y microcontrolador** sin conexión | ⚠️ móvil sí, placa **no** | Buffer circular de lecturas en la placa que se reenvía al reconectar |
| 3.1.2.5 / 3.1.2.6 App envía / recibe | ✅ | Solo documentar |
| 3.1.2.7 Almacenamiento temporal + sync externa | ⚠️ hecho en último commit | Revisar: usa el **correo del usuario** como clave en Firebase (dato personal en la nube); sin reglas de Firebase; mostrar en UI "pendientes / sincronizado" |
| 3.1.3.8 / 3.1.3.9 Placa envía / recibe | ✅ | Solo documentar |
| 3.1.3.10 Consumo de energía | ⚠️ parcial (14 dBm, escritura BD cada 30 s) | Medición/estimación de consumo (tabla mA por componente), modem-sleep en placa, intervalo adaptativo en la app |
| 3.1.4.11 Estándares ISO | ✅ tabla ISO 27001/27400 | Actualizar con la nube |
| 3.1.4.12 Datos personales | ⚠️ docs dicen "solo en el teléfono" y ya no es cierto | Usar ID anónimo (hash) en Firebase + reglas de acceso; corregir doc |
| 3.1.4.13 Plan de contingencia y continuidad | ⚠️ existe modo seguro en placa | Documento formal: escenarios de falla → respuesta (app y placa) |
| 3.1.4.14 Compatibilidad versiones Android y arquitecturas | ⚠️ minSdk 24, Kotlin puro | Documentar matriz (API 24–37, arm64/armv7/x86_64), probar en 2 emuladores |

## Problemas detectados (hay que arreglarlos sí o sí)

1. **Docs U2 y README dicen cosas que el código no hace**: BLE/ESP32, HC-05, MQTT, Argon2, 2FA OTP, TLS 1.3, RBAC, servo en GPIO18. El hardware real es **Wemos D1 mini (ESP8266) por Wi-Fi TCP**. Si el profe pregunta (2.1.4.13/2.1.4.15) se cae. → Reescribir con lo real.
2. **Firebase**: correo del usuario como ruta en la nube y sin reglas de seguridad documentadas.
3. `mockup/aeroSmart_firebase_database.json` describe ESP32/BLE → actualizar.
4. No hay buffer offline en la placa (3.1.1.4).

## Plan (resumido)

1. **Firmware**: buffer circular (~60 lecturas) cuando no hay app conectada; al reconectar envía `HIST` con las lecturas guardadas; modem-sleep cuando no hay cliente.
2. **App**: recibir `HIST` y guardarlo en SQLite; Firebase con ID anónimo (SHA-256 del correo), mostrar contador "pendientes de sincronizar"; ahorro de energía (menos frecuencia en segundo plano).
3. **Seguridad nube**: archivo `firebase/database.rules.json` (solo el usuario autenticado escribe su nodo).
4. **Tests**: unit tests de parseo `HIST` y del buffer; `placa_simulada.py` actualizado con modo sin conexión.
5. **Documentación** (`docs/`):
   - `rubrica-eva3.md`: evidencia + cómo demostrar, por cada indicador del Excel.
   - `plan-contingencia.md`: fallas y respuesta.
   - `energia.md`: consumo estimado y optimizaciones.
   - `compatibilidad.md`: matriz de versiones/arquitecturas y pruebas.
   - Corregir `EVALUACION_TI3042_U2.md`, `README.md`, `rubrica-u3.md` y el JSON del mockup para que digan lo que realmente hay.
   - `GUIA_EQUIPO.md`: cómo compilar, cargar la placa, demostrar cada criterio y posibles preguntas del profe con respuesta.
6. **Verificar**: correr tests y compilar (en este entorno no hay Android SDK; se intenta instalar, si no, se indica que lo prueben en Android Studio).
