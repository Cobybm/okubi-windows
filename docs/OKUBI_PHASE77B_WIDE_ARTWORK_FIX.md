# OKUBI 遊 — Fase 77B: banners de ports y capturas de proyectos oficiales

## Defecto confirmado con captura física Windows

La imagen social de GitHub es horizontal, pero el catálogo la colocaba en un rectángulo vertical de 115×145 píxeles. Quedaba diminuta, con mucho espacio vacío. Un tooltip largo tapaba los botones DESCARGAS / VINCULAR .EXE y la mayoría de las vistas previas eran tarjetas de repositorio, no imágenes del juego.

## Cambios realizados

- Imagen panorámica de 420×155 píxeles de preferencia visual en cada ficha; las columnas se adaptan al ancho del catálogo y el contenido de texto aparece debajo. Filas de 400px y scroll normal.
- Arte horizontal llena la banda de imagen, recortando el margen sobrante con cuidado; ilustraciones verticales siguen enteras. Tooltips extensos eliminados (accesibilidad mantenida).
- Nuevas fuentes de imágenes desde archivos **del propio repositorio de cada proyecto** fijados a un SHA Git de 40 caracteres, solo en la instalación del usuario y nunca incluidos en el ZIP.
- DevilutionX: https://github.com/diasurgical/DevilutionX/blob/86cd4137c32b513e429ebe2cb67bdaac7c1f244e/Packaging/vita/sce_sys/pic0.png
- dhewm3: https://github.com/dhewm/dhewm3/blob/505c5c85303b5db3e4a6290550c7dc239c169b6c/docs/img/d3gui-example-16_9.png
- Augustus: https://github.com/Keriew/augustus/blob/9f367f9936f4e28e074b13d968f8f5ec8e3ff117/doc/main-image.png
- Caché de estas fuentes en directorio `official-screenshots-v1` separado de la caché anterior, para que las viejas miniaturas GitHub no tapen las nuevas imágenes. Conservamos los archivos antiguos y toda elección manual.
- Si falla una fuente de imagen del repositorio, se mantiene el fallback GitHub social preview; no bloquea la navegación.

## Seguridad

- Orígenes HTTPS limitados a una lista de **tres URL exactas** y el endpoint social GitHub ya existente, sin redirecciones, conservando límites de 2 MB y 9 millones de píxeles.
- No añade ROMs, BIOS, APK, DLL o juegos comerciales. No abre contenido descargado como código y no toca guardados, perfiles ni firmas.
- Las capturas son recursos del proyecto upstream utilizados para vista previa local, no prueba de licencia para su redistribución. No vienen empaquetadas con el EXE.
- El catálogo de GitHub no verificado seguirá mostrando tarjeta de proyecto en vez de fingir una portada de juego. Las imágenes de usuario tienen prioridad.

## QA física sugerida

1. Descomprime la nueva ZIP en una carpeta independiente. Comprueba hash e inicia `RUN-QA57-ISOLATED.cmd`.
2. Abre PORTS PC · GITHUB. Compara DevilutionX, dhewm3 y Augustus con la captura anterior. Ahora las imágenes deben ser grandes y horizontales.
3. Pasa el cursor encima de la imagen y comprueba que no aparece texto emergente tapando botones.
4. Prueba el filtro y la vista de GitHub; las tarjetas y botones no deberían solaparse.
5. Prueba IMAGEN (override manual) y su persistencia; comprueba que las partidas siguen intactas.

QA automatizada de Java/Windows es independiente de la prueba física real. Los PR continúan en borrador hasta autorización expresa.