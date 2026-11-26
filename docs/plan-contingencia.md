# Plan de contingencia y continuidad operativa (3.1.4.13)

Objetivo: que una falla de comunicación entre la app y la placa **nunca deje la compuerta en un estado inseguro**
ni haga perder datos, y que el sistema vuelva a operar solo.

## Principios

1. **Falla segura:** ante la duda, la compuerta se cierra y la turbina se apaga.
2. **La placa es autónoma:** el modo automático corre en la placa, no depende de la app.
3. **No perder datos:** placa (RAM, 1 h) → teléfono (SQLite, hasta 30 días) → nube (Firebase).
4. **Recuperación automática:** reconexión y sincronización sin intervención del usuario.

## Escenarios

| # | Falla | Cómo se detecta | Respuesta automática | Qué hace el usuario |
|---|---|---|---|---|
| 1 | La placa se apaga o reinicia | La app no recibe nada en 10 s o el socket se cierra | Reintenta 6 veces (1-2-4-8 s). Al arrancar, la placa deja la compuerta cerrada. | Si sale "No se pudo conectar", revisar alimentación y tocar Conectar. |
| 2 | El teléfono se aleja, se cierra la app o se corta el Wi-Fi | Placa: 15 s sin mensajes de la app | **Modo seguro:** cierra compuerta y apaga turbina, termina la sesión. Sigue midiendo, aplica el modo automático y guarda 1 lectura cada 30 s en el búfer. | Volver a conectarse; las lecturas del búfer llegan solas (`HIST`). |
| 3 | Pérdida de paquetes / interferencia | Saltos en la secuencia, ACK que no llega | Reenvía la orden hasta 2 veces; tarjeta "Estabilidad" muestra % de pérdida y latencia. | Si dice "Inestable", acercarse a la placa. |
| 4 | Mensaje alterado, repetido o de otro equipo | MAC inválido o secuencia vieja | Se descarta y se cuenta en "Rechazos"; no se ejecuta nada. | Ninguna. Si los rechazos suben mucho, revisar la red. |
| 5 | Clave del enlace incorrecta | WELCOME con MAC inválido | La app deja de reintentar y avisa "La placa no respondió con la clave correcta". | Revisar `local.properties` y `secrets.h`. |
| 6 | Falla el sensor DHT11 | Lectura `NaN` | La placa envía `ERR:DHT`; el modo automático no usa la humedad (solo el PIR). | Revisar el cable del DHT11 (D5). |
| 7 | Sin Internet en el teléfono | `NetworkCapabilities` sin `VALIDATED` | Las lecturas quedan en SQLite (`sincronizado = 0`). Al volver Internet, `NetworkCallback` dispara la subida. | Nada. Monitoreo muestra las pendientes. |
| 8 | Firebase rechaza o no está disponible | Excepción al subir | El lote queda pendiente y se reintenta en el próximo ciclo (máx. cada 5 min o al volver la red). | Revisar que esté habilitado el acceso anónimo en Firebase. |
| 9 | Búfer de la placa lleno (más de 1 h sin app) | `offCount == 120` | Se sobrescribe la lectura más antigua (`offDropped` cuenta cuántas). | Conectarse al menos una vez por hora para no perder historial. |
| 10 | Falla total del hardware el día de la presentación | — | **Modo demostración** en la app, o `tools/placa-simulada/placa_simulada.py` con el mismo protocolo seguro. | Ver `docs/GUIA_EQUIPO.md`. |

## Continuidad

- **Control manual:** desde la pestaña Ventilación se puede abrir o cerrar en modo manual. Si se pierde la app, la placa vuelve a cerrar a los 15 s (escenario 2).
- **Tiempo de recuperación:** reconexión automática en menos de 30 s después de que vuelve la placa (suma de esperas 1+2+4+8+8+8).
- **Respaldo de datos:** hasta 1 h en la placa, hasta 30 días de pendientes en el teléfono, y luego permanente en Firebase.

## Cómo probarlo en la presentación

1. Conectar la app, abrir la compuerta en manual y apagar el Wi-Fi del teléfono → a los 15 s la compuerta se cierra (escenario 2).
2. Esperar 1 a 2 minutos y volver a conectar → monitor serie: "[offline] lecturas por reenviar"; app: "Recuperadas de la placa" (3.1.1.4).
3. Desenchufar la placa con la app abierta → "Reconectando (n/6)…"; enchufarla → vuelve sola (escenario 1).
