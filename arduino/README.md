# AeroSmart Home: placa Wemos D1 mini (ESP8266)

Sketch: `AeroSmartHome/AeroSmartHome.ino`

## Conexiones

| Componente | Pin del componente | Pin de la Wemos |
|---|---|---|
| DHT11 | VCC / DATA / GND | 3V3 / D5 / GND |
| PIR HC-SR501 | VCC / OUT / GND | 5V / D6 / GND |
| Servo SG90 | Rojo / Señal / Marrón | 5V / D7 / GND |
| Turbina | Base o gate del transistor (con resistencia de 1 kΩ) | RX |

Notas:
- La turbina **no** se conecta directo al pin: usa un transistor o MOSFET (ej. 2N2222, IRLZ44N) y un diodo en paralelo al motor (1N4007).
- Si la subida del sketch falla, desconecta un momento el cable del pin RX (la turbina) y vuelve a subir.
- El firmware actual **no usa LCD**: los eventos se ven en el monitor serie (115200 baudios).

## Preparar el Arduino IDE

1. **Archivo > Preferencias > Gestor de URLs adicionales**: `https://arduino.esp8266.com/stable/package_esp8266com_index.json`
2. **Herramientas > Placa > Gestor de tarjetas**: instala **esp8266** (versión 3.1 o superior).
3. **Herramientas > Administrar bibliotecas**: instala **DHT sensor library** (Adafruit) y **Adafruit Unified Sensor**.
4. Placa: **LOLIN(WEMOS) D1 R2 & mini**.

## Clave y red (no se suben al repositorio)

1. Copia `secrets.example.h` como `secrets.h` en la misma carpeta.
2. Cambia `AP_PASSWORD` (mínimo 8 caracteres) y `LINK_KEY_HEX` (32 caracteres hexadecimales).
3. Pon **la misma clave** en Android Studio, en `local.properties`:
   ```
   aerosmart.linkKey=TU_CLAVE_DE_32_CARACTERES
   ```

## Probar

1. Sube el sketch. El monitor serie muestra la red y la IP `192.168.4.1`.
2. En el teléfono Android: **Ajustes > Wi-Fi > AeroSmart-Home**. Si avisa que no hay internet, elige **Mantener conexión**.
3. En la app: **Conectar > Placa AeroSmart**. El monitor serie dice "sesión segura iniciada".
4. El monitor serie muestra los eventos: `[orden] VENT:1`, mensajes descartados, modo seguro, etc.

## Sin conexión (búfer offline)

- Sin app conectada la placa guarda **1 lectura cada 30 s** en RAM (hasta 120 = 1 hora; si se llena, se pisa la más antigua).
- Al reconectar las reenvía como `HIST:edad_s,temp,hum,pir,compuerta` (firmadas igual que todo) y el monitor serie dice "[offline] lecturas por reenviar: N".
- Si la app deja de hablar 15 s, la placa **cierra la compuerta y apaga la turbina** (modo seguro).
- Se pierde si se corta la luz (está en RAM a propósito, para no gastar la flash).
