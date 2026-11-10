# OKUBI 遊 — Fase 77H · Ampliación verificada de PORTS PC

## Objetivo
Añadir imágenes verdaderamente asociadas al proyecto de origen y **no** asignar miniaturas aleatorias de un título comercial a otro port. Fase solo Windows, apilada sobre 77G, sin fusionar a `main`.

## Cobertura
La biblioteca tiene **32 ports**. Pasamos de **13 a 15 ports con una fuente visual revisada**. La galería crece de **27 a 33 imágenes**: 15 portadas oficiales y 18 capturas secundarias. Diez ports tienen 2 o 3 imágenes.

### Nuevas fuentes concretas

| Port exacto del catálogo | Imagen principal | Imágenes extra | Origen del proyecto |
|---|---|---|---|
| VCMI (`vcmi/vcmi`) | `static/img/screenshots/1.4.0/Siege.jpg` | `1.4.0/Town and Unit.jpg` y `1.3.0/Castle Siege.jpg` | Repositorio del sitio **vcmi/VCMI.eu**, commit `626f5b12a30590d04cfce7e2b610c69d2a4889fc` |
| GZDoom (`ZDoom/gzdoom`) | `src/posix/freedesktop/screenshot_doom.png` | `screenshot_heretic.png` y `screenshot_hexen.png` | Repositorio **ZDoom/gzdoom**, commit `c26ce2e6ca2a0c770f140cb25dde0d30073ca8f7` |

**Nota de exactitud:** las imágenes de GZDoom son capturas oficiales del **motor ejecutando Doom, Heretic y Hexen**. No son tres juegos incluidos en OKUBI, ni indican que el usuario tenga sus datos. Las imágenes de VCMI son capturas publicadas por los responsables de VCMI; algunas incluyen características/mods de versiones anteriores. Ninguna es una promesa de rendimiento o compatibilidad.

Todas las URL están aprobadas individualmente en `DesktopOfficialPortArtwork`, usan HTTPS hacia `raw.githubusercontent.com`, están fijadas a commits SHA de 40 caracteres y no tienen consulta, redirección ni dominio alternativo. Los archivos con espacios usan `%20` en URI. Sus tamaños publicados por GitHub están por debajo del máximo de descarga existente (2 MB). El programa sigue rechazando imágenes inválidas, sobredimensionadas o formatos incorrectos.

### Fuentes que todavía NO se activan
Para OpenMW, ScummVM, DOSBox Staging, Julius, OpenTyrian, OpenXcom, Daggerfall Unity y otros aún no encontramos —en las fuentes revisadas de esta ronda— imágenes que representen inequívocamente **ese port**, en vez de logos, recursos gráficos, screenshots de juegos ajenos o documentación del motor.

**Política:** no usar esas imágenes dudosas. Mantener fallback CINEMA neutro y opción **IMAGEN** para personalizaciones. Eso significa **17 ports todavía sin fuente oficial configurada**.

### Conservación de estado
- No cambia ruta ni prioridad de `%APPDATA%/OKUBI/port-artwork-v1/manual/<sha256>.png`.
- No borra imágenes antiguas ni la caché `official-screenshots-v2` y `official-gallery-v1`.
- Nuevas identidades utilizan su propio hash de repositorio. Cada extra emplea además un hash combinado de repositorio + URL.
- Fotos adicionales se descargan solo al navegar hasta ellas, en segundo plano; cuando están cacheadas funcionan sin Internet.
- No se toca código de Android, juego VIDAS, ROMs, archivos de partidas, emuladores, favoritos, aplicaciones instaladas ni ajustes.

## Pruebas automáticas
- `DesktopVerifiedArtworkExpansionTest`: cuenta exacta de 32 ports, 15 fuentes, 33 imágenes, URL/identidad correcta, SHA fijo, imágenes diferenciadas, rechazo de imágenes sociales GitHub, archivos personalizados intactos, prioridad manual y caché separada (sin Internet ni ROMs).
- `DesktopPortGalleryTest`: mantiene ocho galerías anteriores y añade dos nuevas para validar diez galerías con 28 imágenes.
- `DesktopPortArtworkServiceTest`: añade ambas identidades y valida 15 fuentes.
- Los pipelines Windows+Ubuntu y Windows x64 vuelven a compilar y ejecutar todas las regresiones.

## Prueba en PC
1. Abrir el ZIP de prueba nuevo en carpeta independiente, no reemplazar instalación original.
2. En PORTS PC comprobar VCMI (Siege, ciudad/unidad, otro asedio) y GZDoom (Doom, Heretic, Hexen), con sus contadores `1 / 3`.
3. Verificar que los nombres, el visor y las flechas funcionan en catálogo y CINEMA UNIFIED.
4. Configurar una imagen manual de VCMI, cerrar/reabrir y verificar que sigue teniendo prioridad y que GZDoom no cambia.
5. Apagar Internet tras visitar las capturas; repetir la navegación usando caché.
6. Verificar los ports sin captura y que ningún título muestra imágenes de un proyecto distinto.

No hacer merge ni publicación estable antes de QA visual real, licencias y pruebas de regresión.
