# OKUBI 遊 — Fase 77C: más imágenes de videojuegos desde fuentes oficiales

**Problema confirmado con captura real Windows:** Augustus mostraba una captura del juego, pero Arx Libertatis seguía mostrando una tarjeta blanca con el nombre del repositorio y sus estadísticas. El texto «GITHUB» se superponía a los banners.

## Implementación

- Conserva el banner panorámico y el estilo CINEMA de Fase 77B.
- Arx Libertatis ahora utiliza una captura del sitio **oficial** de su equipo, dentro del repositorio `arx/arx-libertatis.org`, fijada a un commit de 40 dígitos.
- Se amplía la lista de imágenes oficiales revisadas de **3 a 8**: DevilutionX, dhewm3, Augustus, Arx Libertatis, OpenTTD, Warzone 2100, Luanti y OpenRA.
- Imagen de OpenTTD desde la galería de `OpenTTD/website`, de Warzone 2100 desde su documentación, de Luanti desde `games/devtest/screenshot.png` y de OpenRA desde su artwork oficial de empaquetado.
- El badge **GITHUB** ya no aparece sobre una imagen visible; solo queda un pequeño distintivo de fallback cuando falta la imagen.
- Prioridad conservada: imagen elegida manualmente → caché de imagen upstream → imagen oficial upstream → vista social GitHub como respaldo → arte procedimental.
- Una imagen del repositorio no garantiza que sea arte de gameplay de la versión actual; las fuentes se identifican como originales del proyecto y la captura física confirmará su calidad visual.

## URLs verificadas y sujetas a los límites de descarga existentes

Todos los recursos se solicitan individualmente desde `raw.githubusercontent.com` mediante **HTTPS y URLs exactas fijadas por commit SHA**. No se incorporan imágenes al ZIP ni se descargan ejecutables.

- Arx: https://github.com/arx/arx-libertatis.org/blob/c3ea1a3dccd90c15042f3432aaca8bc19849ca21/htdocs/images/castle-of-arx-2x.jpg
- OpenTTD: https://github.com/OpenTTD/website/blob/37ddb47c5d3b861195aff7a6b997837fdc433b2f/_screenshots/1.9-coldice_1.png
- Warzone 2100: https://github.com/Warzone2100/warzone2100/blob/253e370c2af78228a822df6afa82b6261dc94656/doc/images/awaymission.jpg
- Luanti: https://github.com/luanti-org/luanti/blob/a10dc8dbff3b0af8295ae55df44f65b1d9ee92c7/games/devtest/screenshot.png
- OpenRA: https://github.com/OpenRA/OpenRA/blob/b4d95a56717d8d0070f195e343f6c2728ce87900/packaging/artwork/macos-background-2x.png

## Prueba del usuario

1. Descarga y extrae el ZIP QA en una carpeta nueva y verifica el paquete.
2. Entra a **PORTS PC / GITHUB** y compara Arx Libertatis con Augustus. Arx debe mostrar una imagen del juego en lugar de la ficha blanca de estadísticas.
3. Comprueba también OpenTTD, Warzone 2100 y OpenRA. Si una fuente no responde, permanece disponible el fallback; las imágenes manuales tienen prioridad.
4. Prueba la opción IMAGEN > Elegir imagen local, cierra y reabre; debe conservar tu selección.
5. Prueba sin conexión después de haber cargado imágenes. Las que hayan entrado en caché deben seguir funcionando.
6. Verifica que las fichas se desplazan y que DESCARGAS, VINCULAR .EXE e IMAGEN no se tapen.

## Seguridad y otros pendientes

Se mantienen los límites de 2 MB de descarga, resolución máxima, validación de imagen, no seguir redirecciones y operaciones asíncronas. No se envían ROMs, rutas personales, archivos originales, guardados ni datos de cuenta. Android sigue intacto, igual que la rama `main` y el repositorio privado original.

Persisten las tareas de validación física del nuevo diseño, Cross Save en dos dispositivos reales, revisión GPL/MPL de DLL Windows y firma Android antes de la publicación estable.