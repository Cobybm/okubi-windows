# OKUBI 遊 Desktop 0.9.4 — Fase 43 HEADER RESPONSIVE

Esta fase resuelve el problema visto en capturas reales de Windows: los botones superiores `MODO CON...`, `Juego sor...`, `ESTADÍST...` e `Importar ...` perdían parte de su nombre. La nueva barra se adapta a la ventana **sin desaparecer ni desactivar funciones**.

## Cómo se adapta la cabecera

OKUBI mide el ancho **real** del texto con la fuente y el tamaño de Swing que usa Windows. No depende de una resolución exacta ni de adivinar el DPI.

| Espacio disponible | Distribución | Ejemplo |
| --- | --- | --- |
| Ancho | Una fila con seis botones y títulos completos | MODO CONSOLA, Juego sorpresa, CLOUD SAVE, ESTADÍSTICAS, Motores, Importar ROM |
| Medio | Una fila con seis botones y títulos abreviados, **sin cortar palabras** | Consola, Sorpresa, Nube, Horas, Motores, Importar |
| Estrecho (incluye mínimo 800 px) | Dos filas, **tres botones por fila**, con títulos completos | Primero Consola/Sorpresa/Cloud; debajo Estadísticas/Motores/Importar |

El cambio ocurre al **redimensionar la ventana**, sin reiniciar la aplicación. Cada acción sigue conservando su función y su descripción completa en el tooltip (ratón) y en su nombre accesible (lector de pantalla). Los iconos se dibujan con vectores propios de OKUBI, nunca con emojis que se conviertan en cuadrados.

Cuando entras al **Modo Consola (F11 o Start)**, el primer botón cambia a **Salir de consola**; también se adapta cuando reduces la ventana. Las demás acciones conservan la misma identidad.

## Lo que no cambia

- OKUBI Desktop sigue cargando núcleos Libretro integrados SameBoy, mGBA, Supafaust.
- **Fase 41**: HUD automático, completo, compacto y oculto, acceso Esc/F1.
- **Fase 42**: SMART SCALING Fit, Pixel Perfect, Fill, Stretch y tecla F2.
- Game Hub, juegos, favoritos, estadísticas, biblioteca, Cross Save y Cloud Save.
- Todas las rutas de SRAM/RTC, guardados F5/F9, recuperaciones y ROMs.
- App Android, firma APK, Google Drive para escritorio y cualquier proyecto separado.

No incorpora cuentas, compras, descargas de ROM ni telemetría.

## Cómo probar

1. Termina la partida y cierra OKUBI Desktop 0.9.3, conservando `%APPDATA%/OKUBI`.
2. Extrae el ZIP 0.9.4 completo **en una carpeta distinta**, sin borrar la versión anterior.
3. Abre `OKUBI Desktop.exe` y observa la barra superior con la ventana habitual.
4. Reduce la ventana hasta 800 px de ancho: deben aparecer **dos filas completas**, sin textos cortados.
5. Maximiza la ventana: vuelve a una sola fila cuando los títulos quepan.
6. Prueba los botones principales, entra y sale de Modo Consola y comprueba que cada acción responde.
7. Abre Pokémon Esmeralda para verificar que emulador, guardados y SMART SCALING no han cambiado.

## Pruebas automatizadas Windows

`DesktopHeaderResponsiveTest` verifica:
- Medición con fuentes reales de Swing y condiciones de texto grande (simulación de accesibilidad).
- Anchos 800, 900, 960, 980, 1024, 1140, 1280, 1440 y 1920, y los píxeles exactos en que cambia de modo.
- Que las seis acciones se muestran y caben completas con tamaño real de botón.
- Que siguen haciendo clic y mantienen tooltips y nombres accesibles.
- Entrada/salida del Modo Consola y disponibilidad de importación y Cloud Save.

La ejecución también incluye pruebas anteriores de emulación nativa, filtros, SRAM/RTC, USB/mandos XInput, Cloud Save, Cross Save, Pixel Perfect y HUD.

El PR permanece **en borrador sin fusionar**; falta confirmar visualmente la distribución en la PC del usuario, especialmente si usa escala de Windows 150% o 200%.
