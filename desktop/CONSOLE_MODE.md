# OKUBI DESKTOP 0.6 — CONSOLE MODE (Fase 37G)

## Tu PC se convierte en una consola retro

Nuevo modo de biblioteca a **pantalla completa sin bordes**, accesible mediante el botón **MODO CONSOLA**, `F11` o **Start en un mando XInput**. No cambia la resolución de Windows ni requiere iniciar un emulador aparte. Se conserva la ventana anterior con un solo clic, `F11` o **B**.

En modo consola, el menú lateral se oculta para dedicar más anchura a juegos, arte retro y destacado. La biblioteca sigue mostrando **dos tarjetas por fila**, con borde rosa para el juego elegido. La vista se desplaza automáticamente cuando mueves la selección. Los juegos siguen usando las portadas originales generadas localmente.

## Control sin ratón

| Acción | Teclado | Mando Xbox / XInput |
| --- | --- | --- |
| Recorrer biblioteca | Flechas | Cruceta o stick izquierdo |
| JUGAR al seleccionado | Enter | A (botón físico de Xbox) |
| Favorito del seleccionado | F (fuera del buscador) | X |
| Cambiar categoría Todos / Favoritos / Recientes | Barra lateral | LB / RB |
| Volver a ventana / biblioteca general | Esc | B |
| Activar / salir de Modo Consola | F11 | Start |
| Buscar | Ctrl+F | Teclado |
| Importar ROM propias | Ctrl+O | Botón Importar ROM |
| Juego sorpresa | Ctrl+R | Botón Juego sorpresa |

La navegación del mando tiene *detección del primer toque* y repetición moderada cuando dejas presionada la cruceta; no salta diez juegos de golpe ni abre múltiples partidas por mantener A. Las direcciones opuestas se cancelan. El polling usa el puente XInput nativo **ya incluido en OKUBI** y no descarga drivers.

Solo se consulta el mando desde la biblioteca cuando la ventana está activa **y no hay un juego integrado ejecutándose**. Cuando el juego está abierto, el control continúa siendo del emulador.

## Instalación / actualización

1. Descarga el artefacto **OKUBI-Desktop-CONSOLE-MODE-Windows-x64-Phase37G** de la ejecución verde de GitHub Actions.
2. Extrae todo el ZIP en otra carpeta; conserva `OKUBI Desktop.exe`, `app` y `runtime`.
3. Cierra OKUBI 0.5 y abre el EXE 0.6. No es necesario importar otra vez tus juegos.
4. Conecta un mando Xbox/XInput compatible y pulsa Start. Mueve la cruceta y abre un juego con A.
5. Si no tienes mando, también puedes usar `F11`, flechas y Enter. Comprueba el filtro y la pausa con `Esc` **dentro** del juego: siguen siendo del CINEMA CONTROL CENTER 0.5.

**Compatibilidad**: Windows 10/11 x64. Mandos Xbox y otros controladores compatibles con XInput; DirectInput puro, Bluetooth sin XInput, PlayStation genérico y mandos propietarios todavía requieren pruebas/adaptadores. El soporte de teclas y ratón es completo.

## Partidas y privacidad

No se cambia ninguna ROM ni el emulador seleccionado. Se conservan los tres núcleos SameBoy, mGBA y Supafaust y la lectura de SRAM/RTC del reproductor. **El programa no cierra** mientras sigue abierta una partida integrada: así no corta un guardado en progreso. Preferencias de biblioteca, favoritos, filtros, teclado y guardados siguen en los mismos archivos de `%APPDATA%/OKUBI`.

No añade cuentas, sincronización, suscripciones ni descarga ROMs. **Cross Save Android/PC** sigue siendo una fase separada, todavía no implementada. Ninguna rama anterior se fusiona.

## Verificaciones

Test Java sin hardware de navegación 2D y casos borde, A/B/X/Start/LB/RB, anti-rebote y repetición controlada, direcciones opuestas y mando desconectado. El CI de Windows conserva las pruebas de emulación JNI, audio, imágenes, guardados, CINEMA CONTROL CENTER y núcleos Libretro oficiales, y genera el EXE autocontenido.

**Prueba física pendiente**: el build no demuestra por sí solo funcionamiento en todos los mandos/monitores. Confirma el modo ventana, pantalla completa y el juego seleccionado en tu PC.
