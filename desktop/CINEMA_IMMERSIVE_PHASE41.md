# OKUBI 遊 — Fase 41: CINEMA IMMERSIVE (Desktop 0.9.2)

Esta fase está basada directamente en las capturas del usuario con **Pokémon Esmeralda** dentro de OKUBI. Busca que el juego tenga más espacio sin introducir estiramientos o cambios en la emulación.

## Cómo usarlo

En cualquier juego GB/GBC/GBA/SNES, pulsa **ESC → CINEMA CONTROL CENTER → HUD**. Elige uno de los cuatro modos:

| Modo | Comportamiento |
|---|---|
| **Automático** (predeterminado) | Muestra cabecera y pie al inicio, los oculta tras aproximadamente **3 segundos** sin actividad, y los vuelve a mostrar al mover el ratón por encima del juego |
| **Completo** | Mantiene siempre los botones F5/F9, Menú, pantalla completa, Salir y la barra inferior de ayuda/estado |
| **Compacto** | Muestra una cabecera pequeña con el título, **Menú / Esc** y **Salir**, sin barra inferior |
| **Oculto** | Oculta cabecera y pie incluso al mover el ratón; **F1** los revela temporalmente, o **Esc** abre el menú para cambiar el modo |

Los cambios son inmediatos y se recuerdan **por consola**. Por ejemplo, puedes usar GBA en automático y SNES en compacto. Los ajustes van a `%APPDATA%/OKUBI/cinema-hud-v1.properties` y **no reemplazan** tus configuraciones previas de teclado, filtros, mandos, estadísticas ni biblioteca.

**Si ocultas todo por accidente:** pulsa **Esc** para abrir los ajustes o **F1** para mostrar temporalmente los botones. Salir también sigue funcionando cerrando la ventana de Windows. No se puede perder acceso a los controles por escoger HUD oculto.

## Controles que se mantienen

- **F5**: estado rápido. **F9**: cargar estado rápido con confirmación.
- **F12**: captura PNG. **M**: silenciar/reactivar audio.
- **P**: pausar/reanudar. **F11**: maximizar/restaurar ventana.
- **Esc**: menú de control dentro del juego.
- **F1**: ver las barras temporalmente (se vuelve a ocultar pasados unos 3 segundos).
- Flechas, Start, Select, A/B/X/Y, L/R, teclas remapeadas y XInput siguen pasando al mismo núcleo nativo.

Al abrir el menú ESC, el juego se pausa y ambas barras se ocultan para dejar que el panel sea el único control visible. Al cerrarlo regresa a la disposición elegida, sin reiniciar ROM, audio, SRAM o RTC.

La cabecera y el pie son componentes Swing que se muestran/ocultan desde un **temporizador Swing**, nunca desde el hilo JNI de emulación. La vista del juego usa `BorderLayout.CENTER`: al desaparecer las barras, la pantalla de Pokémon gana altura real disponible. El renderizador conserva la proporción original de GBA (240×160), GB/GBC y SNES, así que algunos monitores seguirán mostrando bandas negras laterales en lugar de deformar la imagen.

## Privacidad, guardados y compatibilidad

La versión 0.9.2 conserva lo siguiente sin cambios de formato ni ubicación:

- SameBoy (GB/GBC), mGBA (GBA), Beetle Supafaust (SNES) integrados y verificados.
- SRAM y RTC, guardados de cartucho al salir y cada ~30 segundos.
- Estados rápidos F5/F9 y sus copias `.previous`.
- Cross Save Android ↔ PC, Cloud Save por carpeta local sincronizada con Drive y recuperación protegida.
- Game Hub, estadísticas, favoritos y controles personalizados.

No hay nuevos permisos, servicios en segundo plano ni cambios en la APK Android. **El usuario no tiene que volver a importar las ROMs ni desinstalar una versión anterior.** Extraer el ZIP en otra carpeta y conservar `%APPDATA%/OKUBI`.

## Pruebas

GitHub Actions de Windows ejecuta `DesktopImmersiveTest` para verificar los cuatro modos, el auto-ocultamiento y revelación, que ESC siempre ofrece un menú, preferencia por consola, recuperación de datos inválidos, y que F1/ESC están reservados al sistema. También conserva todas las pruebas anteriores de emulación y partidas.

**Pendiente probar visualmente en el PC real** que el temporizador y el ratón reaccionen correctamente durante Pokémon Esmeralda, y que el modo oculto/compacto no afecte el uso del mando. La fase se mantiene como **PR en borrador**. No fusionar.
