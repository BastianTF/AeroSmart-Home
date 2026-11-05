# AeroSmart Home: placa Wemos D1 mini (ESP8266)

Sketch: `AeroSmartHome/AeroSmartHome.ino`

## Conexiones

| Componente | Pin del componente | Pin de la Wemos |
|---|---|---|
| DHT11 | VCC / DATA / GND | 3V3 / D5 / GND |
| PIR HC-SR501 | VCC / OUT / GND | 5V / D6 / GND |
| Servo SG90 | Rojo / Señal / Marrón | 5V / D7 / GND |
| Turbina | Base o gate del transistor (con resistencia de 1 kΩ) | RX |
| LCD 16x2 | RS / E / D4 / D5 / D6 / D7 | D1 / D2 / D3 / D4 / D0 / D8 |
| LCD 16x2 | VSS, RW, K / VDD / V0 / A | GND / 5V / potenciómetro 10 kΩ / 5V con 220 Ω |

Notas:
- La turbina **no** se conecta directo al pin: usa un transistor o MOSFET (ej. 2N2222, IRLZ44N) y un diodo en paralelo al motor (1N4007).
- Si la subida del sketch falla, desconecta un momento el cable del pin RX (la turbina) y vuelve a subir.
- D3, D4 y D8 son pines de arranque del ESP8266. Con la LCD conectada así arranca normal; si no arranca, revisa que nada fuerce esos pines a un nivel distinto.

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

1. Sube el sketch. La LCD muestra la IP `192.168.4.1`.
2. En el teléfono Android: **Ajustes > Wi-Fi > AeroSmart-Home**. Si avisa que no hay internet, elige **Mantener conexión**.
3. En la app: **Conectar > Placa AeroSmart**. La LCD muestra `APP` en la segunda línea.
4. Monitor serie (115200 baudios) muestra los eventos: sesión iniciada, mensajes descartados, etc.
