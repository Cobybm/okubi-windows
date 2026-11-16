# OKUBI 遊 — Fase 76 · CINEMA UNIFIED (Windows)

**Estado:** beta de escritorio, PR apilado después de hotfix 75B. No fusionado a `main`.

## Qué cambia

La pantalla principal de OKUBI Windows incorpora una nueva entrada en el menú lateral **▣ CINEMA · MIS JUEGOS** y el atajo **Ctrl+U**. Abre una vista cinematográfica que reúne dos fuentes locales:

- **RETRO:** todos los juegos ya importados por la biblioteca de OKUBI (GB, GBC, GBA, SNES). Utiliza las portadas cacheadas y los fondos originales del launcher; mantiene favoritos, historial y horas reales del motor retro, sin alterar rutas.
- **PORTS PC:** solo ejecutables Windows **ya vinculados** por el usuario desde el catálogo revisado o desde el explorador de GitHub. Aparecen como una tarjeta PC separada, nunca como ROM o APK Android.

La nueva vista tiene cabecera CINEMA, recuentos separados de retro/PC, tiempo **solo retro realmente medido**, búsqueda instantánea, filtros **TODO / RETRO / PORTS PC / FAVORITOS / RECIENTES**, tarjetas con portada/fondo y botón de jugar, y paginación ligera de **36 títulos por tanda**. Adapta el número de columnas al ancho de la ventana para no generar miles de controles a la vez.

**Importante:** el filtro FAVORITOS conserva los favoritos que ya existen en la biblioteca retro; no inventa favoritos para ports. RECIENTES utiliza únicamente el historial retro registrado; no finge fechas de apertura de EXE. Esta fase añade una **vista unificada**, no reemplaza ni borra el catálogo retro anterior. El menú original y el botón PORTS PC · GITHUB siguen disponibles.

## Seguridad y datos

- `DesktopUnifiedLibrary` agrega las listas **solo en memoria**; no copia ROMs, partidas, BIOS, imágenes comerciales, ejecutables, certificados ni contraseñas.
- `DesktopPortsLibrary.linkedEntries()` lee referencias ya autorizadas en el perfil local y rechaza rutas obsoletas, symlinks y claves de preferencias desconocidas. No escanea todo el disco.
- El lanzamiento **RETRO** utiliza la función original `OkubiDesktop.play`, por lo que conserva Smart Save Guard, emuladores y estadísticas existentes.
- El lanzamiento **PC** exige una confirmación visible con ruta antes de abrir un `.exe`. Se vuelve a validar la cabecera PE Windows; no utiliza `cmd.exe`, descarga, extracción automática, ejecución remota ni privilegios elevados.
- Los juegos PC externos usan sus propios guardados: OKUBI no afirma transferirlos automáticamente al celular, ni mide su tiempo real.
- La APK Android, su firma, los nombres de paquete y los repositorios privados no cambian.
- El CI usa únicamente juegos/ejecutables sintéticos creados en carpetas temporales.

## Prueba en la PC

1. Extrae el **nuevo ZIP** en una carpeta independiente y ejecuta `VERIFY-WINDOWS-PACKAGE.cmd` y `RUN-QA57-ISOLATED.cmd`.
2. En la barra lateral de Windows abre **CINEMA · MIS JUEGOS** o pulsa **Ctrl+U**.
3. Confirma que tus ROMs ya importadas aparecen con portadas si están cacheadas, y que los ports **vinculados** en el mismo perfil QA también aparecen. Un proyecto GitHub sin vínculo no debe verse como instalado.
4. Escribe un nombre; prueba los filtros **TODO, RETRO, PORTS PC, FAVORITOS y RECIENTES**. Si hay muchas tarjetas, usa **VER MÁS**.
5. Abre una partida retro desde esta ventana. Debe utilizar las mismas opciones de Smart Save Guard y los guardados existentes de ese perfil.
6. Abre un port Windows, comprueba que muestra una confirmación con la ruta antes de iniciar y que no se abren aplicaciones al navegar por la lista.
7. Prueba una ventana más pequeña: deben seguir visibles títulos, botones y desplazamiento, sin superposiciones ni controles blancos.
8. Verifica que la biblioteca anterior y el botón **PORTS PC · GITHUB** siguen funcionando.

## Puertas previas al lanzamiento estable

- [ ] QA físico del panel CINEMA y corrección visual 75B en la PC del propietario.
- [ ] Cross Save físico de una partida **de prueba** Android ↔ Windows.
- [ ] Correspondencia fuente exacta de DLL y revisión GPL/MPL de los núcleos incluidos.
- [ ] Verificar certificado Android antes de modificar la aplicación canónica.
- [ ] Revisión y autorización explícita de integración ordenada de los PR apilados, sin tocar el proyecto privado original.

El lanzamiento estable no está autorizado por la sola compilación de esta fase.
