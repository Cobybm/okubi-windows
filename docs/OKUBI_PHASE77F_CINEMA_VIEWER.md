# OKUBI 遊 — Fase 77F · Visor CINEMA PRO

## Objetivo

Ver las **27 capturas oficiales de 13 ports** de la fase 77E en un cuadro grande, manteniendo el catálogo legible y preservando datos personales. Esta fase afecta solo el código Java Swing de Windows. **No se ha modificado Android ni VIDAS**.

## Funcionalidad real

- En PORTS PC/GITHUB, un **clic en el banner** abre una ventana modal grande, sin agregar un quinto/sexto botón que corte los botones DESCARGAS / VINCULAR .EXE / IMAGEN.
- Alternativamente usar **IMAGEN → Ver galería completa**; la selección mantiene la posición de la foto visible cuando se abre.
- **Enter o Espacio** sobre un banner con foco lo abre. El visor permite **flecha izquierda/derecha** para cambiar imágenes y **Escape** para cerrar. Los botones propios de las fichas siguen intactos.
- **Mando Windows XInput:** D-pad izquierda/derecha o LB/RB navega, B cierra la ventana, utilizando las clases preexistentes `WindowsRetroNative.gamepadMask`, `DesktopConsoleNavigation` y `DesktopControllerFocusGate`.
- El polling solo se ejecuta con el visor activo y se detiene al cerrarse. Se exige un ciclo completamente neutro al activar el cuadro (evita heredar el botón de otra ventana); se suspende al detectar una sesión de emulación activa y no modifica el controlador del juego.
- El visor usa **ajuste para mostrar la fotografía completa** (no recorte panorámico), centrado en fondo CINEMA, con encabezado, contador y CERRAR. Tamaño limitado según resolución de pantalla.
- Las tarjetas pequeñas de la biblioteca unificada no cambian. Se mantienen tamaños y botones de las fichas previas.
- Conexión y caché: emplea **exactamente** el servicio ya existente. No añade dominios, repositorios ni redirecciones; no descarga ejecutables, ROMs, BIOS, saves ni otros archivos. Descarga secundaria solo al navegar por iniciativa del usuario.
- Imagen propia conservada en `port-artwork-v1/manual/` con prioridad en la primera foto; la ventana solo lee imágenes y no escribe sobre ajustes o juegos.
- Las respuestas asíncronas antiguas se descartan mediante el contador existente; no pueden reemplazar una foto diferente si se navega rápido.

## Archivos

- `DesktopPortGalleryDialog.java`: ventana de lectura en formato grande.
- `DesktopPortGalleryInput.java`: teclado / XInput con gate de seguridad.
- `DesktopPortArtwork.java`: modo fit y activar desde clic, Enter o Espacio.
- `DesktopPortsDialog.java`: entrada visual y menú IMAGEN actualizado.
- `DesktopPortGalleryViewerTest.java`: prueba sin red ni hardware, con imágenes sintéticas.
- Workflows `okubi-phase73-windows-ports.yml` y `okubi-desktop.yml`: ejecutan la nueva regresión.

## QA automático y de usuario

1. Compilar todas las fuentes Java desktop y correr pruebas del catálogo Windows/Ubuntu.
2. Ejecutar pipeline Windows completo y verificar artefacto ZIP/checksum.
3. En Windows real, abrir un port con varias imágenes (ej. OpenRA) desde el banner, recorrer **las tres** fotografías con flechas de teclado. Repetir usando un mando XInput conectado, primero soltando todos los botones al entrar al visor.
4. Cerrar con Escape y B, revisar que no se activan juegos por error y que la biblioteca sigue respondiendo.
5. En port sin galería oficial, comprobar fondo CINEMA, sin logo aleatorio de GitHub; si se eligió una imagen propia, debe continuar apareciendo.
6. Elegir una imagen propia, regresar a la primera fotografía, cerrar y abrir la app, comprobar que la selección sigue intacta.
7. Desactivar Internet después de cargar las capturas y verificar acceso offline desde la caché.
8. Comprobar en ventanas pequeñas que el título y el botón CERRAR permanecen visibles; no hay superposición en DESCARGAS/VINCULAR .EXE/IMAGEN.

## Restricciones

**PR en borrador, apilado sobre Fase 77E.** Sin merge a `main`, sin publicación estable ni reemplazo automático de la instalación. No tocar Android, VIDAS, ROMs, partidas, claves, emuladores ni datos del propietario. Cierre de revisión de licencias, firma Android y Cross Save sigue pendiente antes de un release general.
