# Guía del equipo: preparar, probar y presentar

## 1. Preparar (una vez)

### App
1. Abrir la carpeta en **Android Studio** y dejar que Gradle sincronice.
2. En `local.properties` (no se sube a GitHub) agregar:
   ```
   aerosmart.linkKey=<32 caracteres hex, la misma de secrets.h>
   ```
   Para generar una clave: `python -c "import os;print(os.urandom(16).hex())"`.
3. Run 'app'.

### Placa
Ver `arduino/README.md`: copiar `secrets.example.h` como `secrets.h`, poner la misma clave y subir el sketch.

### Firebase (para la sincronización, 3.1.2.7)
1. Consola de Firebase → proyecto `aerosmart-99678` → **Authentication → Sign-in method → Anónimo → Habilitar**.
2. **Realtime Database → Reglas** → pegar el contenido de `firebase/database.rules.json` → **Publicar**.
3. Sin estos dos pasos, las lecturas quedan pendientes en el teléfono (no se pierden, pero no suben).

## 2. Lista de pruebas (marcar antes de presentar)

| # | Prueba | Esperado | OK |
|---|---|---|---|
| 1 | Correr `SecureChannelTest` y `HistoryReadingTest` | 11 pruebas en verde | |
| 2 | Registro con correo nuevo | Entra a la app | |
| 3 | Login con contraseña mala 5 veces | Bloqueo de 30 s | |
| 4 | Conectar a la placa | Datos reales en Inicio y Monitoreo | |
| 5 | Abrir/cerrar compuerta y cambiar velocidad | Servo y turbina responden, monitor serie muestra `[orden]` | |
| 6 | Pasar la mano por el PIR | "Movimiento detectado" al instante + notificación | |
| 7 | Desenchufar la placa y volver a enchufar | "Reconectando…" y vuelve sola | |
| 8 | Abrir compuerta y cortar Wi-Fi del teléfono | A los 15 s la placa cierra la compuerta | |
| 9 | 2 min sin app y reconectar | "Recuperadas de la placa: N" en Monitoreo | |
| 10 | Conectado a la placa (sin Internet) | "Nube: N lecturas pendientes" | |
| 11 | Volver a un Wi-Fi con Internet | Pendientes bajan a "al día"; datos en Firebase con UID | |
| 12 | Database Inspector | `password_hash` y `telefono_cifrado` ilegibles | |
| 13 | Emuladores API 24 y API 34+ | La app abre y funciona (ver `docs/compatibilidad.md`) | |

## 3. Guion de la demo (≈ 8 min)

1. **Problema y arquitectura** (`README.md`): por qué Wi-Fi (tabla en `docs/rubrica-u2.md`).
2. **Login/registro** y Database Inspector: hash y celular cifrado (2.1.3.10, 3.1.4.12).
3. **Conectar a la placa**: lecturas en vivo, PIR, abrir/cerrar (3.1.2.x, 3.1.3.x).
4. **Seguridad del enlace**: monitor serie, formato `SEQ|CUERPO|MAC`, rechazos (3.1.1.1–3.1.1.2).
5. **Fallas en vivo**: desenchufar placa → reconexión; cortar Wi-Fi → modo seguro → reconectar → historial recuperado (3.1.1.3, 3.1.1.4, 3.1.4.13).
6. **Nube**: pendientes → sincronizado; consola Firebase con UID anónimo (3.1.2.7).
7. **Energía y compatibilidad**: tablas de `docs/energia.md` y `docs/compatibilidad.md` (3.1.3.10, 3.1.4.14).

Plan B si falla el hardware: `tools/placa-simulada/placa_simulada.py` (mismo protocolo) o el **Modo demostración** de la app.

## 4. Preguntas probables del profe

| Pregunta | Respuesta corta |
|---|---|
| ¿Por qué Wi-Fi y no Bluetooth? | La Wemos solo trae Wi-Fi; más alcance y estabilidad (TCP), funciona sin Internet. La placa va enchufada, así que el consumo no es problema. |
| ¿Dónde está la "comunicación serial"? | El socket TCP es un flujo de bytes igual a un puerto serial; se envían líneas de texto terminadas en `\n`. |
| ¿Está cifrado el enlace? | El Wi-Fi WPA2 cifra con AES-CCMP; encima, cada mensaje va firmado con HMAC-SHA256 para que no se pueda alterar ni falsificar. El contenido no se cifra dos veces (es telemetría, sin datos personales). |
| ¿Qué pasa si alguien repite un mensaje? | Se rechaza por número de secuencia (antirepetición) y nonces nuevos en cada conexión. |
| ¿Cómo guardan las contraseñas? | Nunca en claro: PBKDF2 con sal y 120.000 iteraciones. |
| ¿Qué datos personales suben a la nube? | Ninguno: solo temperatura, humedad, presencia y compuerta, con un UID anónimo. |
| ¿Qué pasa si se corta la conexión? | Placa: modo seguro a los 15 s y guarda 1 h de lecturas. App: reconecta sola y guarda todo en SQLite hasta que haya Internet. |
| ¿Qué norma ISO aplican? | ISO/IEC 27400:2022 (IoT) como guía y controles de ISO/IEC 27001:2022 Anexo A (tabla en `docs/rubrica-u3.md`). |
| ¿Funciona en teléfonos viejos? | Desde Android 7.0 (API 24), en cualquier procesador porque no tiene código nativo. |
| ¿El código de verificación es real? | No, es simulado (1234) para mostrar el flujo. Sería el siguiente paso con Firebase Auth por correo. |

## 5. Limitaciones (decirlas si preguntan)

- La clave del enlace va dentro de la APK; en un producto real sería una clave por placa al emparejar.
- El búfer de la placa está en RAM: si se corta la luz se pierde (máx. 1 h).
- El `google-services.json` está en el repo: es normal en Firebase (no es secreto); la protección está en las reglas.
- La placa usa modo punto de acceso, que no permite dormir el radio (ver `docs/energia.md`).

## 6. Subir los cambios (somos colaboradores)

Si Claude no puede hacer push, se aplica el parche desde un PC con acceso al repo:
```bash
git checkout -b claude/cool-davinci-2ylw0e
git am aerosmart-eva3.patch
git push -u origin claude/cool-davinci-2ylw0e
```
