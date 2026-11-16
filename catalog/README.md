# OKUBI 遊 — Biblioteca gratuita autorizada

## Lo que se añadió
`catalog/free-games.v1.json` contiene 5 juegos concretos, sus plataformas, autores, sitios oficiales, permisos conocidos y opciones de descarga, además de fuentes para ampliar el catálogo.

**Esto es el catálogo de datos inicial; no es todavía la pantalla de tienda conectada ni un EXE compilado.**

## Integración prevista en Windows y Android
1. Leer el catálogo local y cargar fichas sin conexión.
2. Actualizar desde un proveedor Homebrew Hub cuando esté disponible; documentación pública: https://github.com/gbdev/homebrewhub/blob/main/API.md
3. Usar `GET https://hh3.gbdev.io/api/search?platform=GBC` paginado y `GET /entry/{slug}.json` para título, licencia, consola y lista de archivos. No confiar únicamente en la extensión.
4. Descargar directamente solo cuando los términos y licencias permitan ese modo y el archivo sea de la fuente original. En itch.io mostrar la página oficial y permitir importar el archivo descargado mediante el flujo autorizado.
5. Validar HTTPS, tamaño, formato, nombre de archivo, redirecciones y hash SHA-256 de contenido al descargar. Rechazar archivos ejecutables o rutas de escritura fuera de la biblioteca. Ofrecer cancelación y progreso.
6. Detectar consola y seleccionar el núcleo disponible. Si no hay núcleo compatible o es necesario BIOS/firmware del usuario, informar sin descargar BIOS comerciales.
7. Primer inicio por sistema: mostrar mapa del teclado y mando, permitir remapear y recordar por consola/juego.
8. Guardar por separado juegos, partidas y configuraciones. Nunca sobrescribir datos existentes durante instalaciones o actualizaciones.
9. No alojar ni recomendar ROMs comerciales sin permisos de distribución verificados. No suponer que todo juego gratuito se puede redistribuir.

## Pruebas de aceptación
- Arranca con red desconectada y muestra el catálogo guardado.
- Filtro por consola y búsqueda por título.
- Juego de GB de código abierto: descargas autorizadas, validación, indexación, apertura y guardado.
- Juego de itch.io con flujo web oficial: descarga voluntaria e importación posterior sin eludir su página.
- Error 404, servidor lento, archivo inválido, 0 bytes, URL no HTTPS y cancelación.
- Compatibilidad de teclado y mandos y accesibilidad de la guía inicial.
- Preservación de partidas y configuración antes y después de actualización.

## URLs verificadas
- https://github.com/gbdev/homebrewhub/blob/main/API.md
- https://tangramgames.itch.io/tobu-tobu-girl-deluxe
- https://morphcatgames.itch.io/bobl
- https://benjelter.itch.io/opossum-country
- https://github.com/wyattferguson/2048-gb
