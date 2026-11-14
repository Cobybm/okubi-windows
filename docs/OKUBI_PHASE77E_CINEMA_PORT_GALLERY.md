# OKUBI 遊 — Fase 77E · Galería CINEMA para ports PC

Implementación Windows únicamente, apilada sobre Fase 77D sin fusionar otras ramas.

## Qué cambia

- Las fichas anchas de **PORTS PC / GITHUB** muestran flechas **◀** / **▶** y contador `1 / 3` (cuando existe más de una imagen oficial).
- La primera imagen sigue siendo la portada de 77D; si el usuario seleccionó su propia imagen manual, **esa imagen se ve primero** y no se pierde al navegar por la galería. El botón **IMAGEN** conserva su formato y ubicación.
- Capturas adicionales únicamente de ocho ports concretos, asociadas por **identidad exacta de repositorio**; no se adivinan coincidencias por el nombre del juego.
- La sección de biblioteca unificada CINEMA conserva su tarjeta pequeña original, sin flechas que estorben; las flechas aparecen exclusivamente en las fichas amplias.
- Cada captura se descarga **solo cuando el usuario pulsa la flecha**, sin precarga masiva. Conexión HTTPS y lista cerrada de URLs con commits Git completos, sin redirecciones; validación original de bytes, dimensiones y formato.
- Cache adicional independiente bajo `port-artwork-v1/official-gallery-v1/<hash>.png`, donde hash SHA-256 depende de la identidad del port y la URL exacta. No reutiliza cache de otros juegos.
- Cachés anteriores `port-artwork-v1/cache`, `official-screenshots-v1`, `official-screenshots-v2` y personalizaciones `manual/` permanecen intactas. Las capturas ya cargadas se pueden navegar desconectado.
- Si una imagen extra no se puede descargar, se presenta el fondo CINEMA, sin reemplazarla por una imagen del repo GitHub o de otro juego.
- Cada solicitud tiene un número de versión, por lo que al pulsar varias veces rápido la imagen de una respuesta anterior no sobreescribe la última seleccionada.
- La selección o eliminación de imagen personal devuelve la galería a la primera portada. Nunca altera el archivo fuente original de la foto personal.

## Catálogo cerrado y procedencia

Las **13 imágenes principales** se heredan de la Fase 77D sin cambios. Se añaden **14 imágenes secundarias**, todas como recursos de los propios repositorios/sitios oficiales, con hash de commit fijado y sin agregar archivos binarios al ZIP.

| Juego | Fotografías adicionales | Origen de proyecto |
|---|---|---|
| Arx Libertatis | spellcasting-2x.jpg | arx/arx-libertatis.org |
| OpenTTD | 1.9-coldice_2.png y 1.9-coldice_3.png | OpenTTD/website |
| Warzone 2100 | artillery-far-away.jpg y building.jpg | Warzone2100/warzone2100 |
| OpenRA | ingame-cnc.png e ingame-d2k.png | OpenRA/OpenRAWebsiteV3 |
| OpenLoco | gallery-screenshot-1.png y gallery-screenshot-2.png | OpenLoco/OpenLoco.io |
| CorsixTH | 2-medium.jpg | CorsixTH/corsixth.github.io |
| Endless Sky | trading.jpg y shipyard.jpg | endless-sky/endless-sky.github.io |
| SuperTuxKart | SuperTuxKart_1.5_2.jpg y SuperTuxKart_1.5_3.jpg | supertuxkart/stk-website |

No se afirma que las fotos de la documentación sean del último motor o que el juego esté instalado. La lista pretende ser **auténtica del proyecto**, no una comprobación de compatibilidad de hardware.

## Regresión y validaciones

Nueva prueba sintética `DesktopPortGalleryTest`, añadida a los workflows Windows / Ubuntu y al empaquetado Windows:

1. 13 identidades originales, 27 URLs en total y 8 galerías con dos/tres imágenes.
2. URLs inmóviles (`40` caracteres hex de commit) y solo dominios aprobados.
3. Rechazo de imagen de otro port, índices inválidos y previews sociales de GitHub.
4. Caché offline separada para las imágenes 1 y 2, visualización de cada color sintético.
5. Navegación rápida y protección contra callbacks asíncronos antiguos.
6. Imagen del usuario tiene prioridad, permanece sin modificar y su eliminación no toca el resto de las fotografías.

### Comprobación manual en Windows

1. Extraer un ZIP de prueba **en una carpeta nueva**, sin desinstalar ni sobrescribir OKUBI anterior.
2. Abrir PORTS PC, ir a OpenLoco, Endless Sky, OpenRA y SuperTuxKart; navegar con las flechas; comprobar barra inferior y que acciones DESCARGAS / VINCULAR .EXE / IMAGEN siguen accesibles.
3. Elegir una imagen local, navegar a la siguiente y volver a `1 / 3`: debe mostrarse la selección original en el primer panel.
4. Desconectar Internet después de cargar las 3 imágenes y confirmar navegación offline.
5. Visitar un port sin galería verificada: debe conservar el fondo CINEMA neutro, sin imágenes ajenas.
6. Comprobar con el mando/teclado el foco en botones, contraste y funcionamiento general.

## Alcance fuera de esta fase

Sin cambios en Android, datos de partidas, archivos ROM/BIOS, emuladores y configuraciones personales. Sin lanzamiento automático de ejecutables, minería de fotos, descarga de ROMs o almacenamiento de juegos ajenos. No integrar en `main` ni publicar estable antes de CI Windows completo, comprobación visual real, y gates de firma/licencias/Cross Save.
