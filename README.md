# 🏎️ F1 PitWall TV

Plataforma profesional de Fórmula 1 diseñada para **Smart TV, Android TV y dispositivos multimedia (1080p y 4K)**, con una experiencia visual cinematográfica y futurista inspirada en **F1 TV, ESPN, DAZN y Disney+**, navegable al 100% mediante control remoto (D-Pad).

---

## 🚀 Novedades de la Fase 5: Rediseño Futurista & Navegación Fluida

### 1. 🎮 Navegación D-Pad de Alta Fluidez para Smart TV
- **`TvHorizontalRecyclerView`**: Interceptor de foco que confina el movimiento horizontal a los límites del carrusel, eliminando saltos accidentales fuera de la sección al alcanzar los extremos (inicio o final).
- **Auto-centrado Progresivo**: Desplazamiento vertical suave y adaptativo en `NestedScrollView` con margen de lectura para mantener siempre centrada la fila enfocada en televisores 1080p y 4K.
- **Restauración de Estado y Scroll**: Al navegar a subpantallas (Muro de Pits, Carreras, Ajustes) y presionar el botón **Atrás**, la aplicación regresa de inmediato a la pantalla de Inicio conservando la posición exacta del scroll y el foco.
- **Micro-animaciones de Foco**: Escalado fluido (1.06x), elevación y contornos en cian eléctrico y rojo competición en cada tarjeta y botón.

### 2. 🏎️ Muro de Pits Inteligente (Cero Vídeos Falsos en Bucle)
- **Modo Standby & Hub Editorial**: Si no hay carrera en directo, el reproductor no arranca en bucle de forma engañosa. En su lugar, presenta un centro de análisis técnico del próximo Gran Premio (*GP de Las Vegas 2026*), especificaciones de pista, telemetría y botones para reproducir previos o resúmenes bajo demanda.
- **Sintonizador de Radios de Equipo**: Canales de audio reales de OpenF1 por piloto (`#1 VER`, `#4 NOR`, `#16 LEC`, `#44 HAM`, `#63 RUS`, `#81 PIA`, `#14 ALO`) sin simulaciones estáticas.

### 3. 👤 Fotos Reales de Pilotos y Logos Oficiales de Constructores
- **Mapeo Oficial de Headshots**: Resolución garantizada en la CDN oficial de F1 para todos los pilotos de la parrilla 2026 (Antonelli, Verstappen, Russell, Hamilton, Leclerc, Norris, Piastri, Hadjar, Lawson, etc.).
- **Logos de Equipos Oficiales**: Renderizado nativo de emblemas de alta definición para las 11 escuderías (Mercedes, Ferrari, Red Bull, McLaren, Aston Martin, Alpine, Williams, Haas, Racing Bulls, Audi F1 Team y Cadillac F1).
- **Fallback Deportivo**: Casco aerodinámico vectorial (`ic_driver_helmet.xml`) como alternativa si no hay conexión de red.

---

## 🛠️ Tecnologías y Arquitectura

- **Lenguaje**: Kotlin 1.9+
- **Plataforma**: Android TV (Leanback / Android 10+ / API 29+)
- **Módulos y Desacoplamiento**:
  - `HomeViewManager`: Orquesta el Hero dinámico, podio reciente y 5 carruseles con scroll restoration.
  - `PitWallViewManager`: Controla el ExoPlayer, estado Standby/En Espera, sintonizador de radio y telemetría de monoplazas.
  - `TvHorizontalRecyclerView`: Gestión de límites horizontales y foco D-pad.
  - `F1Repository`: Proveedor único de datos oficiales Jolpica & OpenF1.
- **Reproductor Multimedia**: AndroidX Media3 / ExoPlayer (HLS, DASH, MP4)
- **Carga de Imágenes**: Coil con caché en memoria y disco
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
