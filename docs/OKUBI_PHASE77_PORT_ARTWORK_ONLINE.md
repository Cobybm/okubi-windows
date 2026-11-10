# OKUBI 遊 — Fase 77: ARTWORK ONLINE para PORTS PC y CINEMA

Estado: beta Windows. Rama independiente, sin fusionar main ni tocar Android.

## Implementado

- DesktopPortArtworkService: carga de imágenes asíncrona, caché en disco, priorización manual > caché > GitHub > arte por defecto y límites de formato/tamaño.
- DesktopPortArtwork: vista con imagen de proyecto y fallback original CINEMA. Mantiene las proporciones de logos/texto.
- DesktopPortsDialog: imagen por proyecto en 32 ports curados y resultados del explorador público de GitHub. Botón IMAGEN con tres opciones: Elegir imagen local, Actualizar desde GitHub y Quitar imagen personalizada.
- DesktopUnifiedLibraryDialog: los ports Windows vinculados utilizan la misma caché. Los juegos retro mantienen su servicio Libretro de carátulas, OnlineCoverService, sin cambiar las ROMs.

## Procedencia y límites

- Imagen automática: GitHub Social Preview, desde https://opengraph.githubassets.com/1/owner/repo. Puede ser un logo o tarjeta del repositorio y no necesariamente una portada o captura del juego.
- Solo HTTPS, host de GitHub fijado, sin redirecciones. No se ejecutan instaladores ni scripts.
- Imagen de hasta 2 MB, como máximo 4096 píxeles de ancho/alto y 9 millones de píxeles, con decodificación validada y miniatura redimensionada.
- Dos hilos de fondo y cola acotada; el hilo de Swing no realiza accesos de red. Si falla un origen, aparece arte predeterminado. Se evitan reintentos de fallos durante seis horas en la sesión.
- Las imágenes locales se copian como PNG independiente; los originales quedan intactos. Se descartan enlaces simbólicos y archivos corruptos.
- El caché queda en el perfil OKUBI local (APPDATA/OKUBI/port-artwork-v1 o la cuenta QA aislada), fuera del repositorio. No se envían rutas personales, cuentas, ROMs ni partidas a GitHub. El servicio de GitHub sí recibe la solicitud de la imagen pública del repositorio.
- No se incluyen ni redistribuyen imágenes de terceros dentro del ZIP. Las miniaturas solo se descargan en el dispositivo y pueden no estar disponibles.

## Prueba física de Fase 77

1. Descarga el ZIP QA nuevo de esta rama y extráelo aparte, sin sustituir la copia anterior. Verifica el paquete y abre RUN-QA57-ISOLATED.cmd.
2. En PORTS PC / GITHUB, mira las imágenes junto a Arx Libertatis, Augustus, CorsixTH y OpenTTD. Una vista previa puede ser solamente el nombre/logo oficial del repositorio.
3. Usa IMAGEN > Elegir imagen local (PNG/JPG/GIF de menos de 2 MB). Confirma que la ficha muestra tu imagen y que se conserva tras cerrar/reabrir la aplicación.
4. Usa IMAGEN > Quitar imagen personalizada; volverá a la vista previa GitHub o al arte de respaldo.
5. Desconecta internet y abre el catálogo: las imágenes cacheadas deben continuar visibles.
6. En CINEMA · MIS JUEGOS (Ctrl+U), si tienes un port vinculado, verifica que utiliza la misma imagen.
7. Confirma que juegos retro y partidas permanecen intactos y que la navegación del catálogo sigue siendo fluida.

## Pendientes generales

- QA visual real de Fase 77 en Windows; las pruebas automáticas no validan todos los monitores.
- Integrar más fuentes de carátulas autorizadas por título para obtener capturas reales, sin confundir motor y videojuego.
- Verificar Cross Save físico, código fuente/licencias de DLL Windows, firma Android y revisar PR apilados antes de merge o lanzamiento estable.