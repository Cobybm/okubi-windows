# OKUBI 遊 — FASE 1: INTEGRACIÓN DEL CÓDIGO

Fecha de revisión: 2026-11-16

## Base real
Se partió de `release/okubi-phase82-final-acceptance-gates`, que sí contiene la aplicación de Windows y las fuentes Android, en lugar del `main` reducido de migración. La rama **feat/okubi-phase1-unified-full** recibe los cambios sin tocar las ramas estables ni los datos del usuario. Los PR anteriores #37 (catálogo parcial) y #102 (branding en repositorio Android original) quedan como referencias; no deben confundirse con esta integración completa.

## Existía y se conserva
- Windows: biblioteca GB/GBC/GBA/SNES, núcleos Libretro, mandos XInput y teclado configurable por consola, CINEMA, SMART PC para PS1/PS2/PS3 y emuladores externos con elección explícita, estadísticas, guardados, Vault/Cloud/Cross Save, escalado y ajustes de audio.
- Android: pantalla vertical/horizontal, SameBoy/mGBA/Supafaust según artefacto, mandos USB/Bluetooth, controles táctiles, ranuras de guardado, exportación y restauración, velocidad/audio, importación retro, catálogo independiente con descarga directa Ascension y parches oficiales.
- Se conserva estrictamente la biblioteca de partidas existentes, sin copiar ROMs privadas a GitHub, sin manipular firmas, BIOS, firmware o emuladores externos. VIDAS no se toca.

## Incorporado en código ejecutable
1. **Windows**: panel DESCUBRIR JUEGOS en la barra lateral y acceso Ctrl+D. Tarjetas de catálogo con enlaces originales. Descarga directa explícita de 2048 GB desde Homebrew Hub, almacenada bajo APPDATA/OKUBI/homebrew, verificación de cabecera de cartucho, límites, no redirecciones, comprobación SHA-256 e importación a la biblioteca existente en SwingWorker.
2. **Windows**: guía de teclas reales al primer inicio por consola (se cierra automáticamente a los 8 segundos para mandos sin teclado); refleja los cambios guardados en DesktopPlaySettings. En el CINEMA CONTROL CENTER se incorpora **VER CONTROLES** para consultarla de nuevo, con distribución XInput y atajos F5/F9/ESC.
3. **Android**: guía del primer inicio para GB/GBC/GBA/SNES con teclas WASD, J/K, Q/E, U/I, Enter y Shift, y mandos físicos USB/Bluetooth ya soportados. Menú rápido mantiene acceso a controles. Guarda únicamente la preferencia de mostrar guía.
4. **Android**: catálogo retro añade 2048 GB como segundo título con descarga directa seleccionada desde Homebrew Hub, cabecera GB verificada; archivos inválidos se rechazan y se pueden descargar de nuevo. Se añaden fuentes por consola y ficha oficial God of War sin proporcionar ISOs comerciales.
5. **Identidad Android**: la etiqueta del launcher es OKUBI 遊 tanto para la variante normal como para QA side-by-side. Se mantiene `applicationId`, firma no se modifica; el APK QA no reemplaza la instalación normal.
6. Metadatos de saga comercial God of War y catálogo homebrew multiplataforma con URLs oficiales, idioma/ediciones y distinción entre descargas autorizadas y compras/importaciones.

## No confundir biblioteca con compatibilidad
- Solo GB/GBC/GBA/SNES tienen emuladores de núcleo integrados previamente. PlayStation PS1/PS2/PS3 usan emuladores externos oficialmente instalados cuando se configuran; otras consolas son catálogo y enlaces de descubrimiento, **no** una afirmación de emulación para todas.
- En Windows, solo los juegos cuya fuente permite descarga automática, tienen URL directa fija y verificable y estén registrados en la lista segura activan el botón Descargar. En Android, además, se permiten entradas públicas de Homebrew Hub cuya respuesta indique licencia abierta, plataforma soportada, título original (no hackrom) y archivo jugable; siempre con origen HTTPS limitado y validación de ROM. Comercial God of War solo ficha, fuente oficial e importación de copia autorizada.
- La API externa puede cambiar, fallar o no estar disponible: no afirmar descarga online exitosa sin probarla con red real. No hay descarga directa para todas las sagas o plataformas.
- Android y Windows NO alteran el audio del reproductor externo sin su permiso.

## QA de cierre de Fase 1
El flujo `.github/workflows/okubi-final-phase1-source.yml` comprueba compilación de todas las fuentes Java Windows, pruebas de teclado/mando, reglas de descarga, la biblioteca, respaldos y contratos de licencias. En Android ensambla el proyecto Java mediante integración, compila las clases, verifica nombre de launcher/guía y ejecuta la suite de regresión Python. Es requisito que la ejecución final esté verde.

## Fase 2 (aún pendiente)
- Compilación de Windows con DLLs oficiales con SHA-256 verificado. El workflow heredado `okubi-desktop.yml` falló en un hash del upstream Libretro: **NO desactivar el verificador**. Revalidar artefactos y volver a fijar hashes sólo con evidencia de procedencia.
- APK de Android con núcleos oficiales, firma compatible con la instalación anterior, comprobación instalada de nombre/icono, pruebas de juegos con mando/teclado, recuperación de partidas.
- Descarga real desde Homebrew Hub, enlaces de juegos libres, antivirus, tamaños, acceso al disco, pruebas en PC y A05s.
- No liberar EXE ni APK como estable sin verificación de los dos artefactos y pruebas físicas.

## Evidencias y trazabilidad
- Rama: https://github.com/Cobybm/okubi-windows/tree/feat/okubi-phase1-unified-full
- Tests: https://github.com/Cobybm/okubi-windows/actions/workflows/okubi-final-phase1-source.yml
- Licencia API Homebrew Hub: https://github.com/gbdev/homebrewhub/blob/main/API.md

## Regla de aprobación
Marcar Fase 1 como **código integrado** solo con CI verde. No marcar Fase 2 como terminada por tener la documentación o los metadatos.
