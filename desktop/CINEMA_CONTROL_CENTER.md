# OKUBI 遊 Desktop 0.5 — Fase 37F CINEMA CONTROL CENTER

## Menú dentro del juego

**ESC** abre el centro de control sobre la pantalla de emulación y pausa la partida. Presiona **ESC** de nuevo o **VOLVER** para regresar al juego. Puedes cambiar ajustes durante una partida: no hace falta cerrar la ROM, ni cambiar una DLL, ni modificar tus guardados.

### Cinco filtros de imagen, guardados por consola

| Modo | Cómo se ve / funciona |
| --- | --- |
| Píxeles nítidos | Nearest-neighbor adaptado a ventana, imagen pixelada nítida |
| Suave HD | Interpolación bilineal, bordes suaves, no simula xBRZ |
| LCD retro | Rejilla LCD sutil sobre píxeles suficientemente grandes |
| CRT scanlines | Líneas horizontales semitransparentes tipo monitor clásico |
| Pixel Perfect | Escalado **entero** cuando cabe, centrado sin deformar |

Cada sistema (GB, GBC, GBA y SNES) recuerda su filtro independiente. El cambio de filtro solo afecta al dibujo final en pantalla; el núcleo sigue recibiendo los mismos botones y produce las mismas partidas.

### Personalizar teclado

En **ESC → PERSONALIZAR TECLADO**, hay 12 acciones por consola (cruceta, A/B, X/Y, L/R, Start y Select). Pulsa el botón de la tecla que quieres cambiar, después presiona la nueva tecla. Los duplicados se rechazan y las teclas de control del sistema están reservadas: ESC, F1, F5, F9, F11, F12, P, M, Tab y modificadoras. Existe **Restaurar teclas y filtro** con confirmación.

Se guardan los perfiles en:

`%APPDATA%/OKUBI/cinema-controls-v1.properties`

Esto **no modifica ni reemplaza** `desktop-library.properties`, las partidas ni las ROM. Si una preferencia está dañada, la tecla vuelve a un valor predeterminado seguro.

### Accesos de siempre

- **F5**: guardado rápido (estado específico del núcleo, con copia anterior).
- **F9**: cargar estado rápido, siempre previa confirmación.
- **F12**: guardar captura PNG en la carpeta separada del juego.
- **M**: silenciar/reactivar audio.
- **P**: pausar/reanudar.
- **F11**: maximizar/restaurar.
- **ESC**: abrir/cerrar CINEMA CONTROL CENTER.

El mando Xbox/XInput conserva la detección automática, y el teclado puede personalizarse sin alterar el mando.

## Precauciones con las partidas

- SRAM/RTC existente: **no se cambió el formato** ni la ubicación; guardado automático y al salir.
- Los estados rápidos F5/F9 se asocian al hash de la ROM y del núcleo usado. La copia anterior se conserva.
- El menú pausa el núcleo: nunca carga o guarda estados desde el hilo de interfaz.
- La app principal bloquea su salida si hay un juego abierto, para no cortar el guardado.
- Android queda totalmente sin modificar; Cross Save entre plataformas sigue pendiente.

## Compilación y aceptación

GitHub Actions para Windows x64 compila la aplicación y el puente JNI, realiza pruebas del motor Libretro sintético (fotogramas, audio, entradas, SRAM/RTC, snapshots), tests de filtros y mapeo por consola, y empaqueta SameBoy/mGBA/Supafaust de código abierto en un único ZIP con runtime Java integrado.

Los tests prueban la persistencia, el rechazo de teclas reservadas o duplicadas, el escalado entero incluso en ventanas estrechas y el restablecimiento seguro de perfiles dañados. Todavía es necesario hacer pruebas visuales con ROMs reales y verificar las asignaciones de teclado desde tu PC.

El ZIP Windows x64 aún no tiene firma Authenticode. **Extrae siempre las carpetas completas `app` y `runtime`; no necesitas instalar emuladores aparte.** El PR sigue como borrador; no se fusionan ramas.
