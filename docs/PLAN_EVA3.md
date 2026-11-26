# Plan de trabajo — Evaluación 3 (TI3042)

Estado al 26-11-2026. Punto de partida: commit `31cf7bb "ns"` (sincronización SQLite → Firebase de lecturas pendientes).

## Rúbricas a cumplir (las 3)

| Rúbrica | Documento de evidencia |
|---|---|
| A. Escala eva3 (Excel, 14 indicadores, 68,6 pts) | `docs/rubrica-eva3.md` |
| B. Rúbrica 3.1.x Sí/No (12 indicadores) | `docs/rubrica-u3.md` |
| C. Rúbrica 2.1.x (U2, 15 indicadores) | `docs/rubrica-u2.md` |

## Plan y estado

| # | Tarea | Indicadores | Estado |
|---|---|---|---|
| 1 | Búfer offline en la placa (120 lecturas, reenvío `HIST`) | 3.1.1.4, 3.1.3.8 | ✅ Hecho (falta probar en placa real) |
| 2 | App: recibir `HIST` y guardarlo con su hora | 3.1.1.4, 3.1.2.6 | ✅ Hecho |
| 3 | Sync Firebase con UID anónimo (sin correo), por lotes, al volver Internet | 3.1.2.7, 3.1.4.12, 3.1.3.10 | ✅ Hecho (falta habilitar Auth anónimo en la consola) |
| 4 | Reglas de Firebase (`firebase/database.rules.json`) | 3.1.4.11, 3.1.4.12 | ✅ Hecho (falta publicarlas en la consola) |
| 5 | Contador de pendientes y recuperadas en Monitoreo | 3.1.2.7 | ✅ Hecho |
| 6 | No borrar lecturas pendientes al limpiar la BD | 3.1.2.7 | ✅ Hecho |
| 7 | Celular cifrado también en DataStore | 3.1.4.12, 2.1.3.10 | ✅ Hecho |
| 8 | Quitar permisos sin uso (Bluetooth, ubicación) | 2.1.3.9, 3.1.4.12 | ✅ Hecho |
| 9 | Ahorro de energía en placa (loop lento sin app) y app (sync cada 5 min) | 3.1.3.10 | ✅ Hecho |
| 10 | Monitor serie muestra cada orden recibida | 3.1.3.9 | ✅ Hecho |
| 11 | Placa simulada con modo offline | Plan B | ✅ Hecho y probado |
| 12 | Tests `HistoryReadingTest` | 3.1.1.2 | ✅ 11/11 pasan |
| 13 | Docs: eva3, contingencia, energía, compatibilidad, guía del equipo | 3.1.3.10, 3.1.4.13, 3.1.4.14 | ✅ Hecho |
| 14 | Corregir README, U2, U3, Arduino README, JSON del mockup (decían BLE/ESP32/LCD/MQTT/Argon2) | 2.1.4.13–15 | ✅ Hecho |
| 15 | Compilar app y probar en emuladores API 24 y 34+ | 3.1.4.14 | ⏳ Equipo (en el entorno de Claude no hay Android SDK) |
| 16 | Compilar y cargar el sketch en la Wemos | todos 3.1.3 | ⏳ Equipo |
| 17 | Medir consumo con medidor USB y llenar tabla | 3.1.3.10 | ⏳ Equipo (opcional, suma) |
| 18 | Anotar retroalimentación del profe | 2.1.2.7, 2.1.4.15 | ⏳ Equipo |

## Bitácora

- **26-11-2026:** revisión contra las 3 rúbricas; tareas 1–14 implementadas. Ver `docs/GUIA_EQUIPO.md` para probar.
