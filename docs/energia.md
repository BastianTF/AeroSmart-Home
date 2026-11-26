# Consumo de energía (3.1.3.10)

## Consumo estimado de la placa (valores de hoja de datos)

| Componente | Estado | Corriente típica | Notas |
|---|---|---|---|
| ESP8266 (Wemos D1 mini) | Wi-Fi como punto de acceso | 70–80 mA | En modo AP el radio no puede dormir; por eso se baja la potencia de transmisión. |
| ESP8266 | Pico al transmitir | hasta ~170 mA (a 20 dBm) | Con `setOutputPower(14)` el pico baja. |
| DHT11 | Midiendo / reposo | 0,5–2,5 mA / 0,1 mA | Se lee cada 3 s. |
| PIR HC-SR501 | Siempre | ~0,065 mA | Despreciable. |
| Servo SG90 | Moviéndose | 100–250 mA | Solo ~350 ms por movimiento. |
| Servo SG90 | Quieto sin señal (`detach`) | casi 0 mA de control | Sin `detach` el servo corrige posición y consume. |
| Turbina | Encendida | Según el motor | Solo con la compuerta abierta; velocidad por PWM (20–100 %). |

**Promedio estimado** con la compuerta cerrada: ~80 mA a 5 V ≈ **0,4 W** (≈ 9,6 Wh/día).
Con un power bank de 10.000 mAh (≈ 37 Wh) alcanza para ≈ 3–4 días sin turbina.

## Optimizaciones implementadas

### Placa (`arduino/AeroSmartHome/AeroSmartHome.ino`)
- Potencia de radio reducida a **14 dBm** (`WiFi.setOutputPower(14.0)`): alcanza para una pieza y consume menos que 20,5 dBm.
- **Servo con `detach()`** después de cada movimiento: no se generan pulsos PWM en reposo.
- **Turbina apagada** cuando la compuerta está cerrada (PWM = 0).
- **Loop más lento sin app** (50 ms en vez de 5 ms): el CPU pasa más tiempo en `delay`, que en el ESP8266 permite el ahorro automático del procesador.
- Búfer offline en **RAM** (no en flash): no gasta ciclos de escritura de la memoria flash.

### App Android
- Lecturas guardadas en SQLite **cada 30 s** como máximo, no en cada mensaje.
- Sincronización con Firebase **en lotes de 100** (`updateChildren`: un solo envío) y como máximo **cada 5 min**, o al volver Internet (`NetworkCallback`, sin sondeo).
- Latido PING cada **5 s** (lo justo para detectar caídas en 10 s).
- Sin escaneo de Wi-Fi ni Bluetooth, sin GPS.
- Tema oscuro (`values-night`): en pantallas OLED gasta menos.

## Cómo medirlo (recomendado para la presentación)

Con un **medidor USB** (tipo UM25C o similar) entre el cargador y la Wemos, anotar:

| Situación | Corriente medida | Potencia (5 V) |
|---|---|---|
| Sin app conectada, compuerta cerrada | ___ mA | ___ W |
| App conectada, compuerta cerrada | ___ mA | ___ W |
| Servo moviéndose (pico) | ___ mA | ___ W |
| Compuerta abierta + turbina al 60 % | ___ mA | ___ W |

En el teléfono: **Ajustes > Batería > Uso por app** después de 1 hora con la app conectada.

## Mejoras posibles (no implementadas)

- Cambiar el punto de acceso por modo estación (la placa se conecta al router), que permite el modem-sleep del ESP8266 (~15 mA).
- Leer el DHT11 cada 10 s cuando no hay app.
