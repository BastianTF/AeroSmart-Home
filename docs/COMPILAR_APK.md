# Compilar el APK en otro PC e instalarlo en un teléfono

Requisito: **Android Studio** instalado (trae Java y el SDK). Al abrirlo la primera vez, completar el asistente (**Next → Standard → Finish**) para que descargue el SDK.

Todos los comandos van en **Git Bash** (o la terminal UCRT64).

## 1. Descargar el proyecto
```bash
cd ~ && git clone https://github.com/BastianTF/AeroSmart-Home.git && cd AeroSmart-Home
```
Si ya lo tenías:
```bash
cd ~/AeroSmart-Home && git checkout master && git pull
```

## 2. Configurar la placa (1 paso, solo si se usará la placa real)
```bash
bash tools/configurar.sh
```
Pide la **clave del enlace** (la entrega el equipo por WhatsApp o en persona, nunca por GitHub) y la **clave del Wi-Fi** de la placa, y deja listos `local.properties` y `arduino/AeroSmartHome/secrets.h`.

Sin este paso igual compila y funciona en **Modo demostración**. Android Studio crea solo `local.properties` con la ruta del SDK al abrir el proyecto.

## 3. Compilar el APK
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew assembleDebug
```
La primera vez tarda varios minutos. Al final debe decir `BUILD SUCCESSFUL`. El APK queda en:
```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```
Para abrir la carpeta en el Explorador:
```bash
explorer "app\\build\\outputs\\apk\\debug"
```

Alternativa sin comandos: abrir la carpeta en Android Studio → **Build → Build App Bundle(s) / APK(s) → Build APK(s)** → **locate**.

## 4. Instalar en el teléfono Android
1. Pasar `app-debug.apk` al teléfono (WhatsApp a ti mismo, Google Drive o cable USB).
2. Abrirlo en el teléfono. Si pregunta, permitir **"Instalar apps de fuentes desconocidas"** para esa app (WhatsApp, Drive o Archivos).
3. **Instalar → Abrir**.

## 5. Notas
- **Inicio con Google:** funciona en cualquier PC porque el proyecto usa una llave de firma compartida (`app/debug.keystore`) cuyo SHA-1 ya está registrado en Firebase. Solo sirve para pruebas, no para Play Store.
- **iPhone:** no instala APK. Usar un teléfono Android o el emulador de Android Studio.
- **Actualizar la app:** repetir los pasos 1 (con `git pull`), 3 y 4. Se instala encima sin perder datos.

## Errores típicos
| Error | Solución |
|---|---|
| `SDK location not found` | Revisar la ruta `sdk.dir` en `local.properties` (Android Studio → Settings → Languages & Frameworks → Android SDK muestra la ruta). |
| `JAVA_HOME is set to an invalid directory` | Android Studio está en otra carpeta: ajustar la ruta del paso 3. |
| `License for package ... not accepted` | Abrir Android Studio → Settings → Android SDK → instalar **Android API 37** y aceptar licencias. |
| `Espacio en disco insuficiente` | Liberar al menos 15 GB en C:. |
| Google dice error 10 / "DEVELOPER_ERROR" | El APK no se firmó con `app/debug.keystore`: hacer `git pull` y compilar de nuevo. |
| "App no instalada" en el teléfono | Desinstalar la versión anterior (firmada con otra llave) y volver a instalar. |
