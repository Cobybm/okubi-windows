# OKUBI 遊 — Fase 77D · Capturas fieles al juego y sin tarjetas equivocadas

## Alcance

Ampliación **solo Windows** de imágenes para el catálogo PORTS PC. Rama creada a partir de la Fase 77C, sin fusionar ninguna rama ni modificar Android, ROMs, saves, ejecutables, ajustes de emuladores o archivos existentes del usuario.

### Fuente revisada por juego

| Port del catálogo | Repositorio de imagen perteneciente al propio proyecto | Recurso |
|---|---|---|
| DevilutionX | diasurgical/DevilutionX | Packaging/vita/sce_sys/pic0.png |
| dhewm3 | dhewm/dhewm3 | docs/img/d3gui-example-16_9.png |
| Augustus | Keriew/augustus | doc/main-image.png |
| Arx Libertatis | arx/arx-libertatis.org | htdocs/images/castle-of-arx-2x.jpg |
| OpenTTD | OpenTTD/website | _screenshots/1.9-coldice_1.png |
| Warzone 2100 | Warzone2100/warzone2100 | doc/images/awaymission.jpg |
| Luanti | luanti-org/luanti | games/devtest/screenshot.png (devtest; no implica juego instalado) |
| OpenRA | OpenRA/OpenRAWebsiteV3 | images/appdata/ingame-ra.png (**sustituye arte de empaquetado** de 77C) |
| OpenRCT2 | OpenRCT2/openrct2.github.io | assets/img/screenshot.png |
| OpenLoco | OpenLoco/OpenLoco.io | assets/img/gallery-screenshot-0.png |
| CorsixTH | CorsixTH/corsixth.github.io | assets/media/screenshots/1-medium.jpg |
| Endless Sky | endless-sky/endless-sky.github.io | images/screenshots/battle.jpg |
| SuperTuxKart | supertuxkart/stk-website | assets/gallery/SuperTuxKart_1.5_1.jpg |

Los 13 enlaces de descarga usan `https://raw.githubusercontent.com/` con Git SHAs completos e inmutables en la lista `DesktopOfficialPortArtwork`. El catálogo solo obtiene el recurso del juego **por identidad exacta owner/repository**. Al no haber un recurso revisado, **no se prueba otra imagen aleatoria ni la preview social de GitHub**: se utiliza el fallback CINEMA oscuro, salvo la imagen elegida por el propietario.

Este cambio corrige en particular el ejemplo de OpenRA, que anteriormente usaba fondo de empaquetado de macOS en lugar de imagen interna del juego. Algunas imágenes de las ocho fuentes anteriores pueden representar una demo o UI del motor, no necesariamente una captura del título original; nunca se anuncian como una prueba de compatibilidad.

### Persistencia y protección

- Selección manual PNG/JPG/GIF -> `port-artwork-v1/manual/<sha256>.png` con prioridad máxima; **sin cambios en su ruta**.
- Cache automática actual -> `port-artwork-v1/official-screenshots-v2/<sha256>.png`; evita mostrar antiguas miniaturas GitHub por accidente. No borra `cache/` ni `official-screenshots-v1/`.
- Recursos exclusivamente HTTPS, lista exacta, sin seguir redirecciones, hasta 2 MB de entrada, dimensiones restringidas, descargas en segundo plano y caché offline.
- Si un archivo oficial no carga, se muestra el fallback neutro; no se representa material no validado como si fuera gameplay.
- `IMAGEN` permite cambiar, restaurar y quitar overrides; nunca altera las fuentes originales del usuario.
- Descubrimiento GitHub sin fuente oficial sigue funcionando; el texto y los vínculos permanecen y su imagen se presenta neutra hasta selección manual.

### QA automático

`DesktopPortArtworkServiceTest` comprueba las 13 identidades, los SHAs/URLs inmutables, ausencia de preview genérica, caché nueva y persistencia manual. Se añade la rama al workflow de Windows/Ubuntu para ejecutar pruebas existentes. No se descargan imágenes reales durante la prueba; las imágenes de prueba son sintéticas.

### QA manual en Windows

1. Instalar en **carpeta nueva**, sin reemplazar la versión existente.
2. Abrir PORTS PC y revisar OpenRCT2, OpenLoco, CorsixTH, Endless Sky, SuperTuxKart y OpenRA.
3. Confirmar que las tarjetas no muestran capturas ajenas, páginas blancas de GitHub o banners recortados de manera ilegible.
4. Elegir una imagen local para un port; cerrar/abrir, refrescar y confirmar prioridad manual. Luego quitarla, verificando que el archivo original permanece intacto.
5. Desconectar Internet y verificar la caché de capturas previamente cargadas. Para un proyecto no listado, confirmar fondo CINEMA sin falsa miniatura.
6. Revisar logs de Actions y el ZIP Windows; no declarar estable sin prueba física.

**Aún pendiente:** validación visual en PC real, cierre de revisión de licencias, firma Android y Cross Save. No hay merge automático ni publicación estable.
