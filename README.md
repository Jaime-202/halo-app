# Halo

App Android de salud personal: tu día, tu historial y tu comida en un solo sitio.
Local-first: tus datos viven en el móvil y solo salen si tú haces una copia de seguridad.

[![Android](https://img.shields.io/badge/Android-9.0%2B%20(API%2028)-7CF5D8.svg?style=flat&logo=android&logoColor=white)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-8AA7FF.svg?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-7CF5D8.svg?style=flat)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-8AA7FF.svg)](LICENSE)

---

## Qué hace

**Hoy** — una pantalla por capas:
anillo con el *Halo Score* del día y la recuperación, balance de calorías (comido frente a gastado),
cuatro métricas a elegir, tus recordatorios, ánimo y energía y una sola frase útil.

**Comida**
- Buscador único: alimentos genéricos primero y productos de supermercados españoles
  (Mercadona, Carrefour, Dia, Alcampo, Lidl, Eroski, El Corte Inglés) vía Open Food Facts.
- Código de barras, lectura de etiquetas nutricionales con OCR en el dispositivo, foto o texto con Gemini.
- Favoritos, recientes, raciones rápidas, comidas guardadas y "copiar ayer".
- Fibra, azúcar y sal. Registro sin colores de culpa.

**Calorías personales** — bienvenida con sexo, edad, altura, peso, actividad y objetivo.
Mifflin-St Jeor × actividad, y gasto adaptativo con tus datos reales de peso y comida.

**Calendario** — semana, mes y año con filtros (Halo, kcal, pasos, sueño, actividad, recordatorios, ánimo),
resumen de cada periodo, racha con comodín semanal y conclusiones automáticas que cruzan tus datos.

**Actividad** — tus entrenos de Symmetry (u otra app) leídos desde Health Connect:
minutos, calorías y pulso por sesión, frecuencia semanal y días de descanso.
Importación opcional del CSV de Symmetry para ver ejercicios, series y pesos.

**Botón +** — registra comida, peso, agua o ánimo desde cualquier pantalla.

**Más** — resumen semanal los domingos, avisos automáticos opcionales, recordatorios propios,
copia de seguridad cifrada (AES-256-GCM) en Google Drive o donde quieras, exportación CSV
y dos widgets (Anillo 2x2 y Hoy 4x2).

---

## 🛠️ Stack Tecnológico

| Capa / Módulo | Tecnología Utilizada |
| :--- | :--- |
| **Lenguaje** | Kotlin 2.0.21 |
| **UI Framework** | Jetpack Compose (BOM 2024.09.00) + Material 3 |
| **Inyección de Dependencias** | Dagger Hilt 2.52 con Hilt WorkManager integration |
| **Persistencia Local** | AndroidX Room Database 2.6.1 con soporte para Coroutines y Flow |
| **Preferencias Clave-Valor** | Jetpack DataStore Preferences 1.1.1 |
| **Segundo Plano / Background** | AndroidX WorkManager 2.9.1 (`@HiltWorker`) |
| **Salud y Biometría** | AndroidX Health Connect Client SDK (`1.1.0-alpha11`) |
| **Widgets** | Jetpack Glance AppWidget 1.1.1 |
| **Inteligencia Artificial** | Google Gemini API (`gemini-3.8-flash` con auto-descubrimiento y multi-tier fallback) |
| **Red y Parsing** | OkHttp 4.12, Retrofit-ready JSON parsing, Gson |
| **Visión y Códigos de Barras** | Google Code Scanner (Play Services) + Open Food Facts API |

---

## 🔐 Privacidad, Seguridad y Buenas Prácticas

* **Local-First & Cero Telemetría:** Todas las bases de datos de salud, nutrición y entrenamientos residen localmente en el almacenamiento privado de la app (`SQLite / Room`). No existen servidores intermedios de recopilación de datos, trackers ni SDKs de publicidad.
* **Seguridad de Credenciales:**
  - El proyecto está preparado para su publicación en repositorios públicos.
  - Ninguna clave API o credencial sensible se almacena en el control de versiones (Git).
  - Las claves opcionales de Gemini se gestionan de forma segura a través de `local.properties` (ignorado en `.gitignore`) o mediante el diálogo cifrado de ajustes dentro de la propia aplicación en `DataStore`.

---

## 🚀 Guía de Instalación y Compilación

### Requisitos Previos
* **Android Studio:** Ladybug (2024.2.1) o superior.
* **JDK:** Java 17 o Java 21 (incluido en Android Studio JBR).
* **Dispositivo Físico o Emulador:** Android 9.0 (API 28) o superior. Se recomienda Android 14+ para soporte nativo directo de Health Connect sin dependencias externas.

### Pasos para Compilar

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/Jaime-202/halo-app.git
   cd halo-app
   ```

2. **Configurar el entorno local (`local.properties`):**
   Copia el archivo de plantilla proporcionado:
   ```bash
   # Windows PowerShell:
   Copy-Item local.properties.example local.properties

   # macOS / Linux:
   cp local.properties.example local.properties
   ```
   Abre `local.properties` e indica la ruta de tu Android SDK y, opcionalmente, tu clave de Gemini API gratuita de [Google AI Studio](https://aistudio.google.com):
   ```properties
   sdk.dir=C\:\\Users\\tu_usuario\\AppData\\Local\\Android\\Sdk
   GEMINI_API_KEY=tu_clave_de_gemini_opcional
   ```
   *(Nota: Si no introduces ninguna clave, la aplicación funcionará al 100% en modo local offline con su motor heurístico).*

3. **Compilar el APK de depuración:**
   ```bash
   # Windows PowerShell:
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat assembleDebug

   # macOS / Linux:
   ./gradlew assembleDebug
   ```

4. **Instalar en un dispositivo conectado:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📄 Licencia

Este proyecto está distribuido bajo la licencia [MIT](LICENSE). Siéntete libre de adaptarlo, mejorarlo y utilizarlo en tus propios proyectos personales o profesionales.
