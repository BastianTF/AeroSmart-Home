# Mockup Interactivo Oficial - AeroSmart Home 📱
**TI3042 - Aplicaciones Móviles para IoT | Unidad 2**

Este directorio contiene la aplicación de simulación interactiva desarrollada para la evaluación sumativa de la Unidad 2.

## 🌟 Cómo abrir y usar el Mockup

1. Abre el archivo [`index.html`](index.html) con cualquier navegador web moderno (Google Chrome, Microsoft Edge, Mozilla Firefox o Safari).
2. No requiere servidor web local, librerías externas ni conexión a Internet (100% autónomo y offline).

## 🚀 Funcionalidades Principales

* **Chasis de Smartphone Android:** Simulación fiel de un dispositivo móvil Android con barra de estado dinámica y navegación gestual.
* **Navegación de Pantallas Completa:**
  1. *Splash Screen:* Pantalla de carga animada con logo del ventilador.
  2. *Onboarding:* Carrusel de 3 diapositivas interactivas con `ViewPager2` virtual.
  3. *Inicio de Sesión:* Autenticación con credenciales y selector de backend (SQLite, Firebase, API MySQL).
  4. *Registro + OTP:* Formulario con validación y modal interactivo de verificación en dos pasos (OTP `1234`).
  5. *Dashboard IoT Central:* Turbina animada con aspas vectoriales rotando a 1850 RPM, indicador de ángulo del servomotor SG90 (0° a 90°), gauges térmicos con zonas de confort, radar PIR y evaluador de reglas.
  6. *Perfil & ISO 27400:* Auditoría de seguridad OT y datos de usuario.
* **Simulador de Hardware IoT (Panel Lateral):**
  * Control deslizante de temperatura (16°C a 36°C) para probar en vivo la respuesta del actuador.
  * Control deslizante de humedad (20% a 95%).
  * Disparador de presencia humana PIR.
  * Consola serial de telemetría a 115200 baudios con timestamp.
* **Modo Multi-Dispositivo (Criterio 2.1.4):**
  * Botón en la cabecera superior para alternar a la vista simultánea de **Dispositivo 1 (Master / Supervisor)** y **Dispositivo 2 (Esclavo / Cliente Monitor)** demostrando la interconexión inalámbrica en tiempo real.
* **Dossier Académico Unidad 2:**
  * Botón en la barra superior con el desglose oficial de respuestas a los pasos 1, 2, 3 y 4 de la guía TI3042.
