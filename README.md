# 🏎️ F1 PitWall TV

Plataforma profesional de Fórmula 1 diseñada para **Smart TV, Android TV y dispositivos multimedia (1080p y 4K)**, con una experiencia visual cinematográfica inspirada en **F1 TV, ESPN, DAZN y Disney+**, navegable al 100% mediante control remoto (D-Pad).

---

## 🚀 Características Principales

### 1. 🏠 Pantalla Principal (Estilo Streaming Premium)
- **Hero Cinematográfico**: Detección inteligente del próximo evento (ej. *GP de Las Vegas 2026*), cuenta regresiva dinámica en tiempo real y conversión de horarios a hora de Colombia (GMT-5).
- **Resultados Oficiales Integrados**: Tarjeta del último Gran Premio disputado (*GP de São Paulo 2026 / Interlagos*) con podio oficial (1º Norris, 2º Verstappen, 3º Hadjar) y acceso a clasificación completa.
- **Hub de Categorías**: Navegación rápida por tarjetas a Mundial 2026, Muro de Pits, Calendario, Resultados y Ajustes.
- **Carrusel de Grandes Premios**: Posicionamiento dinámico con la próxima carrera al frente y desplazamiento bidireccional (carreras pasadas y futuras).
- **Mundial de Pilotos 2026**: Ordenado estrictamente de mayor a menor puntuación real (Jolpica F1 API: 1º Antonelli 390 pts, 2º Verstappen 279 pts, 3º Russell 262 pts...).
- **Mundial de Constructores 2026**: Puntos, victorias y diferencias oficiales (1º Mercedes 652 pts, 2º Ferrari 510 pts, 3º Red Bull 415 pts...).
- **Circuitos y Trazados Oficiales**: Siluetas de pistas en alta definición.

### 2. 🏎️ Muro de Pits y Telemetría en Directo
- **Espacio de Contenido Oficial F1**: Cuando no hay sesión en directo, la pantalla no queda vacía; ofrece previa técnica de la próxima carrera, resumen de la última cita y feed de boxes.
- **Selector de Radios de Equipo**: Selector multi-piloto (`#1 VER`, `#4 NOR`, `#16 LEC`, `#44 HAM`, `#63 RUS`, `#81 PIA`, `#14 ALO`) con reproducción de audio real de F1 live timing mediante ExoPlayer secundario sin interrumpir el vídeo.
- **Torre de Tiempos y Telemetría**: Sincronización de velocidad, marcha, RPM, acelerador, freno y DRS con OpenF1 API.

### 3. 📅 Calendario y Archivo Histórico
- Navegación por temporadas (2024, 2025 y 2026).
- Detalle de sesiones (Prácticas, Clasificación, Sprint y Carrera) en hora local.

### 4. 📺 Control Centralizado de Fuentes de Vídeo (Git Tracking)
- Administrable directamente desde `app/src/main/assets/video_sources.json`.
- Permite cambiar transmisiones HLS (`.m3u8`), DASH (`.mpd`) o MP4 sin tocar el código fuente.

---

## 🛠️ Tecnologías y Arquitectura

- **Lenguaje**: Kotlin 1.9+
- **Plataforma**: Android TV (Leanback / Android 10+ / API 29+)
- **Reproductor Multimedia**: AndroidX Media3 / ExoPlayer (HLS, DASH, MP4)
- **Carga de Imágenes**: Coil
- **Red & APIs**: OkHttp3, Gson, Coroutines & Flow
- **Fuentes de Datos**: OpenF1 API & Jolpica F1 API (Ergast)

---

## 📁 Control de Fuentes de Vídeo con Git

Para añadir o modificar enlaces de transmisión desde tu computador:

1. Edita el archivo:
   ```bash
   app/src/main/assets/video_sources.json
   ```
2. Agrega tu proveedor en la sección `providers` o `contentHub`:
   ```json
   {
     "providers": [
       {
         "id": "mi_senal",
         "name": "Mi Señal Personal",
         "streamUrl": "https://mi-servidor.com/live.m3u8",
         "type": "HLS",
         "isDefault": true
       }
     ]
   }
   ```
3. Guarda los cambios con Git:
   ```bash
   git add app/src/main/assets/video_sources.json
   git commit -m "feat(video): actualizar enlace de transmisión"
   git push origin main
   ```

---

## 📦 Compilación e Instalación en Smart TV

```bash
# Compilar APK Debug
./gradlew assembleDebug

# Instalar en televisor Android TV conectado por ADB
adb -s <IP_DEL_TELEVISOR>:5555 install -r app/build/outputs/apk/debug/app-debug.apk

# Ejecutar la aplicación
adb -s <IP_DEL_TELEVISOR>:5555 shell am start -n com.f1pitwall.tv/.ui.MainActivity
```
